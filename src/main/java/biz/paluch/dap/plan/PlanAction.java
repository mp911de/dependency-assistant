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

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.check.UpgradeCandidate;
import biz.paluch.dap.plan.UpgradePlanState.Content;
import biz.paluch.dap.plan.UpgradePlanState.Item;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * A single reversible transition of the persisted Upgrade Plan, recorded as one
 * entry on the platform undo stack.
 *
 * <p>Implementations are the unit of change behind every plan mutation:
 * capture, remove, paste, discard, and ticket link and unlink.
 * {@link UpgradePlanService} drives them and owns the surrounding machinery: it
 * opens the platform command, advances the plan generation, runs
 * {@link #apply()}, registers the undo adapter, and publishes the change event
 * chosen by {@link #materialization()}. An implementation therefore only
 * mutates the persisted {@link UpgradePlanState.Content} or
 * {@link UpgradePlanState.Plan} and reports its command name and
 * materialization; it does not touch the command boundary, the undo stack, or
 * the UI.
 *
 * <p>{@link #apply()} and {@link #undo()} must be exact inverses: undo restores
 * the state that apply changed, and redo re-runs apply. An instance is
 * single-use and captures whatever it needs to reverse itself, either at
 * construction or on apply; {@link #undo()} is only invoked after a preceding
 * {@link #apply()}. All methods run on the EDT inside the service's command and
 * plan-generation advance.
 *
 * @author Mark Paluch
 */
interface PlanAction {

	/**
	 * Return the user-visible name of this transition, shown in the platform Undo
	 * and Redo menu entries.
	 *
	 * @return the localized command name.
	 */
	String getCommandName();

	/**
	 * Return how the service refreshes the live plan after this transition, which
	 * also selects the change event it publishes.
	 *
	 * @return {@link Materialization#REBUILD} when the live plan must be
	 * re-materialized, or {@link Materialization#RETAIN} for an item-scoped change.
	 */
	Materialization materialization();

	/**
	 * Perform the forward transition, mutating the persisted plan state. Also
	 * serves as the redo step, re-run by the platform after an {@link #undo()}.
	 */
	void apply();

	/**
	 * Reverse the most recent {@link #apply()}, restoring the plan state it
	 * changed. Invoked by the platform undo stack only after a preceding
	 * {@link #apply()}.
	 */
	void undo();

	/**
	 * Create the capture transition that replaces the plan with fresh content built
	 * from the armed upgrades and the build files they were captured from.
	 *
	 * @param upgrades the armed candidates mapped to their pinned target versions.
	 * @param files the build files the upgrades were captured from.
	 * @param plan the persisted plan to replace.
	 * @return the reversible capture transition.
	 */
	static PlanAction planUpgrades(Map<UpgradeCandidate, ArtifactVersion> upgrades, List<VirtualFile> files,
			UpgradePlanState.Plan plan) {
		return new PlanUpgrades(upgrades, files, plan);
	}

	/**
	 * Create the transition that merges a copied plan fragment into the current
	 * content, unioning affected files and appending items not already present.
	 *
	 * @param pasteContent the copied plan fragment to merge in.
	 * @param content the current plan content to merge into.
	 * @return the reversible paste transition.
	 */
	static PlanAction pasteItems(Content pasteContent, Content content) {
		return new PasteItems(pasteContent, content);
	}

	/**
	 * Create the transition that removes a single item from the plan.
	 *
	 * @param content the plan content to remove from.
	 * @param item the item to remove.
	 * @return the reversible remove transition.
	 */
	static PlanAction removeItems(Content content, UpgradePlanItem item) {
		return new RemoveItems(content, item);
	}

	/**
	 * Create the transition that removes the given items from the plan.
	 *
	 * @param content the plan content to remove from.
	 * @param items the items to remove.
	 * @return the reversible remove transition.
	 */
	static PlanAction removeItems(Content content, Collection<UpgradePlanItem> items) {
		return new RemoveItems(content, items);
	}

	/**
	 * Create the transition that links a ticket to a plan item, or clears its
	 * association when the ticket is {@literal null}.
	 *
	 * @param content the plan content owning the item.
	 * @param item the item whose ticket association changes.
	 * @param ticket the ticket to link, or {@literal null} to clear the
	 * association.
	 * @return the reversible ticket-link transition.
	 */
	static PlanAction linkTicket(Content content, UpgradePlanItem item, @Nullable UpgradeTicket ticket) {
		return new LinkTicket(content, item, ticket);
	}

	/**
	 * Create the transition that clears the ticket association of a plan item.
	 *
	 * @param content the plan content owning the item.
	 * @param item the item to unlink.
	 * @return the reversible ticket-unlink transition.
	 */
	static PlanAction unlinkTicket(Content content, UpgradePlanItem item) {
		return new UnlinkTicket(content, item);
	}

	/**
	 * Compose several transitions into one undoable unit, applied in order and
	 * undone in reverse. The composed actions should share a command name and
	 * materialization, both taken from the first action.
	 *
	 * @param <T> the composed action type.
	 * @param actions the transitions to apply as one unit, in application order.
	 * @return the reversible composite transition.
	 */
	static <T extends PlanAction> PlanAction composite(List<T> actions) {
		return new CompositeAction<>(actions);
	}

	/**
	 * Create the transition that clears the plan to empty content.
	 *
	 * @param plan the persisted plan to clear.
	 * @return the reversible discard transition.
	 */
	static PlanAction discardUpgrades(UpgradePlanState.Plan plan) {
		return new DiscardUpgrades(plan);
	}

	/**
	 * How the service refreshes the live plan after a transition, selecting the
	 * change event published on apply, undo, and redo.
	 */
	enum Materialization {

		/**
		 * Re-materialize the live plan from persisted state, re-reading releases,
		 * vulnerabilities, and the build-file scope and rebuilding the tree. Used when
		 * the item set or scope gains entries that need fresh materialization: capture,
		 * paste, and discard.
		 */
		REBUILD,

		/**
		 * Reuse the already-materialized items and refresh their views in place,
		 * preserving tree expansion and selection. Used when no item needs
		 * re-materialization: remove and ticket link and unlink.
		 */
		RETAIN

	}

	/**
	 * Applies several transitions as one undoable unit, undoing them in reverse
	 * order. Reports the first action's command name and materialization, so the
	 * composed actions are expected to share both.
	 */
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

	/**
	 * Links a ticket to one plan item, or clears its association when the new
	 * ticket is {@literal null}, capturing the prior ticket at construction for
	 * undo.
	 */
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
			content.apply(it -> {
				for (Item item : it) {
					if (this.item.getId().equals(item.getId())) {
						item.setTicket(UpgradePlanState.Ticket.from(ticket));
						UpgradePlanItem materialized = item.getMaterialized();
						if (materialized != null) {
							materialized.setTicket(ticket);
						}
					}
				}

				this.item.setTicket(ticket);
			});
		}

	}

	/**
	 * Clears the ticket association of one plan item: a {@link LinkTicket} with no
	 * new ticket and its own command name for the Undo and Redo menu.
	 */
	class UnlinkTicket extends LinkTicket {

		private UnlinkTicket(Content content, UpgradePlanItem item) {
			super(content, item, null);
		}

		@Override
		public String getCommandName() {
			return MessageBundle.message("plan.unlink-ticket.command");
		}

	}

	/**
	 * Removes the given items from the plan by their id, capturing the prior item
	 * list on apply so undo restores it in full.
	 */
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
			content.apply(it -> {
				oldItems.clear();
				oldItems.addAll(it.getItems());
				it.getItems().removeIf(item -> toRemove.contains(item.getId()));
			});
		}

		@Override
		public void undo() {
			content.apply(it -> {
				it.getItems().clear();
				it.getItems().addAll(oldItems);
			});
		}

	}

	/**
	 * Captures the armed upgrades and their build-file scope into fresh plan
	 * content, replacing any previous plan and keeping the prior content for undo.
	 */
	class PlanUpgrades implements PlanAction {

		private final UpgradePlanState.Plan plan;

		private final Content oldContent;

		private final Content newContent;

		private PlanUpgrades(Map<UpgradeCandidate, ArtifactVersion> upgrades, List<VirtualFile> files,
				UpgradePlanState.Plan plan) {
			this.plan = plan;

			Content content = new Content();
			List<Item> items = content.getItems();
			upgrades.forEach((upgradeCandidate, version) -> {
				items.add(Item.from(upgradeCandidate, version));
			});
			content.getAffectedFiles()
					.addAll(files.stream().map(VirtualFile::getPath).toList());

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

	/**
	 * Clears the plan to empty content, keeping the prior content for undo.
	 */
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

	/**
	 * Merges a copied plan fragment into the current content, unioning the affected
	 * files and appending fragment items not already present, keeping the prior
	 * content for undo.
	 */
	class PasteItems implements PlanAction {

		private final Content pasteContent;

		private final Content content;

		private final List<String> oldFiles;

		private final List<Item> oldItems;

		private final List<String> newFiles = new ArrayList<>();

		private final List<Item> newItems = new ArrayList<>();

		private PasteItems(Content pasteContent, Content content) {

			this.pasteContent = pasteContent;
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

			content.apply(it -> {

				newFiles.clear();
				newItems.clear();

				Set<String> paths = new LinkedHashSet<>(it.getAffectedFiles());
				paths.addAll(pasteContent.getAffectedFiles());
				newFiles.addAll(paths);
				it.setAffectedFiles(new ArrayList<>(newFiles));

				Set<Item> items = new LinkedHashSet<>(it.getItems());
				items.removeAll(pasteContent.getItems());
				items.addAll(pasteContent.getItems());

				newItems.addAll(items);

				it.setItems(new ArrayList<>(newItems));
			});
		}

		@Override
		public void undo() {

			content.apply(it -> {

				it.getAffectedFiles().clear();
				it.getAffectedFiles().addAll(oldFiles);

				it.getItems().clear();
				it.getItems().addAll(oldItems);
			});
		}

	}

}
