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

package biz.paluch.dap.assistant;

import java.awt.*;
import java.awt.event.ItemEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.Collection;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
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
import org.jspecify.annotations.Nullable;

/**
 * Dialog showing Maven dependency versions and update suggestions.
 *
 * @author Mark Paluch
 */
public class DependencyCheckDialog extends DialogWrapper {

	private static final int AVAILABLE_UPDATES_COLUMN_INDEX = 2;

	private final Project project;

	private final DependencyUpgradeReview review;

	private final Collection<VirtualFile> files;

	private final DependencyCheckComponents components;

	private volatile boolean applyingUpdates;

	public DependencyCheckDialog(Project project, DependencyCheckResult result, String title) {
		super(project, false, IdeModalityType.MODELESS);
		this.project = project;
		this.files = result.files();
		this.review = new DependencyUpgradeReview(result.candidates(), result.errors());
		this.components = new DependencyCheckComponents(this.review, getDisposable());
		setTitle(title);
		init();
	}

	/**
	 * Components panel for the dependency check dialog, containing the table and
	 * related controls.
	 */
	static class DependencyCheckComponents extends JPanel {

		private final DependencyUpgradeReview review;

		private final ListTableModel<UpdateCandidate> tableModel;

		private final DependencyUpdateTable table;

		private final ActionToolbar toolbar;

		private final ComboBox<DependencyUpgradeReview.UpgradeStrategies> strategyComboBox;

		DependencyCheckComponents(DependencyUpgradeReview review, Disposable parent) {
			super(new BorderLayout());
			this.review = review;

			this.tableModel = new ListTableModel<>(new DependencyCoordinateColumn(),
					new CurrentVersionColumn(review), new Upgrades(review), new UpdateToColumn(review),
					new DoUpdateColumn(review));
			this.table = new DependencyUpdateTable(tableModel);
			this.strategyComboBox = new ComboBox<>(
					DependencyUpgradeReview.UpgradeStrategies.values());

			ActionGroup toolbarGroup = getToolbarGroup();
			this.toolbar = ActionManager.getInstance().createActionToolbar("MavenDependencyVersions", toolbarGroup,
					true);

			initialize(parent);
		}

		private void initialize(Disposable parent) {

			tableModel.setItems(this.review.visibleCandidates());

			review.addListener(change -> {

				if (change.reload()) {
					stopEditing();
					tableModel.setItems(review.visibleCandidates());
					return;
				}

				UpdateCandidate candidate = change.candidate();
				if (candidate != null) {
					int modelRow = tableModel.indexOf(candidate);
					if (modelRow >= 0) {
						tableModel.fireTableRowsUpdated(modelRow, modelRow);
					}
					return;
				}

				int rowCount = tableModel.getRowCount();
				if (rowCount > 0) {
					tableModel.fireTableRowsUpdated(0, rowCount - 1);
				}
			}, parent);

			table.setAutoCreateRowSorter(true);
			table.setShowGrid(true);
			table.setRowHeight(table.getRowHeight() + 4);
			table.setIntercellSpacing(new Dimension(JBUI.scale(2), JBUI.scale(2)));
			table.getTableHeader().setReorderingAllowed(false);

			table.setPreferredScrollableViewportSize(new Dimension(JBUI.scale(800), JBUI.scale(420)));

			TableColumnModel columns = table.getColumnModel();

			columns.getColumn(0).setPreferredWidth(JBUI.scale(250));
			columns.getColumn(1).setPreferredWidth(JBUI.scale(150));
			columns.getColumn(2).setPreferredWidth(JBUI.scale(100));
			columns.getColumn(3).setPreferredWidth(JBUI.scale(250));
			columns.getColumn(4).setPreferredWidth(JBUI.scale(50));

			JCheckBox filterVersionsCheckBox = new JCheckBox(MessageBundle.message("dialog.filter.version.suggestions"),
					this.review.isHideUpToDate());
			filterVersionsCheckBox.setToolTipText(MessageBundle.message("dialog.filter.version.tooltip"));

			toolbar.setTargetComponent(table);

			filterVersionsCheckBox.addItemListener(e -> review.setHideUpToDate(filterVersionsCheckBox.isSelected()));

			strategyComboBox.setSelectedItem(this.review.getUpgradeStrategy());
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
				DependencyUpgradeReview.UpgradeStrategies strategy = (DependencyUpgradeReview.UpgradeStrategies) e
						.getItem();
				review.applyStrategyToAll(strategy);
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

			add(toolbarPanel, BorderLayout.NORTH);
			add(scrollPane, BorderLayout.CENTER);
			if (!this.review.getErrors().isEmpty()) {
				String errorText = MessageBundle.message("dialog.warnings.prefix") + "\n"
						+ this.review.getErrors().stream()
								.map(s -> MessageBundle.message("dialog.warnings.bullet") + s)
								.collect(Collectors.joining("\n"));
				JTextArea errorArea = new JTextArea(errorText);
				errorArea.setEditable(false);
				errorArea.setRows(3);
				errorArea.setLineWrap(true);
				add(new JScrollPane(errorArea), BorderLayout.SOUTH);
			}
		}

		private DefaultActionGroup getToolbarGroup() {

			AnAction selectAllAction = new AnAction(MessageBundle.message("dialog.action.selectAll"),
					MessageBundle.message("dialog.action.selectAll.description"), AllIcons.Actions.Selectall) {

				@Override
				public void actionPerformed(AnActionEvent e) {
					review.selectAll(true);
				}

			};

			AnAction deselectAllAction = new AnAction(MessageBundle.message("dialog.action.unselectAll"),
					MessageBundle.message("dialog.action.unselectAll.description"), AllIcons.Actions.Unselectall) {

				@Override
				public void actionPerformed(AnActionEvent e) {
					review.selectAll(false);
				}

			};

			DefaultActionGroup toolbarGroup = new DefaultActionGroup();
			toolbarGroup.add(selectAllAction);
			toolbarGroup.add(deselectAllAction);
			return toolbarGroup;
		}

		void stopEditing() {

			if (table.isEditing()) {
				TableCellEditor editor = table.getCellEditor();
				if (editor != null) {
					editor.stopCellEditing();
				}
			}
		}

		void disableAll() {
			setTreeEnabled(this.table, false);
			setTreeEnabled(this.strategyComboBox, false);
			setTreeEnabled(this.toolbar.getComponent(), false);
		}

		private void setTreeEnabled(Component component, boolean enabled) {

			component.setEnabled(enabled);
			if (component instanceof Container container) {
				for (Component child : container.getComponents()) {
					setTreeEnabled(child, enabled);
				}
			}
		}

	}

	@Override
	protected JComponent createCenterPanel() {
		return this.components;
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return null;
	}

	@Override
	protected void doOKAction() {

		if (applyingUpdates) {
			return;
		}

		this.components.stopEditing();

		List<DependencyUpdate> updates = getSelectedUpdates();
		if (updates.isEmpty()) {
			super.doOKAction();
			return;
		}

		setBusy(true);
		ProgressManager.getInstance().run(new ApplyUpdatesTask(project, updates));
	}

	@Override
	public void doCancelAction() {
		if (applyingUpdates) {
			ProgressManager instance = ProgressManager.getInstance();
			ProgressIndicator progressIndicator = instance.getProgressIndicator();
			if (progressIndicator != null) {
				progressIndicator.cancel();
			}
			return;
		}
		restartHighlighting();
		super.doCancelAction();
	}

	private List<DependencyUpdate> getSelectedUpdates() {
		return review.visibleCandidates().stream()
				.filter(review::isApplyUpdate)
				.map(this::toDependencyUpdate)
				.toList();
	}

	private DependencyUpdate toDependencyUpdate(UpdateCandidate candidate) {

		DependencyUpdateCandidate option = candidate.option();

		record FriendlyArtifactId(ArtifactId id,
		                          String friendlyName) implements ArtifactId {

			@Override
			public String groupId() {
				return id.groupId();
			}

			@Override
			public String artifactId() {
				return id.artifactId();
			}

			@Override
			public String toString() {
				return friendlyName;
			}

		}

		FriendlyArtifactId artifactId = new FriendlyArtifactId(option.getArtifactId(),
				candidate.getDependencyName());
		return DependencyUpdate.from(artifactId, option
				.getDependency(), review.getRequiredUpdateTo(candidate));
	}

	private void setBusy(boolean busy) {

		this.applyingUpdates = busy;
		setOKActionEnabled(!busy);
		this.components.disableAll();
	}

	private void applyUpdates(List<DependencyUpdate> updates, List<DependencyAssistant> assistants,
			ProgressIndicator indicator) {

		Set<AppliedDependencyUpdate> applied = new TreeSet<>();
		new BuildActionDelegate(project, (file, fileUpdates) -> {

			indicator.setText2(file.getName());
			for (DependencyAssistant dependencyAssistant : assistants) {
				if (!dependencyAssistant.supports(file)) {
					continue;
				}

				ProjectDependencyContext context = dependencyAssistant.createContext(file);
				if (context.isAvailable()) {
					context.applyUpdates(file, fileUpdates);

					for (DependencyUpdate fileUpdate : fileUpdates) {
						applied.add(new AppliedDependencyUpdate(fileUpdate.coordinate(), fileUpdate.from(), fileUpdate.version()));
					}
				}
			}
		}).updateBuildFiles(files, updates);

		Notifications.updatesApplied(project, applied);
	}

	class ApplyUpdatesTask extends Task.Backgroundable {

		private final List<DependencyUpdate> updates;

		private final List<DependencyAssistant> assistants;

		private ApplyUpdatesTask(Project project, List<DependencyUpdate> updates) {
			super(project, MessageBundle.message("intention.UpgradingDependencies.text"), true);
			this.updates = updates;
			this.assistants = DependencyAssistantDispatcher.findAll(project);
		}

		@Override
		public void run(ProgressIndicator indicator) {
			indicator.setIndeterminate(true);
			indicator.setText(MessageBundle.message("intention.UpgradingDependencies.text"));
			applyUpdates(updates, assistants, indicator);
		}

		@Override
		public void onSuccess() {

			restartHighlighting();
			close(OK_EXIT_CODE);
		}

		@Override
		public void onThrowable(Throwable error) {
			Notifications.error(project,
					MessageBundle.message("UpdateBuildFile.notification.error.title"),
					Notifications.errorMessage(error));
		}

		@Override
		public void onFinished() {
			if (isShowing()) {
				setBusy(false);
			} else {
				applyingUpdates = false;
			}
		}

	}

	private void restartHighlighting() {

		ReadAction.run(() -> {
			DaemonCodeAnalyzer analyzer = DaemonCodeAnalyzer.getInstance(project);
			BetterPsiManager psiManager = BetterPsiManager.getInstance(project);
			psiManager.stream(files).forEach(analyzer::restart);
		});
	}

	/**
	 * Brings the upgrade-target cell under the cursor into edit mode so its real
	 * toolbar buttons deliver native tooltips and hand cursor. A renderer paints a
	 * dead stamp; only the live editor is a real component tree, so hover-to-edit
	 * is what makes the buttons interactive.
	 */
	static class DependencyUpdateTable extends TableView<UpdateCandidate> {

		DependencyUpdateTable(ListTableModel<UpdateCandidate> model) {
			super(model);
			setToolTipText("");
			addMouseMotionListener(new MouseMotionAdapter() {

				@Override
				public void mouseMoved(MouseEvent e) {
					editUpgradeTargetsOnHover(e);
				}

			});
		}

		private void editUpgradeTargetsOnHover(MouseEvent e) {

			Point p = e.getPoint();
			int row = rowAtPoint(p);
			int col = columnAtPoint(p);
			if (row < 0 || col != convertColumnIndexToView(AVAILABLE_UPDATES_COLUMN_INDEX)) {
				return;
			}

			if (isEditing() && getEditingRow() == row && getEditingColumn() == col) {
				return;
			}

			if (isCellEditable(row, col)) {
				editCellAt(row, col);
			}
		}

	}

	/**
	 * One icon-button strip per {@link UpdateCandidate}.
	 */
	static class UpgradeTargetsToolbarEditor extends DefaultCellEditor {

		private final JPanel buttonPanel = new JPanel(new GridBagLayout());

		private final UpdateCandidate candidate;

		private final DependencyUpgradeReview review;

		private final Map<UpgradeStrategy, JButton> buttons = new EnumMap<>(UpgradeStrategy.class);

		UpgradeTargetsToolbarEditor(UpdateCandidate candidate, DependencyUpgradeReview review) {
			super(new JTextField());
			this.candidate = candidate;
			this.review = review;
			setClickCountToStart(1);
			getComponent().setFocusable(false);
			buttonPanel.setOpaque(true);

			DependencyUpdateCandidate option = candidate.option();
			GridBagConstraints constraints = new GridBagConstraints();
			constraints.gridy = 0;
			constraints.anchor = GridBagConstraints.CENTER;
			constraints.insets = JBUI.insetsRight(JBUI.scale(2));
			int gridx = 0;

			for (UpgradeStrategy strategy : UpgradeStrategy.values()) {

				Release release = option.getTargets().get(strategy);
				if (release == null || strategy == UpgradeStrategy.LATEST || strategy == UpgradeStrategy.RELEASE) {
					continue;
				}

				Icon icon = VersionAge.fromTarget(strategy).getIcon();
				String shortLabel = MessageBundle.message("dialog.upgradeTarget." + strategy.name());
				String tooltip = MessageBundle.message("dialog.upgradeTarget.tooltip", shortLabel, release.version());
				JButton b = createButton(icon, tooltip, shortLabel, release);
				b.addActionListener(e -> review.applyStrategyTarget(candidate, strategy));
				buttons.put(strategy, b);
				constraints.gridx = gridx++;
				buttonPanel.add(b, constraints);
			}

			GridBagConstraints filler = new GridBagConstraints();
			filler.gridx = gridx;
			filler.gridy = 0;
			filler.weightx = 1.0;
			filler.fill = GridBagConstraints.HORIZONTAL;
			buttonPanel.add(Box.createHorizontalGlue(), filler);
		}

		private void refreshButtonVisibility() {

			List<Release> visibleReleases = review.visibleReleases(candidate);
			Map<UpgradeStrategy, Release> targets = candidate.option().getTargets();
			for (Map.Entry<UpgradeStrategy, JButton> entry : buttons.entrySet()) {
				Release target = targets.get(entry.getKey());
				entry.getValue().setVisible(target != null && visibleReleases.contains(target));
			}
		}

		private static JButton createButton(Icon icon, String tooltip, String shortLabel, Release version) {

			JButton b = new JButton(icon) {

				@Override
				protected void paintComponent(Graphics g) {

					ButtonModel model = getModel();
					boolean pressed = model.isArmed() && model.isPressed();
					if (pressed || model.isRollover()) {

						Graphics2D g2 = (Graphics2D) g.create();
						try {
							g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
							int arc = JBUI.scale(4);
							g2.setColor(pressed ? JBUI.CurrentTheme.ActionButton.pressedBackground()
									: JBUI.CurrentTheme.ActionButton.hoverBackground());
							g2.fillRoundRect(0, 0, getWidth(), getHeight(), arc, arc);
							g2.setColor(pressed ? JBUI.CurrentTheme.ActionButton.pressedBorder()
									: JBUI.CurrentTheme.ActionButton.hoverBorder());
							g2.drawRoundRect(0, 0, getWidth() - 1, getHeight() - 1, arc, arc);
						} finally {
							g2.dispose();
						}
					}

					super.paintComponent(g);
				}

			};

			b.setToolTipText(tooltip);
			b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
			b.getAccessibleContext().setAccessibleName(shortLabel + " " + version.version());
			b.setBorderPainted(false);
			b.setContentAreaFilled(false);
			b.setOpaque(false);
			b.setFocusPainted(false);
			b.setRolloverEnabled(true);

			int padding = JBUI.scale(6);
			Dimension preferredSize = new Dimension(icon.getIconWidth() + padding, icon.getIconHeight() + padding);
			b.setPreferredSize(preferredSize);
			b.setMinimumSize(preferredSize);
			b.setMaximumSize(preferredSize);
			return b;
		}

		@Override
		public JComponent getTableCellEditorComponent(JTable table, @Nullable Object value, boolean isSelected, int row,
				int column) {
			refreshButtonVisibility();
			Color bg = isSelected ? table.getSelectionBackground() : table.getBackground();
			buttonPanel.setBorder(JBUI.Borders.empty());
			buttonPanel.setBackground(bg);
			for (Component ch : buttonPanel.getComponents()) {
				ch.setBackground(bg);
			}
			return buttonPanel;
		}

		@Override
		public @Nullable Object getCellEditorValue() {
			return null;
		}

	}

	static class DependencyCoordinateColumn extends ColumnInfo<UpdateCandidate, ArtifactId> {

		DefaultTableCellRenderer tcr = new DefaultTableCellRenderer() {

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {

				Component tableCellRendererComponent = super.getTableCellRendererComponent(table, value, isSelected,
						hasFocus, row, column);
				UpdateCandidate merged = ModelUtil.getRow(table, row);
				DependencyUpdateCandidate option = merged.option();

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

				setIcon(DependencyCoordinateColumn.this.getIcon(merged, table));

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
		Icon getIcon(UpdateCandidate row, JTable table) {

			DependencyUpdateCandidate option = row.option();
			Icon base = row.interfaceAssistant().getTableIcon(option.getDependency());

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
		public ArtifactId valueOf(UpdateCandidate item) {
			return item.getArtifactId();
		}

		@Override
		public TableCellRenderer getRenderer(UpdateCandidate item) {
			return tcr;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactId.class;
		}

	}

	static class CurrentVersionColumn extends ColumnInfo<UpdateCandidate, ArtifactVersion> {

		private final DependencyUpgradeReview review;

		private final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {

				Component component = super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row,
						column);
				UpdateCandidate candidate = ModelUtil.getRow(table, row);
				DeclaredVersions declaredVersions = candidate.getDeclaredVersions();
				EvaluatedDependencyRule rule = candidate.ruleResult();

				if (declaredVersions.hasConflict()) {
					setIcon(AllIcons.General.Warning);
				}
				else if (rule.isPresent()) {
					setIcon(rule.getIcon());
				}
				if (declaredVersions.hasConflict() || rule.isPresent()) {
					setToolTipText(conflictTooltip(candidate, rule));
				} else {
					setIcon(null);
					setToolTipText(null);
				}
				return component;
			}

		};

		CurrentVersionColumn(DependencyUpgradeReview review) {
			super(MessageBundle.message("dialog.column.current"));
			this.review = review;
		}

		@Override
		public @Nullable ArtifactVersion valueOf(UpdateCandidate item) {
			return item.currentVersion();
		}

		@Override
		public TableCellRenderer getRenderer(UpdateCandidate item) {
			return renderer;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactVersion.class;
		}

		private static String conflictTooltip(UpdateCandidate candidate, EvaluatedDependencyRule rule) {
			StringBuilder tooltip = new StringBuilder();
			DeclaredVersions declaredVersions = candidate.getDeclaredVersions();
			if (declaredVersions.hasConflict()) {
				tooltip.append(declaredVersions.getToolTipText());
			}
			if (rule.isPresent()) {
				tooltip.append(rule.getToolTipText());
			}

			if (StringUtils.isEmpty(tooltip.toString())) {
				return "";
			}
			return String.format("<html>%s</html>".formatted(tooltip));
		}

	}


	static class Upgrades extends ColumnInfo<UpdateCandidate, Object> {

		private final Map<UpdateCandidate, UpgradeTargetsToolbarEditor> editors = new ConcurrentHashMap<>();

		private final DependencyUpgradeReview review;

		Upgrades(DependencyUpgradeReview review) {
			super(MessageBundle.message("dialog.column.upgrades"));
			this.review = review;
		}

		private UpgradeTargetsToolbarEditor getToolbarEditor(UpdateCandidate row) {
			return editors.computeIfAbsent(row, it -> new UpgradeTargetsToolbarEditor(it, review));
		}

		@Override
		public @Nullable Object valueOf(UpdateCandidate item) {
			return null;
		}

		@Override
		public boolean isCellEditable(UpdateCandidate item) {
			return item.option().hasUpgradeTargets();
		}

		@Override
		public void setValue(UpdateCandidate item, Object value) {
			// Applied from picker {@link JButton} actions.
		}

		@Override
		public TableCellEditor getEditor(UpdateCandidate item) {
			return getToolbarEditor(item);
		}

		@Override
		public TableCellRenderer getRenderer(UpdateCandidate columnRow) {

			return (table, value, isSelected, hasFocus, row, column) -> {

				UpgradeTargetsToolbarEditor editor = getToolbarEditor(ModelUtil.getRow(table, row));
				JComponent c = editor.getTableCellEditorComponent(table, null, isSelected, 0, 0);
				c.setEnabled(table.isEnabled());
				return c;
			};
		}

		@Override
		public Class<?> getColumnClass() {
			return Object.class;
		}

	}

	static class UpdateToColumn extends ColumnInfo<UpdateCandidate, ArtifactVersion> {

		private final Map<UpdateCandidate, SuggestedVersionComboBoxEditor> editors = new ConcurrentHashMap<>();

		private final DependencyUpgradeReview review;

		UpdateToColumn(DependencyUpgradeReview review) {
			super(MessageBundle.message("dialog.column.upgradeTo"));
			this.review = review;
		}

		@Override
		public @Nullable ArtifactVersion valueOf(UpdateCandidate item) {
			return review.getUpdateTo(item);
		}

		@Override
		public TableCellRenderer getRenderer(UpdateCandidate columnRow) {

			return new DefaultTableCellRenderer() {

				@Override
				public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
						boolean hasFocus, int row, int column) {

					UpdateCandidate info = ModelUtil.getRow(table, row);
					return getEditor(info).getTableCellEditorComponent(table, value, isSelected, row, column);
				}

			};
		}

		@Override
		public SuggestedVersionComboBoxEditor getEditor(UpdateCandidate item) {
			return editors.computeIfAbsent(item, it -> new SuggestedVersionComboBoxEditor(review, it));
		}

		@Override
		public void setValue(UpdateCandidate item, ArtifactVersion value) {
			if (value != null && !value.matches(review.getUpdateTo(item))) {
				review.selectTarget(item, value);
			}
		}

		@Override
		public boolean isCellEditable(UpdateCandidate item) {
			return true;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactVersion.class;
		}

	}

	static class DoUpdateColumn extends ColumnInfo<UpdateCandidate, Boolean> {

		private final ApplyUpdateCheckboxEditor editor = new ApplyUpdateCheckboxEditor();

		private final DependencyUpgradeReview review;

		DoUpdateColumn(DependencyUpgradeReview review) {
			super(MessageBundle.message("dialog.column.upgrade"));
			this.review = review;
		}

		@Override
		public TableCellRenderer getRenderer(UpdateCandidate item) {

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
		public TableCellEditor getEditor(UpdateCandidate item) {
			return editor;
		}

		@Override
		public Boolean valueOf(UpdateCandidate item) {
			return review.isApplyUpdate(item);
		}

		@Override
		public void setValue(UpdateCandidate item, Boolean value) {
			review.setSelected(item, value);
		}

		@Override
		public boolean isCellEditable(UpdateCandidate item) {
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
