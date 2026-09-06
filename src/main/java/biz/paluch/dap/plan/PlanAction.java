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

package biz.paluch.dap.plan;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.state.ApplicationSettings;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.util.MessageBundle;
import org.jspecify.annotations.Nullable;

/**
 * Reversible persisted-plan transition managed by {@link UpgradePlanService}.
 * <p>Implementations own state changes and leave commands, undo registration
 * and UI notification to the service. Each instance represents one transition.
 * <p>Apply and undo must be exact inverses. Redo calls apply again. Undo
 * follows a successful apply. The service invokes transitions on the EDT.
 *
 * @author Mark Paluch
 */
interface PlanAction {

	/**
	 * Return the localized name shown in Undo and Redo menus.
	 */
	String getCommandName();

	/**
	 * Choose whether the service rebuilds the plan or retains materialized items.
	 */
	Materialization materialization();

	/**
	 * Apply the forward transition, including redo.
	 */
	void apply();

	/**
	 * Reverse the preceding apply.
	 */
	void undo();

	/**
	 * Replace the plan with the reviewed upgrades and their captured file scope.
	 */
	static PlanAction planUpgrades(Map<? extends PlannedUpgrade, ArtifactVersion> upgrades, FileScope scope,
			UpgradePlanState.Plan plan) {
		return new PlanUpgrades(upgrades, scope, plan);
	}

	/**
	 * Merge a copied fragment by item identity and union its file scope. Pasted
	 * items replace matching items and are appended in fragment order.
	 */
	static PlanAction pasteItems(Content pasteContent, Content content) {
		return new PasteItems(pasteContent, content);
	}

	static PlanAction removeItems(Content content, UpgradePlanItem item) {
		return new RemoveItems(content, item);
	}

	static PlanAction removeItems(Content content, Collection<UpgradePlanItem> items) {
		return new RemoveItems(content, items);
	}

	/**
	 * Link a ticket, or clear the association when {@code ticket} is
	 * {@literal null}.
	 */
	static PlanAction linkTicket(Content content, UpgradePlanItem item, @Nullable UpgradeTicket ticket) {
		return new LinkTicket(content, item, ticket);
	}

	static PlanAction unlinkTicket(Content content, UpgradePlanItem item) {
		return new UnlinkTicket(content, item);
	}

	/**
	 * Rename an item, optionally updating its application-wide name hint.
	 * @param displayName an already sanitized name.
	 */
	static PlanAction renameItem(Content content, UpgradePlanItem item, String displayName, boolean rememberName) {
		return new RenameItem(content, item, displayName, rememberName);
	}

	/**
	 * Combine a non-empty list of transitions, applying in order and undoing in
	 * reverse. Actions must share the first action's command name and
	 * materialization.
	 */
	static <T extends PlanAction> PlanAction composite(List<T> actions) {
		return new CompositeAction<>(actions);
	}

	static PlanAction discardUpgrades(UpgradePlanState.Plan plan) {
		return new DiscardUpgrades(plan);
	}

	enum Materialization {

		/**
		 * Rebuild the live plan from persisted state.
		 */
		REBUILD,

		/**
		 * Retain materialized items and refresh their views.
		 */
		RETAIN

	}

	class CompositeAction<T extends PlanAction> implements PlanAction {

		private final List<T> actions;

		private CompositeAction(List<T> actions) {
			this.actions = actions;
		}

		@Override
		public String getCommandName() {
			return actions.getFirst().getCommandName();
		}

		@Override
		public Materialization materialization() {
			return actions.getFirst().materialization();
		}

		@Override
		public void apply() {
			for (T action : actions) {
				action.apply();
			}
		}

		@Override
		public void undo() {
			for (T action : actions.reversed()) {
				action.undo();
			}
		}

	}

	class LinkTicket implements PlanAction {

		private final Content content;

		private final UpgradePlanItem item;

		private final @Nullable UpgradeTicket oldTicket;

		private final @Nullable UpgradeTicket newTicket;

		private LinkTicket(Content content, UpgradePlanItem item, @Nullable UpgradeTicket newTicket) {
			this.content = content;
			this.item = item;
			this.oldTicket = item.getTicket();
			this.newTicket = newTicket;
		}

		@Override
		public String getCommandName() {
			return MessageBundle.message("plan.link-ticket.command");
		}

		@Override
		public Materialization materialization() {
			return Materialization.RETAIN;
		}

		@Override
		public void apply() {
			update(newTicket);
		}

		@Override
		public void undo() {
			update(oldTicket);
		}

		private void update(@Nullable UpgradeTicket ticket) {

			for (Item item : content) {
				if (this.item.getId().equals(item.getId())) {
					item.setTicket(UpgradePlanState.Ticket.from(ticket));
					UpgradePlanItem materialized = item.getMaterialized();
					if (materialized != null) {
						materialized.setTicket(ticket);
					}
				}
			}

			this.item.setTicket(ticket);
		}

	}

	class UnlinkTicket extends LinkTicket {

		private UnlinkTicket(Content content, UpgradePlanItem item) {
			super(content, item, null);
		}

		@Override
		public String getCommandName() {
			return MessageBundle.message("plan.unlink-ticket.command");
		}

	}

	class RenameItem implements PlanAction {

		private final ApplicationSettings settings;

		private final Content content;

		private final UpgradePlanItem item;

		private final String oldName;

		private final String newName;

		private final boolean rememberName;

		private @Nullable String oldRememberedName;

		private boolean wasRemembered;

		private final List<PackageIdentity> packages;

		private RenameItem(Content content, UpgradePlanItem item, String newName, boolean rememberName) {
			this.settings = ApplicationSettings.getInstance();
			this.content = content;
			this.item = item;
			this.oldName = item.getDisplayName();
			this.newName = newName;
			this.rememberName = rememberName;
			this.packages = new ArrayList<>(item.getMembers().size());
			for (ItemDependency member : item) {
				this.packages.add(member.getPackageIdentity());
			}
		}

		@Override
		public String getCommandName() {
			return MessageBundle.message("plan.rename.command");
		}

		@Override
		public Materialization materialization() {
			return Materialization.RETAIN;
		}

		@Override
		public void apply() {
			update(newName);
			if (rememberName) {
				oldRememberedName = settings.findNameHint(packages);
				if (oldRememberedName != null) {
					settings.removeNameHint(oldRememberedName, packages);
				}
				wasRemembered = settings.addNameHint(newName, packages);
			}
		}

		@Override
		public void undo() {
			update(oldName);
			if (rememberName && wasRemembered) {
				settings.removeNameHint(newName, packages);
				if (oldRememberedName != null) {
					settings.addNameHint(oldRememberedName, packages);
				}
			}
		}

		private void update(String displayName) {

			for (Item item : content) {
				if (this.item.getId().equals(item.getId())) {
					item.setDisplayName(displayName);
					UpgradePlanItem materialized = item.getMaterialized();
					if (materialized != null) {
						materialized.setDisplayName(displayName);
					}
				}
			}

			this.item.setDisplayName(displayName);
		}

	}

	class RemoveItems implements PlanAction {

		private final Content content;

		private final Collection<Item> oldItems;

		private final Collection<ItemId> toRemove;

		private RemoveItems(Content content, UpgradePlanItem toRemove) {
			this(content, List.of(toRemove));
		}

		private RemoveItems(Content content, Collection<UpgradePlanItem> toRemove) {
			this.content = content;
			this.toRemove = toRemove.stream().map(UpgradePlanItem::getId).toList();
			this.oldItems = new ArrayList<>();
		}

		@Override
		public String getCommandName() {
			return MessageBundle.message("plan.remove.command");
		}

		@Override
		public Materialization materialization() {
			return Materialization.RETAIN;
		}

		@Override
		public void apply() {

			oldItems.clear();
			List<Item> items = content.getItems();
			oldItems.addAll(items);
			items.removeIf(item -> toRemove.contains(item.getId()));
		}

		@Override
		public void undo() {

			List<Item> items = content.getItems();
			items.clear();
			items.addAll(oldItems);
		}

	}

	class PlanUpgrades implements PlanAction {

		private final UpgradePlanState.Plan plan;

		private final Content oldContent;

		private final Content newContent;

		private PlanUpgrades(Map<? extends PlannedUpgrade, ArtifactVersion> upgrades,
				FileScope scope, UpgradePlanState.Plan plan) {
			this.plan = plan;

			Content content = new Content();
			ApplicationSettings settings = ApplicationSettings.getInstance();
			content.setItems(ImplicitGroups.create(upgrades, settings).toList());
			content.getAffectedFiles().addAll(scope.getPaths());

			this.oldContent = plan.getContent();
			this.newContent = content;
		}

		@Override
		public String getCommandName() {
			return MessageBundle.message("plan.capture.command");
		}

		@Override
		public Materialization materialization() {
			return Materialization.REBUILD;
		}

		@Override
		public void apply() {
			plan.setContent(newContent);
		}

		@Override
		public void undo() {
			plan.setContent(oldContent);
		}

	}

	class DiscardUpgrades implements PlanAction {

		private final UpgradePlanState.Plan plan;

		private final Content oldContent;

		private final Content newContent = new Content();

		private DiscardUpgrades(UpgradePlanState.Plan plan) {
			this.plan = plan;
			this.oldContent = plan.getContent();
		}

		@Override
		public String getCommandName() {
			return MessageBundle.message("plan.discard.command");
		}

		@Override
		public Materialization materialization() {
			return Materialization.REBUILD;
		}

		@Override
		public void apply() {
			plan.setContent(newContent);
		}

		@Override
		public void undo() {
			plan.setContent(oldContent);
		}

	}

	class PasteItems implements PlanAction {

		private final Content pasteContent;

		private final Content content;

		private final List<String> oldFiles;

		private final List<Item> oldItems;

		private final List<String> newFiles = new ArrayList<>();

		private final List<Item> newItems = new ArrayList<>();

		private PasteItems(Content pasteContent, Content content) {

			this.pasteContent = pasteContent.snapshot();
			this.content = content;
			this.oldFiles = new ArrayList<>(content.getAffectedFiles());
			this.oldItems = new ArrayList<>(content.getItems());
		}

		@Override
		public String getCommandName() {
			return MessageBundle.message("plan.paste.command");
		}

		@Override
		public Materialization materialization() {
			return Materialization.REBUILD;
		}

		@Override
		public void apply() {

			newFiles.clear();
			newItems.clear();

			Set<String> paths = new LinkedHashSet<>(content.getAffectedFiles());
			paths.addAll(pasteContent.getAffectedFiles());
			newFiles.addAll(paths);
			content.setAffectedFiles(new ArrayList<>(newFiles));

			Set<Item> items = new LinkedHashSet<>(content.getItems());
			items.removeAll(pasteContent.getItems());
			items.addAll(pasteContent.getItems());
			newItems.addAll(items);

			content.setItems(new ArrayList<>(newItems));
		}

		@Override
		public void undo() {

			List<String> affectedFiles = content.getAffectedFiles();
			affectedFiles.clear();
			affectedFiles.addAll(oldFiles);

			List<Item> items = content.getItems();
			items.clear();
			items.addAll(oldItems);
		}

	}

}
