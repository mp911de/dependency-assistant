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

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.ItemEvent;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.ListCellRenderer;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.assistant.check.DependencyCheckResult;
import biz.paluch.dap.assistant.check.DependencySiteNavigator;
import biz.paluch.dap.plan.PlannedUpgrade;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.CommonBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.icons.AllIcons;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.EditorNotificationPanel;
import com.intellij.ui.InlineBanner;
import com.intellij.ui.SimpleListCellRenderer;
import com.intellij.ui.TableUtil;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.ListTableModel;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Modeless dialog for reviewing a {@link DependencyCheckResult} and choosing
 * dependency upgrades.
 *
 * <p>The dialog keeps its mutable selection state in {@link UpgradeReview}.
 * Confirming applies the armed rows to the checked file scope; transferring
 * sends them to the Upgrade Plan without changing build files.
 *
 * @author Mark Paluch
 */
public class DependencyCheckDialog extends DialogWrapper {

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
		int selected = 0;
		for (TableRow row : visible) {
			if (review.isApplyUpdate(row)) {
				selected++;
			}
		}

		openInPlanAction.setEnabled(selected > 0);
		updateOkButtonText(visible.size(), selected);
	}

	/**
	 * Reflect the selected-row count in the OK button; collapses to "All", or "All
	 * Shown" when a filter hides rows.
	 */
	private void updateOkButtonText(int visible, int selected) {

		if (selected == 0) {
			setOKButtonText(CommonBundle.getOkButtonText());
			return;
		}

		if (selected == visible) {
			setOKButtonText(MessageBundle.message(visible == review.getAllCandidates().size()
					? "dialog.ok.update.all"
					: "dialog.ok.update.all-shown"));
			return;
		}

		setOKButtonText(MessageBundle.message("dialog.ok.update", selected));
	}

	/**
	 * OK and Cancel stay on the right.
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

		Map<PlannedUpgrade, ArtifactVersion> upgrades = review.getSelectedUpgrades();
		if (upgrades.isEmpty()) {
			return;
		}

		actions.openInUpgradePlan(upgrades, scope);
		restartHighlighting();
		close(OK_EXIT_CODE);
	}

	/**
	 * Select and reveal the table row for the given artifact, e.g. when the dialog
	 * was opened from a gutter icon or a documentation link. When the visibility
	 * filter hides the row, the filter is cleared first so the row can show.
	 *
	 * @param pkg the artifact whose row to select; group rows match through their
	 * members.
	 */
	public void selectCandidate(PackageIdentity pkg) {

		TableRow candidate = findCandidate(pkg);
		if (candidate == null) {
			return;
		}

		if (!review.getCandidates().contains(candidate)) {
			this.components.clearVersionFilter();
		}

		this.components.select(candidate);
	}

	private @Nullable TableRow findCandidate(PackageIdentity pkg) {

		for (TableRow candidate : review.getAllCandidates()) {
			if (candidate.represents(pkg)) {
				return candidate;
			}
		}
		return null;
	}

	private void navigateToSites(TableRow candidate, RelativePoint where) {
		new DependencySiteNavigator(project, getDisposable(), this::doCancelAction, scope.toList())
				.browse(candidate.toQuery(), where);
	}

	private void showContextMenu(TableRow candidate, RelativePoint where) {

		// PSI-backed availability is computed once per popup so update() stays a
		// plain field read off the highlighting-critical action-update path

		DefaultActionGroup group = new DefaultActionGroup();
		group.add(new AnAction(MessageBundle.message("dialog.action.addToDependencyfile"),
				MessageBundle.message("dialog.action.addToDependencyfile.description"), AllIcons.FileTypes.Json) {

			@Override
			public ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.BGT;
			}

			@Override
			public void actionPerformed(AnActionEvent e) {
				doCancelAction();
				new DependencyfileArtifactWriter(project).add(candidate);
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

		String title = candidate.getName().equals(candidate.getDisplayName()) ? candidate.getName()
				: "%s (%s)".formatted(candidate.getDisplayName(), candidate.getName());

		JBPopupFactory.getInstance()
				.createActionGroupPopup(title, group, dataContext,
						JBPopupFactory.ActionSelectionAid.MNEMONICS, true)
				.show(where);
	}

	/**
	 * Components panel for the dependency check dialog, containing the table and
	 * related controls.
	 */
	static class DependencyCheckComponents extends JPanel {

		private final UpgradeReview review;

		private final ListTableModel<TableRow> tableModel;

		private final DependencyUpdateTable table;

		private final ActionToolbar toolbar;

		private final ComboBox<UpgradeReview.StrategySelection> strategyComboBox;

		private final JCheckBox filterVersionsCheckBox;

		DependencyCheckComponents(UpgradeReview review, Disposable parent,
				BiConsumer<TableRow, RelativePoint> onNavigate,
				BiConsumer<TableRow, RelativePoint> onContextMenu) {
			super(new BorderLayout());
			this.review = review;

			this.table = new DependencyUpdateTable(review, onNavigate, onContextMenu);
			this.tableModel = table.getListTableModel();
			this.strategyComboBox = new ComboBox<>(strategyOptions(review));
			this.filterVersionsCheckBox = new JCheckBox(MessageBundle.message("dialog.filter.version.suggestions"),
					this.review.isHideUpToDate());

			ActionGroup toolbarGroup = createToolbarGroup();
			this.toolbar = ActionManager.getInstance().createActionToolbar("DependencyVersions", toolbarGroup,
					true);

			initialize(parent);
		}

		private void initialize(Disposable parent) {

			tableModel.setItems(this.review.getCandidates());

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

			toolbar.setTargetComponent(table);

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

			ListCellRenderer<UpgradeReview.StrategySelection> renderer = new SimpleListCellRenderer<>() {

				@Override
				public void customize(JList<? extends UpgradeReview.StrategySelection> list,
						UpgradeReview.StrategySelection value, int index, boolean selected, boolean hasFocus) {
					setText(MessageBundle.message(value.getMessageKey()));
					setIcon(value.getIcon());
					setIconTextGap(JBUI.scale(4));
				}

			};

			strategyComboBox.setRenderer(renderer);
			strategyComboBox.addItemListener(e -> {
				if (e.getStateChange() != ItemEvent.SELECTED) {
					return;
				}
				review.applyStrategyToAll((UpgradeReview.StrategySelection) e.getItem());
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
			return JBUI.Panels.simplePanel(warningBanner).withBorder(JBUI.Borders.emptyTop(8));
		}

		private DefaultActionGroup createToolbarGroup() {

			AnAction selectAllAction = new AnAction(MessageBundle.message("dialog.action.selectAll"),
					MessageBundle.message("dialog.action.selectAll.description"), AllIcons.Actions.Selectall) {

				@Override
				public ActionUpdateThread getActionUpdateThread() {
					return ActionUpdateThread.BGT;
				}

				@Override
				public void actionPerformed(AnActionEvent e) {
					review.selectAll(true);
				}

			};

			AnAction deselectAllAction = new AnAction(MessageBundle.message("dialog.action.unselectAll"),
					MessageBundle.message("dialog.action.unselectAll.description"), AllIcons.Actions.Unselectall) {

				@Override
				public ActionUpdateThread getActionUpdateThread() {
					return ActionUpdateThread.BGT;
				}

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
		static UpgradeReview.StrategySelection[] strategyOptions(UpgradeReview review) {

			List<UpgradeReview.StrategySelection> options = new ArrayList<>();
			for (UpgradeReview.StrategySelection strategy : UpgradeReview.StrategySelection.values()) {
				if (strategy == UpgradeReview.StrategySelection.SAFE && !review.isSafeStrategyAvailable()) {
					continue;
				}
				options.add(strategy);
			}
			return options.toArray(UpgradeReview.StrategySelection[]::new);
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
			restartHighlighting();
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
			indicator.setIndeterminate(false);
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
			psiManager.stream(scope.toList()).forEach(analyzer::restart);
		});
	}

}
