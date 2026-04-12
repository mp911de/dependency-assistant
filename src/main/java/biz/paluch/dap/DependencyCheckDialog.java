/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.TableModelEvent;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyUpdateOption;
import biz.paluch.dap.artifact.DependencyUpdates;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.BuildFileUpdateAction;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.LayeredIcon;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.table.TableView;
import com.intellij.util.IconUtil;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import icons.MavenIcons;
import org.jspecify.annotations.Nullable;

/**
 * Dialog showing Maven dependency versions and update suggestions.
 */
public class DependencyCheckDialog extends DialogWrapper {

	/** Model index of {@link Upgrades} in the dependency table. */
	private static final int AVAILABLE_UPDATES_COLUMN_INDEX = 2;

	private final Project project;

	private final VirtualFile buildFile;

	private final DependencyUpdateModel model;

	private final BuildFileUpdateAction updateAction;

	public DependencyCheckDialog(Project project, VirtualFile buildFile, DependencyUpdates updates,
			BuildFileUpdateAction updateAction) {
		super(project, false, IdeModalityType.MODELESS);
		this.project = project;
		this.buildFile = buildFile;
		this.model = new DependencyUpdateModel(updates);
		this.updateAction = updateAction;
		setTitle(MessageBundle.message("dialog.title", project.getName()));
		init();
	}

	@Override
	protected JComponent createCenterPanel() {

		ListTableModel<DependencyUpdateOption> model = new ListTableModel<>(new DependencyCoordinateColumn(),
				new CurrentVersionColumn(), new Upgrades(), new UpdateToColumn(), new DoUpdateColumn());
		model.setItems(this.model.getUpdateOptions());

		DependencyUpdateTable table = new DependencyUpdateTable(model);
		table.setAutoCreateRowSorter(true);
		table.setShowGrid(true);
		table.setRowHeight(table.getRowHeight() + 4);
		table.setIntercellSpacing(new Dimension(JBUI.scale(2), JBUI.scale(2)));
		table.setPreferredScrollableViewportSize(new Dimension(JBUI.scale(800), JBUI.scale(400)));
		table.getTableHeader().setReorderingAllowed(false);

		TableColumnModel columns = table.getColumnModel();

		columns.getColumn(0).setPreferredWidth(JBUI.scale(250));
		columns.getColumn(1).setPreferredWidth(JBUI.scale(150));
		columns.getColumn(2).setPreferredWidth(JBUI.scale(100));
		columns.getColumn(3).setPreferredWidth(JBUI.scale(250));
		columns.getColumn(4).setPreferredWidth(JBUI.scale(50));

		JCheckBox filterVersionsCheckBox = new JCheckBox(MessageBundle.message("dialog.filter.version.suggestions"),
				this.model.isFilterVersionSuggestions());
		filterVersionsCheckBox.setToolTipText(MessageBundle.message("dialog.filter.version.tooltip"));

		JCheckBox editorCheckBox = new JCheckBox();
		editorCheckBox.setHorizontalAlignment(SwingConstants.CENTER);

		ActionGroup toolbarGroup = getToolbarGroup(table);
		ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar("MavenDependencyVersions", toolbarGroup,
				true);
		toolbar.setTargetComponent(table);

		filterVersionsCheckBox.addItemListener(e -> {
			this.model.setFilterVersionSuggestions(filterVersionsCheckBox.isSelected());
			if (table.getCellEditor() != null) {
				table.getCellEditor().cancelCellEditing();
			}
			model.setItems(this.model.getUpdateOptions());
		});

		ComboBox<DependencyUpdateModel.UpgradeStrategies> strategyComboBox = new ComboBox<>(
				DependencyUpdateModel.UpgradeStrategies.values());
		strategyComboBox.setSelectedItem(this.model.getUpgradeStrategy());
		strategyComboBox.setRenderer((list, value, index, isSelected, cellHasFocus) -> {
			JLabel label = new JLabel(value != null ? MessageBundle.message(value.getMessageKey()) : "");
			if (value != null) {
				label.setIcon(value.getIcon());
				label.setIconTextGap(JBUI.scale(4));
			} else {
				label.setIcon(null);
			}
			label.setOpaque(isSelected);
			if (isSelected) {
				label.setBackground(list.getSelectionBackground());
				label.setForeground(list.getSelectionForeground());
			}
			return label;
		});
		strategyComboBox.addItemListener(e -> {
			if (e.getStateChange() != ItemEvent.SELECTED) {
				return;
			}
			DependencyUpdateModel.UpgradeStrategies strategy = (DependencyUpdateModel.UpgradeStrategies) e.getItem();
			this.model.setUpgradeStrategy(strategy);
			if (strategy == DependencyUpdateModel.UpgradeStrategies.MANUAL) {
				return;
			}
			applyUpgradeStrategy(table, strategy);
		});

		JPanel strategyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0));
		JLabel strategyLabel = new JLabel(MessageBundle.message("dialog.upgradeStrategy.label"));
		strategyComboBox.setToolTipText(MessageBundle.message("dialog.upgradeStrategy.tooltip"));
		strategyPanel.add(strategyLabel);
		strategyPanel.add(strategyComboBox);

		JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
		rightPanel.add(strategyPanel);
		rightPanel.add(toolbar.getComponent());

		JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(12), 0));
		leftPanel.add(filterVersionsCheckBox);

		JPanel toolbarPanel = new JPanel(new BorderLayout());
		toolbarPanel.add(leftPanel, BorderLayout.WEST);
		toolbarPanel.add(rightPanel, BorderLayout.EAST);

		JBScrollPane scrollPane = new JBScrollPane(table);
		scrollPane.setPreferredSize(new Dimension(JBUI.scale(775), JBUI.scale(420)));

		JPanel panel = new JPanel(new BorderLayout());
		panel.add(toolbarPanel, BorderLayout.NORTH);
		panel.add(scrollPane, BorderLayout.CENTER);
		if (!this.model.getErrors().isEmpty()) {
			String errorText = MessageBundle.message("dialog.warnings.prefix") + "\n" + this.model.getErrors().stream()
					.map(s -> MessageBundle.message("dialog.warnings.bullet") + s).collect(Collectors.joining("\n"));
			JTextArea errorArea = new JTextArea(errorText);
			errorArea.setEditable(false);
			errorArea.setRows(3);
			errorArea.setLineWrap(true);
			panel.add(new JScrollPane(errorArea), BorderLayout.SOUTH);
		}
		return panel;
	}

	private DefaultActionGroup getToolbarGroup(JTable table) {

		AnAction selectAllAction = new AnAction(MessageBundle.message("dialog.action.selectAll"),
				MessageBundle.message("dialog.action.selectAll.description"), AllIcons.Actions.Selectall) {

			@Override
			public void actionPerformed(AnActionEvent e) {
				model.setUpdateAll(true);
				table.tableChanged(new TableModelEvent(table.getModel()));
			}

		};

		AnAction deselectAllAction = new AnAction(MessageBundle.message("dialog.action.unselectAll"),
				MessageBundle.message("dialog.action.unselectAll.description"), AllIcons.Actions.Unselectall) {

			@Override
			public void actionPerformed(AnActionEvent e) {
				model.setUpdateAll(false);
				table.tableChanged(new TableModelEvent(table.getModel()));
			}

		};

		DefaultActionGroup toolbarGroup = new DefaultActionGroup();
		toolbarGroup.add(selectAllAction);
		toolbarGroup.add(deselectAllAction);
		return toolbarGroup;
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return null;
	}

	@Override
	protected void doOKAction() {
		updateAction.updateBuildFile(project, buildFile, model.toDependencyUpdates());
		DaemonCodeAnalyzer.getInstance(project).restart(buildFile);
		super.doOKAction();
	}

	@Override
	public void doCancelAction() {
		DaemonCodeAnalyzer.getInstance(project).restart(buildFile);
		super.doCancelAction();
	}

	private void applyUpgradeStrategy(JTable table, DependencyUpdateModel.UpgradeStrategies strategy) {

		boolean filtered = model.isFilterVersionSuggestions();

		for (int row = 0; row < table.getModel().getRowCount(); row++) {
			DependencyUpdateOption suggestion = ModelUtil.getOption(table, row);
			ArtifactVersion target = pickTargetVersion(suggestion,
					filtered ? suggestion.filtered() : suggestion.versionOptions(), strategy);

			if (target != null) {
				suggestion.setUpdateTo(target);
			}
		}

		table.tableChanged(new TableModelEvent(table.getModel()));
		table.repaint();
	}

	private @Nullable ArtifactVersion pickTargetVersion(DependencyUpdateOption option, List<Release> options,
			DependencyUpdateModel.UpgradeStrategies strategy) {
		if (strategy == DependencyUpdateModel.UpgradeStrategies.MANUAL) {
			return null;
		}
		if (options.isEmpty()) {
			return null;
		}

		Release upgradeTo = option.getTargets().get(strategy.getStrategy());
		if (upgradeTo != null) {
			return upgradeTo.version();
		}

		return null;
	}

	private void applyUpgradeTargetToViewRow(JTable table, int viewRow, UpgradeStrategy strategy) {
		DependencyUpdateOption suggestion = ModelUtil.getOption(table, viewRow);
		if (suggestion == null) {
			return;
		}
		Release vo = suggestion.getTargets().get(strategy);
		if (vo == null) {
			return;
		}
		suggestion.setUpdateTo(vo.version());
		if (table.isEditing()) {
			table.getCellEditor().stopCellEditing();
		}
		table.tableChanged(new TableModelEvent(table.getModel()));
		table.repaint();
	}

	/**
	 * Forwards tooltips and hand cursor for upgrade-target icon buttons (renderer
	 * stamps are not in the real component tree, so the table must delegate).
	 */
	private static class DependencyUpdateTable extends TableView<DependencyUpdateOption> {

		DependencyUpdateTable(ListTableModel<DependencyUpdateOption> model) {
			super(model);
			setToolTipText("");
			addMouseMotionListener(new MouseMotionAdapter() {

				@Override
				public void mouseMoved(MouseEvent e) {
					updateUpgradeTargetsHoverCursor(e);
				}

			});
			addMouseListener(new MouseAdapter() {

				@Override
				public void mouseExited(MouseEvent e) {
					setCursor(Cursor.getDefaultCursor());
				}

			});
		}

		@Override
		public @Nullable String getToolTipText(MouseEvent event) {
			if (event == null) {
				return null;
			}
			String tip = tooltipForUpgradeTargetsColumn(event);
			return tip != null ? tip : super.getToolTipText(event);
		}

		private @Nullable String tooltipForUpgradeTargetsColumn(MouseEvent event) {

			Point p = event.getPoint();
			int row = rowAtPoint(p);
			int col = columnAtPoint(p);
			if (row < 0 || col < 0) {
				return null;
			}
			int upgradesCol = convertColumnIndexToView(AVAILABLE_UPDATES_COLUMN_INDEX);
			if (upgradesCol < 0 || col != upgradesCol) {
				return null;
			}
			Rectangle cell = getCellRect(row, col, false);
			if (!cell.contains(p)) {
				return null;
			}
			TableCellRenderer renderer = getCellRenderer(row, col);
			Component stamp = prepareRenderer(renderer, row, col);
			layoutRendererStamp(stamp, cell.width, cell.height);
			Point rel = new Point(p.x - cell.x, p.y - cell.y);
			Component deepest = SwingUtilities.getDeepestComponentAt(stamp, rel.x, rel.y);
			if (deepest instanceof JButton btn) {
				MouseEvent relEv = SwingUtilities.convertMouseEvent(this, event, deepest);
				return btn.getToolTipText(relEv);
			}
			return null;
		}

		private static void layoutRendererStamp(Component stamp, int w, int h) {

			stamp.setBounds(0, 0, Math.max(1, w), Math.max(1, h));
			if (stamp instanceof JComponent jc) {
				jc.validate();
			}
		}

		private void updateUpgradeTargetsHoverCursor(MouseEvent e) {

			Point p = e.getPoint();
			int row = rowAtPoint(p);
			int col = columnAtPoint(p);
			Cursor c = Cursor.getDefaultCursor();
			int upgradesCol = convertColumnIndexToView(AVAILABLE_UPDATES_COLUMN_INDEX);
			if (row >= 0 && col >= 0 && upgradesCol >= 0 && col == upgradesCol) {
				Rectangle cell = getCellRect(row, col, false);
				if (cell.contains(p)) {
					Component stamp = prepareRenderer(getCellRenderer(row, col), row, col);
					layoutRendererStamp(stamp, cell.width, cell.height);
					Point rel = new Point(p.x - cell.x, p.y - cell.y);
					Component deepest = SwingUtilities.getDeepestComponentAt(stamp, rel.x, rel.y);
					if (deepest instanceof JButton) {
						c = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR);
					}
				}
			}
			setCursor(c);
		}

	}

	/**
	 * One icon-button strip per {@link DependencyUpdateOption}, shared by renderer
	 * and editor (see {@link UpdateToColumn}). {@link ActionToolbar} is unreliable
	 * inside JTable paint stamps (layout/async refresh), so this uses plain
	 * {@link JButton}s with the same icons.
	 */
	private class UpgradeTargetsToolbarEditor extends DefaultCellEditor {

		private final JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

		private @Nullable JTable contextTable;

		private int contextViewRow;

		UpgradeTargetsToolbarEditor(DependencyUpdateOption option) {
			super(new JTextField());
			setClickCountToStart(1);
			buttonPanel.setOpaque(true);
			for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
				Release vo = option.getTargets().get(strategy);
				if (vo == null || strategy == UpgradeStrategy.LATEST) {
					continue;
				}
				Icon icon = VersionAge.fromTarget(strategy).getIcon();
				String shortLabel = MessageBundle.message("dialog.upgradeTarget." + strategy.name());
				String tooltip = MessageBundle.message("dialog.upgradeTarget.tooltip", shortLabel, vo.version());
				UpgradeStrategy strat = strategy;
				JButton b = createButton(icon, tooltip, shortLabel, vo);
				b.addActionListener(e -> {
					JTable t = contextTable;
					if (t != null) {
						applyUpgradeTargetToViewRow(t, contextViewRow, strat);
					}
				});
				buttonPanel.add(b);
			}
		}

		private static JButton createButton(Icon icon, String tooltip, String shortLabel, Release version) {
			JButton b = new JButton(icon);
			b.setToolTipText(tooltip);
			b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			b.getAccessibleContext().setAccessibleName(shortLabel + " " + version.version());
			b.setBorderPainted(false);
			b.setMargin(JBUI.insets(2));
			b.setContentAreaFilled(false);
			b.setOpaque(false);
			b.setFocusPainted(true);
			b.setRolloverEnabled(true);
			Dimension preferredSize = new Dimension(icon.getIconWidth(), icon.getIconHeight());
			b.setPreferredSize(preferredSize);
			b.setMinimumSize(preferredSize);
			b.setMaximumSize(preferredSize);
			return b;
		}

		JComponent prepareForCell(JTable table, int viewRow, boolean isSelected) {

			contextTable = table;
			contextViewRow = viewRow;
			Color bg = isSelected ? table.getSelectionBackground() : table.getBackground();
			buttonPanel.setBorder(JBUI.Borders.empty());
			buttonPanel.setBackground(bg);
			for (Component ch : buttonPanel.getComponents()) {
				ch.setBackground(bg);
			}
			return buttonPanel;
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, @Nullable Object value, boolean isSelected, int row,
				int column) {

			return prepareForCell(table, row, isSelected);
		}

		@Override
		public @Nullable Object getCellEditorValue() {
			return null;
		}

	}

	static class DependencyCoordinateColumn extends ColumnInfo<DependencyUpdateOption, ArtifactId> {

		DefaultTableCellRenderer tcr = new DefaultTableCellRenderer() {

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus,
					int row, int column) {

				Component tableCellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected,
						hasFocus,
						row, column);
				DependencyUpdateOption option = ModelUtil.getOption(table, row);

				String artifactId = option.getArtifactId().toString();
				String tooltip = artifactId;
				boolean hasPropertyVersion = option.hasPropertyVersion();
				if (hasPropertyVersion) {
					VersionSource.VersionProperty versionProperty = option.getPropertyVersion();
					tooltip = MessageBundle.message("dialog.tooltip.property", versionProperty);
					if (versionProperty instanceof VersionSource.Profile pps) {
						tooltip += MessageBundle.message("dialog.tooltip.profile", pps.getProfileId());
					}
				}

				boolean plugin = option.source() instanceof DeclarationSource.Plugin;

				if (plugin) {
					tooltip += MessageBundle.message("dialog.tooltip.plugin", artifactId);
				}

				if (option.source() instanceof DeclarationSource.Profile profile) {
					tooltip += MessageBundle.message("dialog.tooltip.profile", profile.getProfileId());
				}

				setIcon(DependencyCoordinateColumn.getIcon(option, table));

				setToolTipText(tooltip);
				return tableCellRendererComponent;
			}

			@Override
			protected void setValue(Object value) {
				if (value instanceof ArtifactId coordinate) {
					setText(coordinate.artifactId());
					return;
				}
				super.setValue(value);
			}

		};

		/**
		 * Plugin icon with a small property icon overlaid at the bottom-right (for
		 * plugin + ${property} version).
		 */
		static Icon getIcon(DependencyUpdateOption option, JTable table) {

			Icon base = option.source() instanceof DeclarationSource.Plugin ? MavenIcons.MavenPlugin
					: MavenIcons.MavenProject;

			int pad = 0;
			int bw = base.getIconWidth();
			int bh = base.getIconHeight();

			LayeredIcon layered = new LayeredIcon(2);
			layered.setIcon(base, 0);

			if (option.hasPropertyVersion()) {
				Icon propertySmall = IconUtil.scale(AllIcons.Nodes.Property, table, 0.5f);

				int ow = propertySmall.getIconWidth();
				int oh = propertySmall.getIconHeight();
				layered.setIcon(propertySmall, 1, Math.max(0, bw - ow - pad), Math.max(0, bh - oh - pad));
			}

			return layered;
		}

		DependencyCoordinateColumn() {
			super(MessageBundle.message("dialog.column.dependency"));
		}

		@Override
		public ArtifactId valueOf(DependencyUpdateOption item) {
			return item.getArtifactId();
		}

		@Override
		public TableCellRenderer getRenderer(DependencyUpdateOption option) {
			return tcr;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactId.class;
		}

	}

	static class CurrentVersionColumn extends ColumnInfo<DependencyUpdateOption, ArtifactVersion> {

		CurrentVersionColumn() {
			super(MessageBundle.message("dialog.column.current"));
		}

		@Override
		public @Nullable ArtifactVersion valueOf(DependencyUpdateOption item) {
			return item.currentVersion();
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactVersion.class;
		}

	}

	class Upgrades extends ColumnInfo<DependencyUpdateOption, Object> {

		private final Map<DependencyUpdateOption, UpgradeTargetsToolbarEditor> editors = new ConcurrentHashMap<>();

		private final Set<UpgradeTargetsToolbarEditor> listeners = ConcurrentHashMap.newKeySet();

		Upgrades() {
			super(MessageBundle.message("dialog.column.upgrades"));
		}

		private UpgradeTargetsToolbarEditor getToolbarEditor(DependencyUpdateOption option) {
			return editors.computeIfAbsent(option, UpgradeTargetsToolbarEditor::new);
		}

		@Override
		public @Nullable Object valueOf(DependencyUpdateOption item) {
			return null;
		}

		@Override
		public boolean isCellEditable(DependencyUpdateOption item) {
			return item.hasUpgradeTargets();
		}

		@Override
		public void setValue(DependencyUpdateOption item, Object value) {
			// Applied from picker {@link JButton} actions.
		}

		@Override
		public TableCellEditor getEditor(DependencyUpdateOption item) {
			return getToolbarEditor(item);
		}

		@Override
		public TableCellRenderer getRenderer(DependencyUpdateOption columnOption) {

			return (table, value, isSelected, hasFocus, row, column) -> {

				DependencyUpdateOption info = ModelUtil.getOption(table, row);
				if (info == null) {
					JLabel empty = new JLabel();
					empty.setOpaque(true);
					empty.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
					return empty;
				}
				UpgradeTargetsToolbarEditor editor = getToolbarEditor(info);
				if (listeners.add(editor)) {
					editor.getComponent().setFocusable(false);
				}
				JComponent c = editor.prepareForCell(table, row, isSelected);
				c.setEnabled(table.isEnabled());
				return c;
			};
		}

		@Override
		public Class<?> getColumnClass() {
			return Object.class;
		}

	}

	class UpdateToColumn extends ColumnInfo<DependencyUpdateOption, ArtifactVersion> {

		private final Map<DependencyUpdateOption, SuggestedVersionComboBoxEditor> editors = new ConcurrentHashMap<>();

		private final Set<SuggestedVersionComboBoxEditor> listeners = ConcurrentHashMap.newKeySet();

		UpdateToColumn() {
			super(MessageBundle.message("dialog.column.updateTo"));
		}

		@Override
		public @Nullable ArtifactVersion valueOf(DependencyUpdateOption item) {
			return item.getUpdateTo();
		}

		@Override
		public TableCellRenderer getRenderer(DependencyUpdateOption option) {

			return new DefaultTableCellRenderer() {

				@Override
				public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
						boolean hasFocus, int row, int column) {

					DependencyUpdateOption info = ModelUtil.getOption(table, row);

					SuggestedVersionComboBoxEditor editor = getEditor(info);
					if (listeners.add(editor)) {

						editor.getCombo().addActionListener(actionEvent -> {

							Release option = (Release) editor.getCombo().getSelectedItem();

							if (option != null && !option.version().equals(info.getUpdateTo())) {
								info.setUpdateTo(option.version());
								table.editingStopped(new ChangeEvent(option));
							}
						});
					}

					return editor.getTableCellEditorComponent(table, value, isSelected, row, column);
				}

			};

		}

		@Override
		public SuggestedVersionComboBoxEditor getEditor(DependencyUpdateOption option) {
			return editors.computeIfAbsent(option, it -> new SuggestedVersionComboBoxEditor(model, option));
		}

		@Override
		public void setValue(DependencyUpdateOption item, ArtifactVersion value) {
			if (value != null && !value.equals(item.getUpdateTo())) {
				item.setUpdateTo(value);
			}
		}

		@Override
		public boolean isCellEditable(DependencyUpdateOption item) {
			return true;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactVersion.class;
		}

	}

	static class DoUpdateColumn extends ColumnInfo<DependencyUpdateOption, Boolean> {

		private final ApplyUpdateCheckboxEditor editor = new ApplyUpdateCheckboxEditor();

		DoUpdateColumn() {
			super(MessageBundle.message("dialog.column.update"));
		}

		@Override
		public TableCellRenderer getRenderer(DependencyUpdateOption option) {

			return (t, value, isSelected, hasFocus, row, column) -> {
				JCheckBox cb = new JCheckBox();
				cb.setHorizontalAlignment(SwingConstants.CENTER);
				cb.setSelected(Boolean.TRUE.equals(value));
				cb.setBackground(isSelected ? t.getSelectionBackground() : t.getBackground());
				cb.setOpaque(true);
				return cb;
			};
		}

		@Override
		public TableCellEditor getEditor(DependencyUpdateOption option) {
			return editor;
		}

		@Override
		public Boolean valueOf(DependencyUpdateOption item) {
			return item.isApplyUpdate();
		}

		@Override
		public void setValue(DependencyUpdateOption item, Boolean value) {
			item.setApplyUpdate(value);
		}

		@Override
		public boolean isCellEditable(DependencyUpdateOption item) {
			return true;
		}

		@Override
		public Class<?> getColumnClass() {
			return Boolean.class;
		}

	}

	/**
	 * Single checkbox for the active editor cell only; selection is set from the
	 * row value when editing starts.
	 */
	private static class ApplyUpdateCheckboxEditor extends AbstractCellEditor implements TableCellEditor {

		private final JCheckBox checkBox = new JCheckBox();

		ApplyUpdateCheckboxEditor() {
			checkBox.setHorizontalAlignment(SwingConstants.CENTER);
			checkBox.addActionListener(e -> stopCellEditing());
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, @Nullable Object value, boolean isSelected, int row,
				int column) {

			checkBox.setSelected(Boolean.TRUE.equals(value));
			checkBox.setBackground(isSelected ? table.getSelectionBackground() : table.getBackground());
			return checkBox;
		}

		@Override
		public @Nullable Object getCellEditorValue() {
			return checkBox.isSelected();
		}

	}

}
