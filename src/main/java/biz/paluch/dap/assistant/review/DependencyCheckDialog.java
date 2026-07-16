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

package biz.paluch.dap.assistant.review;

import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

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
import biz.paluch.dap.assistant.check.DependencyCheckResult;
import biz.paluch.dap.assistant.check.DependencySiteNavigator;
import biz.paluch.dap.checker.SecurityShieldIcons;
import biz.paluch.dap.plan.PlannedUpgrade;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.EditorSchemes;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.codeInsight.daemon.impl.HighlightInfoType;
import com.intellij.icons.AllIcons;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionButtonLook;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.editor.markup.TextAttributes;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.*;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.ui.render.RenderingUtil;
import com.intellij.ui.table.TableView;
import com.intellij.util.ui.ColumnInfo;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Dialog showing declared dependency versions and update suggestions across all
 * supported build tools (Maven, Gradle, GitHub Actions, NPM, and Antora).
 *
 * @author Mark Paluch
 */
public class DependencyCheckDialog extends DialogWrapper {

	private static final int DEPENDENCY_COLUMN_INDEX = 0;

	private static final int UPGRADE_TARGETS_COLUMN_INDEX = 2;

	/**
	 * Strategies offered as one-click targets in the upgrades column. LATEST and
	 * RELEASE are not shown.
	 */
	private static final Set<UpgradeStrategy> UPGRADE_TARGET_STRATEGIES = EnumSet
			.complementOf(EnumSet.of(UpgradeStrategy.LATEST, UpgradeStrategy.RELEASE));

	private static final int SUGGESTED_VERSION_CELL_PADDING = 6;

	/**
	 * Unscaled width of the suggested-version combo; also anchors the right-aligned
	 * release date in its dropdown.
	 */
	private static final int SUGGESTED_VERSION_WIDTH = 170;

	private static final KeyStroke TRANSFER_SHORTCUT = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER,
			SystemInfo.isMac ? InputEvent.META_DOWN_MASK : InputEvent.CTRL_DOWN_MASK);

	private final Project project;

	private final UpgradeReview review;

	private final FileScope scope;

	private final AssistantReviewActions actions;

	private final DependencyCheckComponents components;

	private final Action openInPlanAction;

	private volatile boolean applyingUpdates;

	private volatile @Nullable ProgressIndicator updateProgress;

	public DependencyCheckDialog(Project project, DependencyCheckResult result, String title,
			boolean fromEditor) {
		super(project, false, IdeModalityType.MODELESS);
		this.project = project;
		this.scope = result.scope();
		this.actions = new AssistantReviewActions(project, fromEditor);
		this.review = new UpgradeReview(result);
		this.components = new DependencyCheckComponents(this.review, getDisposable(), this::navigateToSites,
				this::showContextMenu);
		setTitle(title);
		this.openInPlanAction = createOpenInPlanAction();
		init();
		installTransferShortcut();
		review.addListener(change -> updateActions(), getDisposable());
		updateActions();
	}

	/**
	 * Reflect the armed-row count in the OK button text and enable the transfer
	 * action, in one pass over the visible candidates.
	 */
	private void updateActions() {

		List<TableRow> visible = review.getCandidates();
		long selected = visible.stream().filter(review::isApplyUpdate).count();

		openInPlanAction.setEnabled(selected > 0);
		updateOkButtonText(visible, selected);
	}

	/**
	 * Reflect the selected-row count in the OK button; collapses to "All", or "All
	 * Shown" when a filter hides rows.
	 */
	private void updateOkButtonText(List<TableRow> visible, long selected) {

		if (selected == 0) {
			setOKButtonText(CommonBundle.getOkButtonText());
			return;
		}

		if (selected == visible.size()) {
			setOKButtonText(MessageBundle.message(visible.size() == review.getAllCandidates().size()
					? "dialog.ok.update.all"
					: "dialog.ok.update.all-shown"));
			return;
		}

		setOKButtonText(MessageBundle.message("dialog.ok.update", selected));
	}

	/**
	 * OK and Cancel stay on the right; OK still applies the armed updates directly.
	 * The transfer button is left-aligned via {@link #createLeftSideActions()}.
	 */
	@Override
	protected Action[] createActions() {
		return new Action[] {getOKAction(), getCancelAction()};
	}

	/**
	 * Left-align the transfer button with a muted shortcut hint beside it, like the
	 * "Open in Find Window" button of the Find in Files dialog. OK and Cancel stay on
	 * the right.
	 */
	@Override
	protected JPanel createSouthAdditionalPanel() {

		JBLabel shortcutHint = new JBLabel(KeymapUtil.getKeystrokeText(TRANSFER_SHORTCUT));
		shortcutHint.setEnabled(false);
		shortcutHint.setBorder(JBUI.Borders.emptyLeft(6));

		JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
		panel.add(new JButton(openInPlanAction));
		panel.add(shortcutHint);
		return panel;
	}

	private Action createOpenInPlanAction() {

		AbstractAction action = new AbstractAction(MessageBundle.message("dialog.openInPlan")) {

			@Override
			public void actionPerformed(ActionEvent e) {
				openInUpgradePlan();
			}

		};
		action.setEnabled(false);
		action.putValue(Action.SHORT_DESCRIPTION, MessageBundle.message("dialog.openInPlan.description"));
		return action;
	}

	/**
	 * Bind the transfer shortcut (Cmd+Enter on macOS, Ctrl+Enter elsewhere), matching
	 * the Find in Files dialog's "Open in Find Window"; the disabled action stays
	 * inert until a row is armed.
	 */
	private void installTransferShortcut() {

		getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
				.put(TRANSFER_SHORTCUT, "openInUpgradePlan");
		getRootPane().getActionMap().put("openInUpgradePlan", openInPlanAction);
	}

	/**
	 * Transfer the armed rows into the Upgrade Plan tool window and close the dialog
	 * without touching build files; OK remains the direct-apply path.
	 */
	private void openInUpgradePlan() {

		if (applyingUpdates) {
			return;
		}

		this.components.stopEditing();

		Map<PlannedUpgrade, UpgradeSelection> selection = getSelection();
		if (selection.isEmpty()) {
			return;
		}

		actions.openInUpgradePlan(selection, scope);
		restartHighlighting();
		close(OK_EXIT_CODE);
	}

	private Map<PlannedUpgrade, UpgradeSelection> getSelection() {

		Map<PlannedUpgrade, UpgradeSelection> result = new LinkedHashMap<>();
		for (TableRow candidate : review.getCandidates()) {
			UpgradeSelection selection = review.getSelection(candidate);
			if (selection.isApplyUpdate()) {
				result.put(candidate, selection);
			}
		}

		return result;
	}

	/**
	 * Select and reveal the table row for the given artifact, e.g. when the dialog
	 * was opened from a gutter icon or a documentation link. When the visibility
	 * filter hides the row, the filter is cleared first so the row can show.
	 *
	 * @param artifactId the artifact whose row to select; group rows match through
	 * their members.
	 */
	public void selectCandidate(ArtifactId artifactId) {

		TableRow candidate = findCandidate(artifactId);
		if (candidate == null) {
			return;
		}

		if (!review.getCandidates().contains(candidate)) {
			this.components.clearVersionFilter();
		}

		this.components.select(candidate);
	}

	private @Nullable TableRow findCandidate(ArtifactId artifactId) {

		for (TableRow candidate : review.getAllCandidates()) {
			if (represents(candidate, artifactId)) {
				return candidate;
			}
		}
		return null;
	}

	private static boolean represents(TableRow candidate, ArtifactId artifactId) {

		if (candidate.getArtifactId().equals(artifactId)) {
			return true;
		}

		return candidate instanceof GroupRow group
				&& group.getMembers().stream().anyMatch(member -> member.getArtifactId().equals(artifactId));
	}

	/**
	 * Run a Dependency Site Find for the double-clicked row and present the result
	 * for navigation.
	 */
	private void navigateToSites(TableRow candidate, RelativePoint where) {
		new DependencySiteNavigator(project, getDisposable(), this::doCancelAction, scope.toList())
				.browse(candidate.toQuery(), where);
	}

	/**
	 * Show the coordinate-column context menu for the right-clicked row: add the
	 * artifact to {@code dependencyfile.json}, or open the Dependency Sites popup.
	 */
	private void showContextMenu(TableRow candidate, RelativePoint where) {

		DefaultActionGroup group = new DefaultActionGroup();
		group.add(new AnAction(MessageBundle.message("dialog.action.addToDependencyfile"),
				MessageBundle.message("dialog.action.addToDependencyfile.description"), AllIcons.FileTypes.Json) {

			@Override
			public void update(AnActionEvent e) {
				Presentation presentation = e.getPresentation();
				presentation.setEnabled(actions.canAddToDependencyfile(candidate));
			}

			@Override
			public ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.BGT;
			}

			@Override
			public void actionPerformed(AnActionEvent e) {
				doCancelAction();
				actions.addToDependencyfile(candidate);
			}

		});
		group.add(new AnAction(MessageBundle.message("dialog.action.openSites"),
				MessageBundle.message("dialog.action.openSites.description"), AllIcons.Actions.DependencyAnalyzer) {

			@Override
			public ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.BGT;
			}

			@Override
			public void actionPerformed(AnActionEvent e) {
				navigateToSites(candidate, where);
			}

		});

		DataContext dataContext = DataManager.getInstance().getDataContext(this.components);
		JBPopupFactory.getInstance()
				.createActionGroupPopup(candidate.getName(), group, dataContext,
						JBPopupFactory.ActionSelectionAid.MNEMONICS, true)
				.show(where);
	}

	/**
	 * Components panel for the dependency check dialog, containing the table and
	 * related controls.
	 */
	static class DependencyCheckComponents extends JPanel implements UiDataProvider {

		private static final int STRATEGY_STRIP_HEIGHT = getStrategyStripHeight();

		private final UpgradeReview review;

		private final ListTableModel<TableRow> tableModel;

		private final DependencyUpdateTable table;

		private final ActionToolbar toolbar;

		private final ComboBox<UpgradeReview.UpgradeStrategies> strategyComboBox;

		private final JCheckBox filterVersionsCheckBox;

		private @Nullable CopyProvider copyProvider;

		DependencyCheckComponents(UpgradeReview review, Disposable parent,
				BiConsumer<TableRow, RelativePoint> onNavigate,
				BiConsumer<TableRow, RelativePoint> onContextMenu) {
			super(new BorderLayout());
			this.review = review;

			this.tableModel = new ListTableModel<>(new DependencyCoordinateColumn(review),
					new CurrentVersionColumn(), new UpgradeTargetsColumn(review), new UpdateToColumn(review),
					new DoUpdateColumn(review));
			this.table = new DependencyUpdateTable(tableModel, review, onNavigate, onContextMenu);
			this.strategyComboBox = new ComboBox<>(strategyOptions(review));
			this.filterVersionsCheckBox = new JCheckBox(MessageBundle.message("dialog.filter.version.suggestions"),
					this.review.isHideUpToDate());

			ActionGroup toolbarGroup = createToolbarGroup();
			this.toolbar = ActionManager.getInstance().createActionToolbar("DependencyVersions", toolbarGroup,
					true);

			initialize(parent);
		}

		void setCopyProvider(CopyProvider copyProvider) {
			this.copyProvider = copyProvider;
		}

		@Override
		public void uiDataSnapshot(DataSink sink) {

			if (copyProvider != null) {
				sink.set(PlatformDataKeys.COPY_PROVIDER, copyProvider);
			}
		}

		@Nullable
		TableRow getSelectedCandidate() {
			return table.getSelectedObject();
		}

		private void initialize(Disposable parent) {

			tableModel.setItems(this.review.getCandidates());
			table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

			review.addListener(change -> {

				if (change.reload()) {
					stopEditing();
					table.clearStrategyHover();
					tableModel.setItems(review.getCandidates());
					return;
				}

				TableRow candidate = change.candidate();
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
			table.setRowHeight(Math.max(table.getRowHeight(), STRATEGY_STRIP_HEIGHT) + 4);
			table.setIntercellSpacing(new Dimension(JBUI.scale(2), JBUI.scale(2)));
			table.getTableHeader().setReorderingAllowed(false);

			table.setPreferredScrollableViewportSize(new Dimension(JBUI.scale(820), JBUI.scale(420)));

			TableColumnModel columns = table.getColumnModel();

			columns.getColumn(0).setPreferredWidth(JBUI.scale(280));
			columns.getColumn(1).setPreferredWidth(JBUI.scale(90));
			columns.getColumn(2).setPreferredWidth(JBUI.scale(90));
			columns.getColumn(3).setPreferredWidth(JBUI.scale(180));
			TableUtil.setupCheckboxColumn(columns.getColumn(4), columns.getColumnMargin() + JBUI.scale(32));

			toolbar.setTargetComponent(table);
			installSpeedSearch(table);

			add(createToolbarPanel(), BorderLayout.NORTH);
			add(new JBScrollPane(table), BorderLayout.CENTER);

			JComponent warningBanner = createWarningBanner();
			if (warningBanner != null) {
				add(warningBanner, BorderLayout.SOUTH);
			}
		}

		private JPanel createToolbarPanel() {

			filterVersionsCheckBox.setToolTipText(MessageBundle.message("dialog.filter.version.tooltip"));
			filterVersionsCheckBox.addItemListener(e -> review.setHideUpToDate(filterVersionsCheckBox.isSelected()));

			strategyComboBox.setToolTipText(MessageBundle.message("dialog.upgradeStrategy.tooltip"));
			strategyComboBox.setSelectedItem(this.review.getUpgradeStrategy());
			strategyComboBox.setRenderer(SimpleListCellRenderer.create((label, value, index) -> {
				label.setText(MessageBundle.message(value.getMessageKey()));
				label.setIcon(value.getIcon());
				label.setIconTextGap(JBUI.scale(4));
			}));
			strategyComboBox.addItemListener(e -> {
				if (e.getStateChange() != ItemEvent.SELECTED) {
					return;
				}
				review.applyStrategyToAll((UpgradeReview.UpgradeStrategies) e.getItem());
			});

			JPanel strategyPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(5), 0));
			strategyPanel.add(new JLabel(MessageBundle.message("dialog.upgradeStrategy.label")));
			strategyPanel.add(strategyComboBox);

			JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, JBUI.scale(8), 0));
			rightPanel.add(strategyPanel);
			rightPanel.add(toolbar.getComponent());

			JPanel leftPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, JBUI.scale(12), 0));
			leftPanel.add(filterVersionsCheckBox);

			JPanel toolbarPanel = new JPanel(new BorderLayout());
			toolbarPanel.add(leftPanel, BorderLayout.WEST);
			toolbarPanel.add(rightPanel, BorderLayout.EAST);
			return toolbarPanel;
		}

		private @Nullable JComponent createWarningBanner() {

			List<String> errors = this.review.getErrors();
			if (errors.isEmpty()) {
				return null;
			}

			String errorText = "<html><b>"
					+ StringUtil.escapeXmlEntities(MessageBundle.message("dialog.warnings.prefix"))
					+ "</b><br>"
					+ errors.stream()
							.map(error -> MessageBundle.message("dialog.warnings.bullet") + error)
							.map(StringUtil::escapeXmlEntities)
							.collect(Collectors.joining("<br>"))
					+ "</html>";

			InlineBanner warningBanner = new InlineBanner(errorText, EditorNotificationPanel.Status.Warning);
			warningBanner.showCloseButton(false);
			return JBUI.Panels.simplePanel(warningBanner).withBorder(JBUI.Borders.emptyTop(8));
		}

		private static void installSpeedSearch(TableView<TableRow> table) {

			TableViewSpeedSearch<TableRow> speedSearch = new TableViewSpeedSearch<>(table, null) {

				@Override
				protected String getItemText(TableRow item) {
					return item.getArtifactId() + " " + item.getDependencyName() + " " + item.getName();
				}

			};
			speedSearch.setupListeners();
		}

		private DefaultActionGroup createToolbarGroup() {

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
			TableUtil.stopEditing(table);
		}

		/**
		 * Clear the version-suggestion filter through the checkbox so the control state
		 * and the review stay in sync.
		 */
		void clearVersionFilter() {
			filterVersionsCheckBox.setSelected(false);
		}

		/**
		 * Select and reveal the row of the given candidate.
		 */
		void select(TableRow candidate) {

			int modelRow = tableModel.indexOf(candidate);
			if (modelRow < 0) {
				return;
			}

			int viewRow = table.convertRowIndexToView(modelRow);
			table.setRowSelectionInterval(viewRow, viewRow);
			table.scrollRectToVisible(table.getCellRect(viewRow, 0, true));
		}

		void setControlsEnabled(boolean enabled) {
			UIUtil.setEnabled(this.table, enabled, true);
			UIUtil.setEnabled(this.filterVersionsCheckBox, enabled, true);
			UIUtil.setEnabled(this.strategyComboBox, enabled, true);
			UIUtil.setEnabled(this.toolbar.getComponent(), enabled, true);
		}

		/**
		 * Return the upgrade-strategy entries to offer: the {@code Safe} entry is added
		 * only when at least one unfiltered candidate is vulnerable.
		 */
		private static UpgradeReview.UpgradeStrategies[] strategyOptions(UpgradeReview review) {

			List<UpgradeReview.UpgradeStrategies> options = new ArrayList<>();
			for (UpgradeReview.UpgradeStrategies strategy : UpgradeReview.UpgradeStrategies.values()) {
				if (strategy == UpgradeReview.UpgradeStrategies.SAFE && !review.isSafeStrategyAvailable()) {
					continue;
				}
				options.add(strategy);
			}
			return options.toArray(UpgradeReview.UpgradeStrategies[]::new);
		}

		/**
		 * Row-height floor matching the upgrades column button strip so rows keep the
		 * same height whether or not any row offers strategy buttons.
		 */
		private static int getStrategyStripHeight() {

			int height = 0;
			for (UpgradeStrategy strategy : UPGRADE_TARGET_STRATEGIES) {
				height = Math.max(height, strategyIconSize(DependencyUpgradeIcons.resolveIcon(strategy)).height);
			}
			return height;
		}

	}

	@Override
	protected JComponent createCenterPanel() {
		return this.components;
	}

	@Override
	protected @Nullable String getDimensionServiceKey() {
		return "DependencyAssistant.DependencyCheckDialog";
	}

	@Override
	public JComponent getPreferredFocusedComponent() {
		return this.components.table;
	}

	@Override
	protected void doOKAction() {
		if (applyingUpdates) {
			return;
		}

		this.components.stopEditing();

		List<DependencyUpdate> updates = review.getSelectedUpdates();
		if (updates.isEmpty()) {
			super.doOKAction();
			return;
		}

		setBusy(true);
		new ApplyUpdatesTask(project, updates).queue();
	}

	@Override
	public void doCancelAction() {
		if (applyingUpdates) {
			ProgressIndicator indicator = this.updateProgress;
			if (indicator != null) {
				indicator.cancel();
			}
			return;
		}
		restartHighlighting();
		super.doCancelAction();
	}

	private void setBusy(boolean busy) {

		this.applyingUpdates = busy;
		setOKActionEnabled(!busy);
		this.components.setControlsEnabled(!busy);
	}

	class ApplyUpdatesTask extends Task.Backgroundable {

		private final List<DependencyUpdate> updates;

		private ApplyUpdatesTask(Project project, List<DependencyUpdate> updates) {
			super(project, MessageBundle.message("intention.UpgradingDependencies.text"), true);
			this.updates = updates;
		}

		@Override
		public void run(ProgressIndicator indicator) {
			updateProgress = indicator;
			indicator.setIndeterminate(true);
			indicator.setText(MessageBundle.message("intention.UpgradingDependencies.text"));
			actions.applyUpdates(scope.toList(), updates, indicator);
		}

		@Override
		public void onSuccess() {

			restartHighlighting();
			close(OK_EXIT_CODE);
		}

		@Override
		public void onThrowable(Throwable error) {
			actions.reportApplyError(error);
		}

		@Override
		public void onFinished() {

			updateProgress = null;
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
			psiManager.stream(scope.toList()).forEach(psiFile -> analyzer.restart(psiFile, "Dependency Check"));
		});
	}

	static class DependencyUpdateTable extends TableView<TableRow> {

		private final UpgradeReview review;

		private final BiConsumer<TableRow, RelativePoint> onNavigate;

		private final BiConsumer<TableRow, RelativePoint> onContextMenu;

		/** View row whose strategy strip is hovered, {@code -1} for none. */
		private int hoveredStrategyRow = -1;

		private @Nullable UpgradeStrategy hoveredStrategy;

		DependencyUpdateTable(ListTableModel<TableRow> model, UpgradeReview review,
				BiConsumer<TableRow, RelativePoint> onNavigate,
				BiConsumer<TableRow, RelativePoint> onContextMenu) {
			super(model);
			this.review = review;
			this.onNavigate = onNavigate;
			this.onContextMenu = onContextMenu;
			setToolTipText("");

			// A disabled "cancel" action keeps JTable's ESC binding from consuming
			// the event, so ESC reaches the dialog while the table has focus.
			getActionMap().put("cancel", new AbstractAction() {

				@Override
				public void actionPerformed(ActionEvent e) {
				}

				@Override
				public boolean isEnabled() {
					return false;
				}

			});

			addMouseMotionListener(new MouseMotionAdapter() {

				@Override
				public void mouseMoved(MouseEvent e) {
					trackStrategyHover(e);
				}

			});

			addMouseListener(new MouseAdapter() {

				@Override
				public void mouseClicked(MouseEvent e) {
					applyStrategyOnClick(e);
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

			UpgradeStrategy strategy = strategyAt(event.getPoint());
			if (strategy == null) {
				return super.getToolTipText(event);
			}

			TableRow candidate = ModelUtil.getRow(this, rowAtPoint(event.getPoint()));
			Release target = review.resolveTarget(candidate, strategy);
			if (target == null) {
				return super.getToolTipText(event);
			}

			String prefix = MessageBundle.message("dialog.upgradeTarget." + strategy.name());
			String suffix = target.getVersion().toDocumentationString();
			return MessageBundle.message("dialog.upgradeTarget.tooltip", prefix, suffix);
		}

		private void applyStrategyOnClick(MouseEvent e) {

			if (e.getClickCount() != 1 || !SwingUtilities.isLeftMouseButton(e) || e.getModifiersEx() != 0) {
				return;
			}

			UpgradeStrategy strategy = strategyAt(e.getPoint());
			if (strategy == null) {
				return;
			}

			e.consume();
			review.applyStrategyTarget(ModelUtil.getRow(this, rowAtPoint(e.getPoint())), strategy);
		}

		private void trackStrategyHover(MouseEvent e) {

			UpgradeStrategy strategy = strategyAt(e.getPoint());
			setHoveredStrategy(strategy == null ? -1 : rowAtPoint(e.getPoint()), strategy);
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

		/** Clear the strategy-strip hover state, e.g. when rows are replaced. */
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
		private @Nullable UpgradeStrategy strategyAt(Point point) {

			int row = rowAtPoint(point);
			int column = columnAtPoint(point);
			if (row < 0 || convertColumnIndexToModel(column) != UPGRADE_TARGETS_COLUMN_INDEX) {
				return null;
			}

			if (!(getCellRenderer(row, column) instanceof UpgradeTargetsRenderer renderer)) {
				return null;
			}

			Rectangle cellRect = getCellRect(row, column, false);
			return renderer.getStrategyAt(this, row, column,
					new Point(point.x - cellRect.x, point.y - cellRect.y), cellRect.getSize());
		}

	}

	static class DependencyCoordinateColumn extends ColumnInfo<TableRow, ArtifactId> {

		static final TextAttributesKey WEAK_WARNING_KEY = HighlightInfoType.WEAK_WARNING.getAttributesKey();

		static final TextAttributes WEAK_WARNING_ATTRIBUTES = EditorSchemes.attributes(WEAK_WARNING_KEY,
				new TextAttributes());

		/**
		 * Weak-warning wave underline for rows coupled through a Shared Version
		 * Property: informative, never blocking.
		 */
		static final SimpleTextAttributes SHARED_PROPERTY_ATTRIBUTES = new SimpleTextAttributes(
				SimpleTextAttributes.STYLE_PLAIN | SimpleTextAttributes.STYLE_WAVED, null,
				WEAK_WARNING_ATTRIBUTES.getEffectColor());

		private final UpgradeReview review;

		private final ColoredTableCellRenderer renderer = new ColoredTableCellRenderer() {

			@Override
			protected void customizeCellRenderer(JTable table, Object value, boolean selected, boolean hasFocus,
					int row, int column) {

				TableRow candidate = ModelUtil.getRow(table, row);
				List<TableRow> peers = review.getSharedPropertyPeers(candidate);
				boolean waved = !peers.isEmpty() || candidate.getDeclaredVersions().hasDeclarationDrift();
				append(candidate.getName(),
						waved ? SHARED_PROPERTY_ATTRIBUTES : SimpleTextAttributes.REGULAR_ATTRIBUTES);

				if (candidate instanceof GroupRow group) {
					append("  (%s)".formatted(group.getMemberLabel()), SimpleTextAttributes.GRAYED_ATTRIBUTES);
				} else if (review.isAmbiguous(candidate)) {
					append("  (%s)".formatted(candidate.getArtifactId().groupId()),
							SimpleTextAttributes.GRAYED_ATTRIBUTES);
				}

				setIcon(peers.isEmpty() ? candidate.getTableIcon() : DependencyAssistantIcons.SHARED_PROPERTY);

				String tooltip = toolTip(candidate, peers);
				setToolTipText(tooltip);
			}

		};

		DependencyCoordinateColumn(UpgradeReview review) {
			super(MessageBundle.message("dialog.column.dependency"));
			this.review = review;
		}

		private static String toolTip(TableRow candidate, List<TableRow> peers) {
			StringBuilder tooltip = new StringBuilder();
			String toolTipText = candidate.getToolTipText();
			tooltip.append(toolTipText);

			if (!peers.isEmpty()) {
				tooltip.append("<p>");
				tooltip.append(sharedPropertyToolTip(candidate, peers));
				tooltip.append("</p>");
			}

			DeclaredVersions declaredVersions = candidate.getDeclaredVersions();
			if (declaredVersions.hasDeclarationDrift()) {
				tooltip.append("<p>");
				tooltip.append(declaredVersions.getDeclarationDriftToolTipText());
				tooltip.append("</p>");
			}

			return "<html>" + tooltip + "</html>";
		}

		private static String sharedPropertyToolTip(TableRow candidate, List<TableRow> peers) {

			Set<String> names = candidate.getVersionPropertyNames();
			Set<String> shared = new LinkedHashSet<>();
			for (TableRow peer : peers) {
				Set<String> peerShared = new LinkedHashSet<>(peer.getVersionPropertyNames());
				peerShared.retainAll(names);
				shared.addAll(peerShared);
			}

			StringBuilder tooltip = new StringBuilder();
			tooltip.append("<b>").append(MessageBundle.message("dialog.tooltip.sharedProperty",
					"<code>" + String.join(", ", shared) + "</code>")).append("</b>");

			tooltip.append("<ul>");
			for (TableRow peer : peers) {
				tooltip.append("<li><code>").append(peer.getName()).append("</code></li>");
			}

			return tooltip.append("</ul>").toString();
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
					setToolTipText(statusTooltip(candidate, rule));
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

		private static @Nullable String statusTooltip(
				TableRow candidate,
				DependencyRuleEvaluator rule) {
			StringBuilder tooltip = new StringBuilder();
			DeclaredVersions declaredVersions = candidate.getDeclaredVersions();

			if (declaredVersions.hasVersionDrift()) {
				tooltip.append(declaredVersions.getVersionDriftToolTipText(candidate.getCurrentVersion()));
			}
			if (rule.isPresent()) {
				tooltip.append(rule.getToolTipText());
			}

			if (tooltip.isEmpty()) {
				return null;
			}

			return "<html>%s</html>".formatted(tooltip);
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

	private static JPanel createStrategyStrip(Collection<? extends JComponent> components) {

		JPanel panel = new JPanel(new GridBagLayout());
		panel.setOpaque(true);
		panel.setBorder(JBUI.Borders.empty());

		GridBagConstraints constraints = new GridBagConstraints();
		constraints.gridy = 0;
		constraints.anchor = GridBagConstraints.CENTER;
		constraints.insets = JBUI.insetsRight(JBUI.scale(2));

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
				label.setVisible(review.resolveTarget(candidate, strategy) != null);
				label.setHovered(strategy == hovered);
			});
			panel.setBackground(RenderingUtil.getBackground(table, isSelected));
			panel.setEnabled(table.isEnabled());
			return panel;
		}

		/**
		 * Return the strategy whose icon is at {@code cellPoint} within a cell of
		 * {@code cellSize}, or {@literal null} if the point hits no visible icon.
		 */
		@Nullable
		UpgradeStrategy getStrategyAt(JTable table, int row, int column, Point cellPoint,
				Dimension cellSize) {

			getTableCellRendererComponent(table, null, false, false, row, column);
			panel.setSize(cellSize);
			panel.doLayout();

			for (Map.Entry<UpgradeStrategy, StrategyIconLabel> entry : icons.entrySet()) {

				StrategyIconLabel label = entry.getValue();
				if (label.isVisible() && label.getBounds().contains(cellPoint)) {
					return entry.getKey();
				}
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
		public void setValue(TableRow item, ArtifactVersion value) {
			if (value != null && !value.matches(review.getUpdateTo(item))) {
				review.selectTarget(item, value);
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
	 * Shared editor for the suggested-version column: a combo box of the visible
	 * release options, re-targeted to the edited row when editing starts. Picking
	 * an option stops editing; {@link UpdateToColumn#setValue} pushes the pick into
	 * the review.
	 */
	static class SuggestedVersionComboBoxEditor extends AbstractCellEditor implements TableCellEditor {

		private final UpgradeReview review;

		private final ComboBox<Release> combo = new ComboBox<>();

		private final VersionOptionCellRenderer optionRenderer;

		/** Suppresses selection events while the combo is re-targeted to a row. */
		private boolean refreshing;

		SuggestedVersionComboBoxEditor(UpgradeReview review) {

			this.review = review;
			this.optionRenderer = new VersionOptionCellRenderer();
			combo.registerTableCellEditor(this);
			combo.setRenderer(optionRenderer);
			combo.setBorder(JBUI.Borders.empty(0, SUGGESTED_VERSION_CELL_PADDING));
			combo.setMinimumAndPreferredWidth(JBUI.scale(SUGGESTED_VERSION_WIDTH));
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
				combo.setModel(new CollectionComboBoxModel<>(review.getReleases(candidate).toList(),
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
			this.optionRenderer = new VersionOptionCellRenderer();
			combo.setModel(model);
			combo.setRenderer(optionRenderer);
			combo.setBorder(JBUI.Borders.empty(0, SUGGESTED_VERSION_CELL_PADDING));
			combo.setMinimumAndPreferredWidth(JBUI.scale(SUGGESTED_VERSION_WIDTH));
		}

		@Override
		public Component getTableCellRendererComponent(JTable table, @Nullable Object value, boolean isSelected,
				boolean hasFocus, int row, int column) {

			TableRow candidate = ModelUtil.getRow(table, row);
			optionRenderer.setCandidate(candidate);
			combo.setFont(table.getFont());

			Release selected = review.getSelectedRelease(candidate);
			model.replaceAll(selected == null ? List.of() : List.of(selected));
			model.setSelectedItem(selected);
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

			// dropdown rows only (index >= 0): the collapsed combo and the table
			// stamp show the bare version. The right-align padding must be a fixed
			// offset: a padding derived from the live list width feeds back into
			// the renderer's preferred width and grows the popup on every opening.
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
