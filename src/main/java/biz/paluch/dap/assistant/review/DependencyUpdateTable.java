/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.assistant.review;

import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

import javax.swing.*;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.TableCellEditor;
import javax.swing.table.TableCellRenderer;
import javax.swing.table.TableColumnModel;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.assistant.DependencyUpgradeIcons;
import biz.paluch.dap.assistant.VersionStatus;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.checker.SecurityShieldIcons;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.EditorSchemes;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.editor.colors.CodeInsightColors;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.ui.BooleanTableCellEditor;
import com.intellij.ui.BooleanTableCellRenderer;
import com.intellij.ui.ClickListener;
import com.intellij.ui.CollectionComboBoxModel;
import com.intellij.ui.ColoredListCellRenderer;
import com.intellij.ui.ColoredTableCellRenderer;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.TableUtil;
import com.intellij.ui.TableViewSpeedSearch;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Dependency review table: the five columns with their renderers and editor,
 * and the point-based interactivity of the upgrade-targets strategy strip
 * (hover feedback, one-click apply, tooltips).
 *
 * <p>The strategy strip is painted by a stamp renderer; hit-testing mirrors the
 * strip geometry arithmetically so no layout runs on the mouse-move path.
 *
 * @author Mark Paluch
 */
class DependencyUpdateTable extends TableView<TableRow> {

	static final int DEPENDENCY_COLUMN_INDEX = 0;

	static final int UPGRADE_TARGETS_COLUMN_INDEX = 2;

	/**
	 * Strategies offered as one-click targets in the upgrades column. LATEST and
	 * RELEASE are not shown.
	 */
	private static final Set<UpgradeStrategy> UPGRADE_TARGET_STRATEGIES = EnumSet
			.complementOf(EnumSet.of(UpgradeStrategy.LATEST, UpgradeStrategy.RELEASE));

	/** Unscaled gap between strategy icons; mirrored by the strip hit-testing. */
	private static final int STRATEGY_ICON_GAP = 2;

	private static final int SUGGESTED_VERSION_CELL_PADDING = 6;

	/**
	 * Unscaled width of the suggested-version combo; also anchors the right-aligned
	 * release date in its dropdown.
	 */
	private static final int SUGGESTED_VERSION_WIDTH = 170;

	private final UpgradeReview review;

	private final BiConsumer<TableRow, RelativePoint> onNavigate;

	private final BiConsumer<TableRow, RelativePoint> onContextMenu;

	/** View row whose strategy strip is hovered, {@code -1} for none. */
	private int hoveredStrategyRow = -1;

	private @Nullable UpgradeStrategy hoveredStrategy;

	DependencyUpdateTable(UpgradeReview review, BiConsumer<TableRow, RelativePoint> onNavigate,
			BiConsumer<TableRow, RelativePoint> onContextMenu) {
		super(new ListTableModel<>(new DependencyCoordinateColumn(review), new CurrentVersionColumn(),
				new UpgradeTargetsColumn(review), new UpdateToColumn(review), new DoUpdateColumn(review)));
		this.review = review;
		this.onNavigate = onNavigate;
		this.onContextMenu = onContextMenu;
		setToolTipText("");

		configureAppearance();
		installEscForwarding();
		installMouseHandlers();
		installSpeedSearch();
	}

	private void configureAppearance() {

		setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
		setAutoCreateRowSorter(true);
		setShowGrid(true);
		setRowHeight(Math.max(getRowHeight(), strategyStripHeight()) + JBUI.scale(4));
		setIntercellSpacing(new Dimension(JBUI.scale(2), JBUI.scale(2)));
		getTableHeader().setReorderingAllowed(false);
		setPreferredScrollableViewportSize(new Dimension(JBUI.scale(820), JBUI.scale(420)));

		TableColumnModel columns = getColumnModel();
		columns.getColumn(0).setPreferredWidth(JBUI.scale(280));
		columns.getColumn(1).setPreferredWidth(JBUI.scale(90));
		columns.getColumn(2).setPreferredWidth(JBUI.scale(90));
		columns.getColumn(3).setPreferredWidth(JBUI.scale(180));
		TableUtil.setupCheckboxColumn(columns.getColumn(4), columns.getColumnMargin() + JBUI.scale(32));
	}

	/**
	 * Row-height floor matching the upgrades column button strip so rows keep the
	 * same height whether or not any row offers strategy buttons. Resolved per
	 * table so an IDE scale change is picked up by the next dialog.
	 */
	private static int strategyStripHeight() {

		int height = 0;
		for (UpgradeStrategy strategy : UPGRADE_TARGET_STRATEGIES) {
			height = Math.max(height, strategyIconSize(DependencyUpgradeIcons.resolveIcon(strategy)).height);
		}
		return height;
	}

	/**
	 * A disabled "cancel" action keeps JTable's ESC binding from consuming the
	 * event, so ESC reaches the dialog while the table has focus.
	 */
	private void installEscForwarding() {

		getActionMap().put("cancel", new AbstractAction() {

			@Override
			public void actionPerformed(ActionEvent e) {
			}

			@Override
			public boolean isEnabled() {
				return false;
			}

		});
	}

	private void installMouseHandlers() {

		addMouseMotionListener(new MouseMotionAdapter() {

			@Override
			public void mouseMoved(MouseEvent e) {
				trackStrategyHover(e);
			}

		});

		new ClickListener() {

			@Override
			public boolean onClick(MouseEvent e, int clickCount) {
				return applyStrategyOnClick(e, clickCount);
			}

		}.installOn(this);

		addMouseListener(new MouseAdapter() {

			@Override
			public void mouseClicked(MouseEvent e) {
				navigateOnDoubleClick(e);
			}

			@Override
			public void mouseExited(MouseEvent e) {
				setHoveredStrategy(-1, null);
			}

			@Override
			public void mousePressed(MouseEvent e) {
				showContextMenuOnPopupTrigger(e);
			}

			@Override
			public void mouseReleased(MouseEvent e) {
				showContextMenuOnPopupTrigger(e);
			}

		});
	}

	private void installSpeedSearch() {

		TableViewSpeedSearch<TableRow> speedSearch = new TableViewSpeedSearch<>(this, null) {

			@Override
			protected String getItemText(TableRow item) {
				return item.getSearchString();
			}

		};
		speedSearch.setupListeners();
	}

	private void navigateOnDoubleClick(MouseEvent e) {

		if (e.getClickCount() != 2 || !SwingUtilities.isLeftMouseButton(e) || e.getModifiersEx() != 0) {
			return;
		}

		e.consume();

		TableRow candidate = coordinateRowAt(e);
		if (candidate != null) {
			onNavigate.accept(candidate, new RelativePoint(this, e.getPoint()));
		}
	}

	private void showContextMenuOnPopupTrigger(MouseEvent e) {

		if (!e.isPopupTrigger()) {
			return;
		}

		TableRow candidate = coordinateRowAt(e);
		if (candidate != null) {
			onContextMenu.accept(candidate, new RelativePoint(this, e.getPoint()));
		}
	}

	/**
	 * Return the candidate under the mouse when it hovers the Dependency coordinate
	 * column, or {@literal null} for any other column or no row.
	 */
	private @Nullable TableRow coordinateRowAt(MouseEvent e) {

		Point p = e.getPoint();
		int row = rowAtPoint(p);
		int col = columnAtPoint(p);
		if (row < 0 || convertColumnIndexToModel(col) != DEPENDENCY_COLUMN_INDEX) {
			return null;
		}

		return ModelUtil.getRow(this, row);
	}

	@Override
	public @Nullable String getToolTipText(MouseEvent event) {

		Point point = event.getPoint();
		int row = rowAtPoint(point);
		UpgradeStrategy strategy = strategyAt(point, row);
		if (strategy == null) {
			return super.getToolTipText(event);
		}

		Release target = review.findRelease(ModelUtil.getRow(this, row), strategy);
		if (target == null) {
			return super.getToolTipText(event);
		}

		String prefix = MessageBundle.message("dialog.upgradeTarget." + strategy.name());
		String suffix = target.getVersion().toDocumentationString();
		return MessageBundle.message("dialog.upgradeTarget.tooltip", prefix, suffix);
	}

	private boolean applyStrategyOnClick(MouseEvent e, int clickCount) {

		if (clickCount != 1 || !SwingUtilities.isLeftMouseButton(e) || e.getModifiersEx() != 0) {
			return false;
		}

		Point point = e.getPoint();
		int row = rowAtPoint(point);
		UpgradeStrategy strategy = strategyAt(point, row);
		if (strategy == null) {
			return false;
		}

		review.applyStrategyTarget(ModelUtil.getRow(this, row), strategy);
		return true;
	}

	private void trackStrategyHover(MouseEvent e) {

		Point point = e.getPoint();
		int row = rowAtPoint(point);
		UpgradeStrategy strategy = strategyAt(point, row);
		setHoveredStrategy(strategy == null ? -1 : row, strategy);
		UIUtil.setCursor(this, strategy == null ? null : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
	}

	private void setHoveredStrategy(int row, @Nullable UpgradeStrategy strategy) {

		if (hoveredStrategyRow == row && hoveredStrategy == strategy) {
			return;
		}

		int previousRow = hoveredStrategyRow;
		this.hoveredStrategyRow = row;
		this.hoveredStrategy = strategy;
		repaintStrategyStrip(previousRow);
		repaintStrategyStrip(row);
	}

	private void repaintStrategyStrip(int row) {

		if (row < 0 || row >= getRowCount()) {
			return;
		}
		repaint(getCellRect(row, convertColumnIndexToView(UPGRADE_TARGETS_COLUMN_INDEX), true));
	}

	void clearStrategyHover() {
		setHoveredStrategy(-1, null);
	}

	/**
	 * Return the hovered strategy icon of the given view row, or {@literal null}
	 * when the mouse is not over one of the row's strategy icons.
	 */
	@Nullable
	UpgradeStrategy getHoveredStrategy(int row) {
		return row == hoveredStrategyRow ? hoveredStrategy : null;
	}

	/**
	 * Return the upgrade strategy whose icon is under the given point within the
	 * upgrade-targets column, or {@literal null} for any other column, no row, or a
	 * point between icons.
	 */
	private @Nullable UpgradeStrategy strategyAt(Point point, int row) {

		int column = columnAtPoint(point);
		if (row < 0 || column < 0 || convertColumnIndexToModel(column) != UPGRADE_TARGETS_COLUMN_INDEX) {
			return null;
		}

		if (!(getCellRenderer(row, column) instanceof UpgradeTargetsRenderer renderer)) {
			return null;
		}

		Rectangle cellRect = getCellRect(row, column, false);
		return renderer.getStrategyAt(ModelUtil.getRow(this, row),
				new Point(point.x - cellRect.x, point.y - cellRect.y), cellRect.height);
	}

	private static JPanel createStrategyStrip(Collection<? extends JComponent> components) {

		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(true);
		panel.setBorder(JBUI.Borders.empty());

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.anchor = GridBagConstraints.CENTER;
		constraints.insets = JBUI.insetsRight(STRATEGY_ICON_GAP);

		int gridx = 0;
		for (JComponent component : components) {
			constraints.gridx = gridx++;
			panel.add(component, constraints);
		}

		GridBagConstraints filler = new GridBagConstraints();
		filler.gridx = gridx;
		filler.gridy = 0;
		filler.weightx = 1.0;
		filler.fill = GridBagConstraints.HORIZONTAL;
		panel.add(Box.createHorizontalGlue(), filler);
		return panel;
	}

	private static Dimension strategyIconSize(Icon icon) {

		int padding = JBUI.scale(8);
		return new Dimension(icon.getIconWidth() + padding, icon.getIconHeight() + padding);
	}

	static class DependencyCoordinateColumn extends ColumnInfo<TableRow, ArtifactId> {

		private final UpgradeReview review;

		/**
		 * Weak-warning wave underline for rows coupled through a Shared Version
		 * Property: informative, never blocking. Resolved per dialog so a theme or
		 * scheme switch is picked up by the next dialog.
		 */
		private final SimpleTextAttributes sharedPropertyAttributes;

		private final ColoredTableCellRenderer renderer = new ColoredTableCellRenderer() {

			@Override
			protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus,
					int row, int column) {

				TableRow candidate = ModelUtil.getRow(table, row);
				List<TableRow> peers = review.getSharedPropertyPeers(candidate);
				boolean waved = !peers.isEmpty() || candidate.getDeclaredVersions().hasDeclarationDrift();
				append(candidate.getName(),
						waved ? sharedPropertyAttributes : SimpleTextAttributes.REGULAR_ATTRIBUTES);

				if (candidate instanceof GroupRow group) {
					append("  (%s)".formatted(group.getMemberLabel()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
				} else if (review.isAmbiguous(candidate)) {
					append("  (%s)".formatted(candidate.getArtifactId().groupId()),
							SimpleTextAttributes.GRAYED_ATTRIBUTES);
				}

				setIcon(peers.isEmpty() ? candidate.getTableIcon() : DependencyAssistantIcons.SHARED_PROPERTY);
				setToolTipText(review.getToolTip(candidate));
			}

		};

		DependencyCoordinateColumn(UpgradeReview review) {
			super(MessageBundle.message("dialog.column.dependency"));
			this.review = review;

			TextAttributes weakWarning = EditorSchemes.attributes(CodeInsightColors.WEAK_WARNING_ATTRIBUTES,
					new TextAttributes());
			this.sharedPropertyAttributes = new SimpleTextAttributes(
					SimpleTextAttributes.STYLE_PLAIN | SimpleTextAttributes.STYLE_WAVED, null,
					weakWarning.getEffectColor());
		}

		@Override
		public ArtifactId valueOf(TableRow item) {
			return item.getArtifactId();
		}

		@Override
		public Comparator<TableRow> getComparator() {
			return Comparator.comparing(TableRow::getName, String.CASE_INSENSITIVE_ORDER);
		}

		@Override
		public TableCellRenderer getRenderer(TableRow item) {
			return renderer;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactId.class;
		}

	}

	static class CurrentVersionColumn extends ColumnInfo<TableRow, ArtifactVersion> {

		private final DefaultTableCellRenderer renderer = new DefaultTableCellRenderer() {

			private @Nullable Font cachedFont;

			private int cachedFontSize = -1;

			@Override
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected,
					boolean hasFocus, int row, int column) {
				super.getTableCellRendererComponent(
						table, value, isSelected, hasFocus, row, column);

				TableRow candidate = ModelUtil.getRow(table, row);
				DeclaredVersions declaredVersions = candidate.getDeclaredVersions();
				DependencyRuleEvaluator rule = candidate.getRuleEvaluator();

				if (declaredVersions.hasVersionDrift()) {
					setIcon(DependencyAssistantIcons.DEPENDENCY_RULE_WARN);
				} else if (rule.isPresent()) {
					setIcon(rule.getIcon());
				} else {
					setIcon(null);
				}
				setFont(getCachedEditorFont(getFont().getSize()));

				if (declaredVersions.hasVersionDrift() || rule.isPresent()) {
					String toolTipText = candidate.getToolTipText();
					setToolTipText(StringUtils.isEmpty(toolTipText) ? null : toolTipText);
				} else {
					setToolTipText(null);
				}

				return this;
			}

			private Font getCachedEditorFont(int uiFontSize) {

				if (cachedFont == null || cachedFontSize != uiFontSize) {
					cachedFontSize = uiFontSize;
					cachedFont = EditorSchemes.editorFont(Font.PLAIN, uiFontSize);
				}

				return cachedFont;
			}

		};

		CurrentVersionColumn() {
			super(MessageBundle.message("dialog.column.current"));
		}

		@Override
		public @Nullable ArtifactVersion valueOf(TableRow item) {
			return item.getCurrentVersion();
		}

		@Override
		public Comparator<TableRow> getComparator() {
			return Comparator.comparing(TableRow::getCurrentVersion,
					Comparator.nullsFirst(Comparator.naturalOrder()));
		}

		@Override
		public TableCellRenderer getRenderer(TableRow item) {
			return renderer;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactVersion.class;
		}

	}

	static class UpgradeTargetsColumn extends ColumnInfo<TableRow, Object> {

		private final UpgradeTargetsRenderer renderer;

		UpgradeTargetsColumn(UpgradeReview review) {
			super(MessageBundle.message("dialog.column.upgrades"));
			this.renderer = new UpgradeTargetsRenderer(review);
		}

		@Override
		public @Nullable Object valueOf(TableRow item) {
			return null;
		}

		@Override
		public TableCellRenderer getRenderer(TableRow item) {
			return renderer;
		}

		@Override
		public Class<?> getColumnClass() {
			return Object.class;
		}

	}

	/**
	 * Paint-only stamp of the strategy button strip. Interactivity (hover feedback,
	 * clicks, tooltips) is provided by {@link DependencyUpdateTable} through
	 * point-based hit-testing; no cell editing is involved.
	 */
	static class UpgradeTargetsRenderer implements TableCellRenderer {

		private final UpgradeReview review;

		private final Map<UpgradeStrategy, StrategyIconLabel> icons = new EnumMap<>(UpgradeStrategy.class);

		private final JPanel panel;

		UpgradeTargetsRenderer(UpgradeReview review) {

			this.review = review;
			for (UpgradeStrategy strategy : UPGRADE_TARGET_STRATEGIES) {

				Icon icon = DependencyUpgradeIcons.resolveIcon(strategy);
				StrategyIconLabel label = new StrategyIconLabel(icon);
				label.setHorizontalAlignment(SwingConstants.CENTER);
				label.setPreferredSize(strategyIconSize(icon));
				label.setMinimumSize(strategyIconSize(icon));
				icons.put(strategy, label);
			}
			this.panel = createStrategyStrip(icons.values());
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {

			TableRow candidate = ModelUtil.getRow(table, row);
			UpgradeStrategy hovered = table instanceof DependencyUpdateTable updateTable
					? updateTable.getHoveredStrategy(row)
					: null;
			icons.forEach((strategy, label) -> {
				label.setVisible(review.findRelease(candidate, strategy) != null);
				label.setHovered(strategy == hovered);
			});
			panel.setBackground(RenderingUtil.getBackground(table, isSelected));
			panel.setEnabled(table.isEnabled());
			return panel;
		}

		/**
		 * Return the strategy whose icon is at {@code cellPoint} within a cell of
		 * {@code cellHeight}, or {@literal null} if the point hits no visible icon.
		 * Mirrors the strip geometry (left-packed fixed-size icons separated by a fixed
		 * gap) arithmetically, without a layout pass.
		 */
		@Nullable
		UpgradeStrategy getStrategyAt(TableRow candidate, Point cellPoint, int cellHeight) {

			int gap = JBUI.scale(STRATEGY_ICON_GAP);
			int x = 0;
			for (Map.Entry<UpgradeStrategy, StrategyIconLabel> entry : icons.entrySet()) {

				if (review.findRelease(candidate, entry.getKey()) == null) {
					continue;
				}

				Dimension size = entry.getValue().getPreferredSize();
				if (new Rectangle(x, (cellHeight - size.height) / 2, size.width, size.height)
						.contains(cellPoint)) {
					return entry.getKey();
				}
				x += size.width + gap;
			}
			return null;
		}

		/**
		 * Icon label painting the action-button hover background when the mouse is over
		 * it, mimicking a toolbar {@code ActionButton} rollover.
		 */
		static class StrategyIconLabel extends JLabel {

			private boolean hovered;

			StrategyIconLabel(Icon icon) {
				super(icon);
			}

			void setHovered(boolean hovered) {
				this.hovered = hovered;
			}

			@Override
			protected void paintComponent(Graphics g) {

				if (hovered) {
					ActionButtonLook.SYSTEM_LOOK.paintLookBackground(g, new Rectangle(0, 0, getWidth(), getHeight()),
							JBUI.CurrentTheme.ActionButton.hoverBackground());
				}
				super.paintComponent(g);
			}

		}

	}

	static class UpdateToColumn extends ColumnInfo<TableRow, ArtifactVersion> {

		private final UpgradeReview review;

		private final SuggestedVersionRenderer renderer;

		private final SuggestedVersionComboBoxEditor editor;

		UpdateToColumn(UpgradeReview review) {
			super(MessageBundle.message("dialog.column.upgradeTo"));
			this.review = review;
			this.renderer = new SuggestedVersionRenderer(review);
			this.editor = new SuggestedVersionComboBoxEditor(review);
		}

		@Override
		public @Nullable ArtifactVersion valueOf(TableRow item) {
			return review.getUpdateTo(item);
		}

		@Override
		public Comparator<TableRow> getComparator() {
			return Comparator.comparing(review::getUpdateTo, Comparator.nullsFirst(Comparator.naturalOrder()));
		}

		@Override
		public TableCellRenderer getRenderer(TableRow item) {
			return renderer;
		}

		@Override
		public TableCellEditor getEditor(TableRow item) {
			return editor;
		}

		@Override
		public void setValue(TableRow item, @Nullable ArtifactVersion value) {
			if (value != null) {
				review.setVersion(item, value);
			}
		}

		@Override
		public boolean isCellEditable(TableRow item) {
			return true;
		}

		@Override
		public Class<?> getColumnClass() {
			return ArtifactVersion.class;
		}

	}

	/**
	 * Configure the shared look of the suggested-version combo used by both the
	 * cell editor and the paint-only renderer, so the two cannot drift apart.
	 */
	private static VersionOptionCellRenderer configureSuggestedVersionCombo(ComboBox<Release> combo) {

		VersionOptionCellRenderer optionRenderer = new VersionOptionCellRenderer();
		combo.setRenderer(optionRenderer);
		combo.setBorder(JBUI.Borders.empty(0, SUGGESTED_VERSION_CELL_PADDING));
		combo.setMinimumAndPreferredWidth(JBUI.scale(SUGGESTED_VERSION_WIDTH));
		return optionRenderer;
	}

	/**
	 * Shared editor for the suggested-version column: a combo box of the visible
	 * release options, re-targeted to the edited row when editing starts. Picking
	 * an option stops editing; {@link UpdateToColumn#setValue} pushes the pick into
	 * the review.
	 */
	static class SuggestedVersionComboBoxEditor extends AbstractCellEditor implements TableCellEditor {

		private final UpgradeReview review;

		private final ComboBox<Release> combo = new ComboBox<>();

		private final VersionOptionCellRenderer optionRenderer;

		private boolean refreshing;

		SuggestedVersionComboBoxEditor(UpgradeReview review) {

			this.review = review;
			this.optionRenderer = configureSuggestedVersionCombo(combo);
			combo.registerTableCellEditor(this);
			combo.addActionListener(e -> {
				if (!refreshing) {
					stopCellEditing();
				}
			});
		}

		@Override
		public Component getTableCellEditorComponent(JTable table, @Nullable Object value, boolean isSelected, int row,
				int column) {

			TableRow candidate = ModelUtil.getRow(table, row);
			optionRenderer.setCandidate(candidate);
			combo.setFont(table.getFont());

			refreshing = true;
			try {
				combo.setModel(new CollectionComboBoxModel<>(review.getReleaseOptions(candidate),
						review.getSelectedRelease(candidate)));
			} finally {
				refreshing = false;
			}
			return combo;
		}

		@Override
		public @Nullable Object getCellEditorValue() {
			return combo.getSelectedItem() instanceof Release release ? release.version() : null;
		}

	}

	/**
	 * Paint-only stamp for the suggested-version column: a combo box showing the
	 * candidate's selected release.
	 */
	static class SuggestedVersionRenderer implements TableCellRenderer {

		private final UpgradeReview review;

		private final ComboBox<Release> combo = new ComboBox<>();

		private final VersionOptionCellRenderer optionRenderer;

		private final CollectionComboBoxModel<Release> model = new CollectionComboBoxModel<>(new ArrayList<>());

		SuggestedVersionRenderer(UpgradeReview review) {

			this.review = review;
			this.optionRenderer = configureSuggestedVersionCombo(combo);
			combo.setModel(model);
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {

			TableRow candidate = ModelUtil.getRow(table, row);
			optionRenderer.setCandidate(candidate);
			combo.setFont(table.getFont());

			Release selected = review.getSelectedRelease(candidate);
			if (model.getSize() != 1 || !selected.equals(model.getElementAt(0))) {
				model.replaceAll(List.of(selected));
				model.setSelectedItem(selected);
			}
			combo.setEnabled(table.isEnabled());
			return combo;
		}

	}

	/**
	 * List cell renderer that shows an icon (older / newer patch / minor / major)
	 * plus version text, graying out versions that do not satisfy the dependency
	 * rule. Options are classified relative to the candidate set via
	 * {@link #setCandidate(TableRow)}.
	 */
	static class VersionOptionCellRenderer extends ColoredListCellRenderer<Release> {

		private final ReleaseDateFormatter formatter = ReleaseDateFormatter.create();

		private @Nullable TableRow candidate;

		VersionOptionCellRenderer() {
			setIconTextGap(JBUI.scale(4));
			setBorder(JBUI.Borders.empty());
		}

		void setCandidate(TableRow candidate) {
			this.candidate = candidate;
		}

		@Override
		protected void customizeCellRenderer(JList<? extends Release> list, @Nullable Release value, int index,
				boolean selected, boolean hasFocus) {

			if (value == null || candidate == null) {
				return;
			}

			VersionStatus status = candidate.getStatus(value.getVersion());
			append(value.getVersion().toString(), status.isRuleViolation() ? SimpleTextAttributes.GRAYED_ATTRIBUTES
					: SimpleTextAttributes.REGULAR_ATTRIBUTES);

			if (!candidate.getUpgrade().getReleases().contains(value)) {
				setIcon(AllIcons.Nodes.WarningIntroduction);
				return;
			}

			if (value.releaseDate() != null) {
				append("  " + formatter.format(value.releaseDate()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
			}

			setIcon(status.getIcon(SecurityShieldIcons.FILLED));
		}

	}

	static class DoUpdateColumn extends ColumnInfo<TableRow, Boolean> {

		private final UpgradeReview review;

		private final BooleanTableCellRenderer renderer = new BooleanTableCellRenderer();

		private final BooleanTableCellEditor editor = new BooleanTableCellEditor();

		DoUpdateColumn(UpgradeReview review) {
			super(MessageBundle.message("dialog.column.upgrade"));
			this.review = review;
		}

		@Override
		public TableCellRenderer getRenderer(TableRow item) {
			return renderer;
		}

		@Override
		public TableCellEditor getEditor(TableRow item) {
			return editor;
		}

		@Override
		public Boolean valueOf(TableRow item) {
			return review.isApplyUpdate(item);
		}

		@Override
		public void setValue(TableRow item, Boolean value) {
			review.setSelected(item, value);
		}

		@Override
		public boolean isCellEditable(TableRow item) {
			return true;
		}

		@Override
		public Class<?> getColumnClass() {
			return Boolean.class;
		}

	}

}
