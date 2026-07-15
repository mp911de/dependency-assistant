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

package biz.paluch.dap.plan;

import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.datatransfer.Transferable;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;

import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JTree;

import biz.paluch.dap.util.MessageBundle;
import com.intellij.ide.CopyProvider;
import com.intellij.ide.DataManager;
import com.intellij.ide.DefaultTreeExpander;
import com.intellij.ide.DeleteProvider;
import com.intellij.ide.PasteProvider;
import com.intellij.ide.TreeExpander;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.ActionUtil;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationActivationListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.DumbAwareToggleAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.SimpleToolWindowPanel;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.openapi.wm.IdeFrame;
import com.intellij.pom.Navigatable;
import com.intellij.ui.awt.RelativePoint;
import com.intellij.ui.components.ActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.NamedColorUtil;
import org.jspecify.annotations.Nullable;

/**
 * Upgrade Plan tool window content: left all-mode action toolbar, top
 * milestone/label selector bar, the plan tree, and a bottom summary line.
 *
 * @author Mark Paluch
 */
class UpgradePlanPanel extends SimpleToolWindowPanel implements Disposable, UpgradePlanListener {

	private static final String EMPTY_STATE_PLACE = "DependencyAssistant.UpgradePlanEmptyState";

	private static final String SUMMARY_LINK_PLACE = "DependencyAssistant.UpgradePlanSummaryLink";

	private static final String SORT_BY_ATTENTION_PROPERTY = "DependencyAssistant.UpgradePlan.SortByAttention";

	private static final String SORT_ALPHABETICALLY_PROPERTY = "DependencyAssistant.UpgradePlan.SortAlphabetically";

	private final Application application = ApplicationManager.getApplication();

	private final UpgradePlanService service;

	private final Clipboard clipboard;

	private final PropertiesComponent viewProperties;

	private final UpgradePlanTree tree;

	private final TreeExpander treeExpander;

	private final DeleteProvider deleteProvider;

	private final CopyProvider copyProvider = new CopyProvider() {

		@Override
		public void performCopy(DataContext dataContext) {
			Transferable copy = clipboard.copy(service.getUpgradePlan()
					.withItems(tree.getSelection()));
			CopyPasteManager.getInstance()
					.setContents(copy);
		}

		@Override
		public boolean isCopyEnabled(DataContext dataContext) {
			return !tree.getSelection().isEmpty();
		}

		@Override
		public boolean isCopyVisible(DataContext dataContext) {
			return true;
		}

		@Override
		public ActionUpdateThread getActionUpdateThread() {
			return ActionUpdateThread.EDT;
		}

	};

	private final PasteProvider pasteProvider = new PasteProvider() {

		@Override
		public void performPaste(DataContext dataContext) {
			pasteFromClipboard();
		}

		@Override
		public boolean isPastePossible(DataContext dataContext) {
			return clipboard.isPlanInClipboard();
		}

		@Override
		public boolean isPasteEnabled(DataContext dataContext) {
			return !service.isBusy() && isPastePossible(dataContext);
		}

		@Override
		public ActionUpdateThread getActionUpdateThread() {
			return ActionUpdateThread.EDT;
		}

	};

	private final SummaryLine summaryLine;

	private final UpgradePlanTabActions tabActions;

	private @Nullable UpgradePlanItem selectionAfterReload;

	private boolean focusTreeAfterReload;

	UpgradePlanPanel(UpgradePlanService service) {

		super(false, true);
		this.service = service;
		this.clipboard = new Clipboard(service);
		this.viewProperties = PropertiesComponent.getInstance(service.getProject());
		this.tabActions = new UpgradePlanTabActions(service, this);

		ActionToolbar toolbar = createLeftToolbar();
		setToolbar(toolbar.getComponent());

		this.tree = new UpgradePlanTree(this::runDependencyCheck, this::pasteFromClipboard,
				it -> PreviewChangesAction.preview(service, service.getUpgradePlan().withItems(List.of(it))),
				service,
				viewProperties.getBoolean(SORT_BY_ATTENTION_PROPERTY, true),
				viewProperties.getBoolean(SORT_ALPHABETICALLY_PROPERTY, true));

		// the empty state offers pasting a copied plan; track IDE-internal
		// clipboard changes, and re-check on application activation because
		// copies made in other applications fire no content event
		updatePasteAvailable();
		CopyPasteManager.getInstance().addContentChangedListener(
				(oldTransferable, newTransferable) -> updatePasteAvailable(), this);
		ApplicationManager.getApplication().getMessageBus().connect(this)
				.subscribe(ApplicationActivationListener.TOPIC, new ApplicationActivationListener() {

					@Override
					public void applicationActivated(IdeFrame ideFrame) {

						ApplicationManager.getApplication()
								.invokeLater(UpgradePlanPanel.this::updatePasteAvailable);
					}

				});
		this.treeExpander = new DefaultTreeExpander((JTree) tree.focusTarget());

		this.deleteProvider = new DeleteProvider() {

			@Override
			public void deleteElement(DataContext dataContext) {
				removeSelectedItems();
			}

			@Override
			public boolean canDeleteElement(DataContext dataContext) {
				return !service.isBusy() && !tree.getSelection().isEmpty();
			}

			@Override
			public ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.EDT;
			}

		};

		this.summaryLine = new SummaryLine();

		JComponent treeComponent = tree.component();
		((JTree) tree.focusTarget()).addTreeSelectionListener(event -> {
			toolbar.updateActionsAsync();
			summaryLine.update(service.getUpgradePlan());
		});
		DumbAwareAction upgradeDependencies = DumbAwareAction.create(event -> runDependencyCheck());
		upgradeDependencies.registerCustomShortcutSet(CommonShortcuts.getNew(), treeComponent, this);

		JPanel content = new JPanel(new BorderLayout());
		content.add(treeComponent, BorderLayout.CENTER);
		content.add(summaryLine, BorderLayout.SOUTH);
		setContent(content);

		getProject().getMessageBus().connect(this)
				.subscribe(UpgradePlanListener.TOPIC, this);
	}

	private ActionToolbar createLeftToolbar() {

		ActionGroup group = (ActionGroup) ActionManager.getInstance()
				.getAction("DependencyAssistant.UpgradePlan.Toolbar");

		ActionToolbar toolbar = ActionManager.getInstance()
				.createActionToolbar("DependencyAssistant.UpgradePlan", group, false);
		toolbar.setTargetComponent(this);
		return toolbar;
	}

	private Project getProject() {
		return service.getProject();
	}

	@Override
	public void uiDataSnapshot(DataSink sink) {

		super.uiDataSnapshot(sink);
		sink.set(CommonDataKeys.PROJECT, getProject());
		sink.set(PlatformDataKeys.TREE_EXPANDER, treeExpander);
		sink.set(PlatformDataKeys.DELETE_ELEMENT_PROVIDER, deleteProvider);
		sink.set(PlatformDataKeys.COPY_PROVIDER, copyProvider);
		sink.set(PlatformDataKeys.PASTE_PROVIDER, pasteProvider);
		PlanSelection selection = tree.getSelection();
		if (!selection.isEmpty()) {
			sink.set(PlatformDataKeys.SELECTED_ITEMS, selection.items().toArray(new Object[0]));
			sink.set(PlatformDataKeys.SELECTED_ITEM, selection.first());
		}

		// jump-to-source target (EditSource/F4): a single selected item while the
		// plan is idle; withheld otherwise so the platform action disables itself
		if (selection.items().size() == 1 && !service.isBusy()) {
			Navigatable navigatable = new PlanItemNavigatable(selection.items().getFirst());
			sink.set(CommonDataKeys.NAVIGATABLE, navigatable);
			sink.set(CommonDataKeys.NAVIGATABLE_ARRAY, new Navigatable[] {navigatable});
		}
	}

	@Override
	public void dispose() {
	}

	/**
	 * Restore (or create) the initial UI state.
	 */
	public void restore() {
		reload(service::reloadPlan);
		tabActions.restore();
	}

	@Override
	public void planChanged() {
		reload(service::reloadPlan);
	}

	private void reload(Callable<UpgradePlan> callable) {
		ReadAction.nonBlocking(callable)
				.inSmartMode(service.getProject())
				.expireWith(this)
				.finishOnUiThread(ModalityState.nonModal(), this::update)
				.submit(AppExecutorUtil.getAppExecutorService());
	}

	private void update(UpgradePlan plan) {

		tree.setItems(plan.getItems());

		if (selectionAfterReload != null || focusTreeAfterReload) {
			tree.selectItem(selectionAfterReload);
			if (focusTreeAfterReload) {
				tree.requestFocusInWindow();
			}
			selectionAfterReload = null;
			focusTreeAfterReload = false;
		}

		tree.component().revalidate();
		tree.component().repaint();
		summaryLine.update(plan);

		revalidate();
		repaint();
	}

	@Override
	public void milestonesChanged() {
		tabActions.milestonesChanged();
	}

	@Override
	public void ticketSystemChanged() {
		application.invokeLater(() -> {
			tabActions.ticketSystemChanged();
			reload(service::reloadPlan);
		});
	}

	@Override
	public void planItemChanged() {

		UpgradePlan plan = service.getUpgradePlan();
		application.invokeLater(() -> {
			tree.refreshItems(plan.getItems());
			summaryLine.setVisible(!plan.isEmpty());
			summaryLine.update(plan);
		});
	}

	private void runDependencyCheck() {
		performAction("biz.paluch.dap.UpgradeDependencies", EMPTY_STATE_PLACE);
	}

	private void performAction(String actionId, String place) {

		AnAction action = ActionManager.getInstance().getAction(actionId);
		if (action == null) {
			return;
		}

		DataContext context = DataManager.getInstance().getDataContext(this);
		ActionUtil.performAction(action, AnActionEvent.createEvent(context, null, place, ActionUiKind.NONE, null));
	}

	private void updatePasteAvailable() {
		tree.setPasteAvailable(clipboard.isPlanInClipboard());
	}

	private void pasteFromClipboard() {

		UpgradePlanState.Content fragment = clipboard.paste();
		if (fragment != null) {
			service.pasteItems(fragment);
		}
	}

	private void removeSelectedItems() {

		Set<UpgradePlanItem> selection = tree.getSelectionSet();
		if (selection.isEmpty() || service.isBusy()) {
			return;
		}

		UpgradePlanItem selectionAfterDelete = tree.selectionAfterRemoving(selection);

		selectionAfterReload = selectionAfterDelete;
		focusTreeAfterReload = true;
		tree.selectItem(selectionAfterDelete);
		service.removeItems(selection);
	}

	/**
	 * Return the component that should take focus when the tool window activates,
	 * keeping the platform action context (copy, paste, delete) anchored in the
	 * panel even while the plan is empty.
	 */
	JComponent getPreferredFocusableComponent() {
		return tree.focusTarget();
	}

	/**
	 * Actions for the tool window tab row, next to the title: the milestone and
	 * label selectors (tab-like popup combos with a filterable chooser), their
	 * clear actions, and the list refresh. The actions control their visibility
	 * from the currently bound ticket system; milestone and label options are
	 * loaded from that system in the background.
	 */
	AnAction[] createTabActions() {
		return tabActions.getActions();
	}

	ActionGroup createGearActions() {

		DefaultActionGroup actions = new DefaultActionGroup();
		actions.addSeparator(MessageBundle.message("plan.sort"));
		actions.add(new DumbAwareToggleAction(MessageBundle.message("plan.sort.attention")) {

			@Override
			public boolean isSelected(AnActionEvent event) {
				return tree.isSortByAttention();
			}

			@Override
			public void setSelected(AnActionEvent event, boolean state) {
				tree.setSortByAttention(state);
				viewProperties.setValue(SORT_BY_ATTENTION_PROPERTY, state, true);
			}

			@Override
			public ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.EDT;
			}

		});
		actions.add(new DumbAwareToggleAction(MessageBundle.message("plan.sort.alphabetically")) {

			@Override
			public boolean isSelected(AnActionEvent event) {
				return tree.isSortAlphabetically();
			}

			@Override
			public void setSelected(AnActionEvent event, boolean state) {
				tree.setSortAlphabetically(state);
				viewProperties.setValue(SORT_ALPHABETICALLY_PROPERTY, state, true);
			}

			@Override
			public ActionUpdateThread getActionUpdateThread() {
				return ActionUpdateThread.EDT;
			}

		});
		return actions;
	}

	class SummaryLine extends JPanel {

		private final JBLabel summaryText = new JBLabel();

		private final ActionLink createTicketsLink;

		public SummaryLine() {

			super(new FlowLayout(FlowLayout.LEFT, JBUI.scale(4), 0));

			createTicketsLink = new ActionLink(MessageBundle.message("plan.summary.create-tickets", 0),
					event -> {
						performAction("DependencyAssistant.UpgradePlan.CreateTickets", SUMMARY_LINK_PLACE);
					});

			summaryText.setForeground(NamedColorUtil.getInactiveTextColor());
			summaryText.setFont(JBUI.Fonts.smallFont());
			createTicketsLink.setFont(JBUI.Fonts.smallFont());

			add(summaryText);
			add(createTicketsLink);
		}

		void update(UpgradePlan plan) {

			boolean ticketing = service.hasTicketSystem();
			long missing = plan.stream().filter(item -> !item.hasTicket()).count();

			String text = plan.getSummary();
			summaryText.setText(ticketing && missing > 0 ? text + " ·" : text);
			createTicketsLink.setText(MessageBundle.message("plan.summary.create-tickets", missing));
			createTicketsLink.setVisible(ticketing && missing > 0);
			summaryLine.setVisible(!plan.isEmpty());
		}

	}

	/**
	 * Jump-to-source target for a single selected plan item. Navigation resolves
	 * the item's declaration sites lazily through the shared Dependency Sites
	 * search: a single site opens directly in the editor, several present through
	 * the sites popup anchored at the tree.
	 */
	private class PlanItemNavigatable implements Navigatable {

		private final JBPopupFactory factory = JBPopupFactory.getInstance();

		private final UpgradePlanItem item;

		PlanItemNavigatable(UpgradePlanItem item) {
			this.item = item;
		}

		@Override
		public void navigate(boolean requestFocus) {
			RelativePoint where = factory
					.guessBestPopupLocation(tree.component());
			FindUsagesAction.findUsages(getProject(), where, item);
		}

		@Override
		public boolean canNavigate() {
			return true;
		}

		@Override
		public boolean canNavigateToSource() {
			return true;
		}

	}

}
