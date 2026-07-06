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
import java.util.EnumMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.Sequence;

/**
 * Immutable live Upgrade Plan: materialized plan items paired with the
 * build-file scope they were captured from. Its serialized counterpart is
 * {@link UpgradePlanState.Content}.
 *
 * @author Mark Paluch
 */
class UpgradePlan implements Sequence<UpgradePlanItem> {

	private static final UpgradePlan EMPTY = new UpgradePlan(FileScope.of(), List.of());

	private final FileScope scope;

	private final List<UpgradePlanItem> items;

	private UpgradePlan(FileScope scope, List<UpgradePlanItem> items) {
		this.scope = scope;
		this.items = List.copyOf(items);
	}

	/**
	 * Return the empty plan: no items, no scope.
	 */
	static UpgradePlan empty() {
		return EMPTY;
	}

	/**
	 * Create a plan from the given scope and items.
	 *
	 * @param scope the build-file scope the items were captured from.
	 * @param items the reconstructed plan items in order.
	 * @return the plan.
	 */
	static UpgradePlan of(FileScope scope, List<UpgradePlanItem> items) {
		return new UpgradePlan(scope, items);
	}

	/**
	 * Return a plan with the given items, keeping this plan's scope. Used by the
	 * remove flow, which narrows the items but not the scope.
	 *
	 * @param items the remaining plan items in order.
	 * @return the derived plan.
	 */
	UpgradePlan withItems(Iterable<UpgradePlanItem> items) {
		return new UpgradePlan(scope, Sequence.of(items).toList());
	}

	FileScope getScope() {
		return scope;
	}

	List<UpgradePlanItem> getItems() {
		return items;
	}

	public int size() {
		return items.size();
	}

	@Override
	public boolean isEmpty() {
		return items.isEmpty();
	}

	/**
	 * Return a plan holding only the items matching the predicate, keeping this
	 * plan's scope. Returns this same plan when every item matches, so callers can
	 * compare by identity to detect a no-op.
	 *
	 * @param predicate the retention test applied to each item.
	 * @return the filtered plan, or this plan when nothing was removed.
	 */
	public UpgradePlan filter(Predicate<? super UpgradePlanItem> predicate) {
		List<UpgradePlanItem> filtered = stream().filter(predicate).toList();
		return filtered.size() == items.size() ? this : withItems(filtered);
	}

	/**
	 * Return a plan with each matching item replaced by the replacement result,
	 * preserving item order and scope. Returns this same plan when no item matches,
	 * so callers can compare by identity to detect a no-op.
	 *
	 * @param predicate selects the items to replace.
	 * @param replacement maps a matched item to its replacement.
	 * @return the derived plan, or this plan when nothing matched.
	 */
	public UpgradePlan replace(Predicate<? super UpgradePlanItem> predicate,
			Function<? super UpgradePlanItem, ? extends UpgradePlanItem> replacement) {

		List<UpgradePlanItem> replaced = new ArrayList<>(items.size());
		boolean matched = false;
		for (UpgradePlanItem item : items) {
			if (predicate.test(item)) {
				replaced.add(replacement.apply(item));
				matched = true;
			} else {
				replaced.add(item);
			}
		}
		return matched ? withItems(replaced) : this;
	}

	/**
	 * Merge a plan fragment into this plan, appending fragment items not already
	 * present by {@link UpgradePlanItem#equals(Object) item identity} and unioning
	 * the two build-file scopes with this plan's paths first.
	 *
	 * @param fragment the plan fragment to fold in.
	 * @return the merged plan.
	 */
	UpgradePlan merge(UpgradePlan fragment) {

		List<UpgradePlanItem> merged = new ArrayList<>(items);
		Set<UpgradePlanItem> seen = new HashSet<>(items);
		for (UpgradePlanItem item : fragment) {
			if (seen.add(item)) {
				merged.add(item);
			}
		}

		LinkedHashSet<String> paths = new LinkedHashSet<>(scope.getPaths());
		paths.addAll(fragment.scope.getPaths());
		return new UpgradePlan(FileScope.from(paths), merged);
	}

	/**
	 * Return a plan whose scope is re-resolved against the current file system
	 * state, keeping the items. Apply and preview rebuild before reading files.
	 */
	UpgradePlan rebuild() {
		return new UpgradePlan(scope.rebuild(), items);
	}

	@Override
	public Stream<UpgradePlanItem> stream() {
		return items.stream();
	}

	@Override
	public List<UpgradePlanItem> toList() {
		return items;
	}

	@Override
	public Iterator<UpgradePlanItem> iterator() {
		return items.iterator();
	}

	/**
	 * Return the plan summary line for the tool window: the item count followed by
	 * the counts of items that warrant review, per attention level.
	 *
	 * @return the rendered summary text.
	 */
	public String getSummary() {
		return MessageBundle.message("plan.summary", size()) + getAttentionSummary();
	}

	/**
	 * Attention counts in {@link UpgradePlanItem.AttentionLevel} declaration order,
	 * highest attention first. Patch is the no-attention level and stays out of the
	 * line.
	 */
	private String getAttentionSummary() {

		Map<UpgradePlanItem.AttentionLevel, Long> counts = new EnumMap<>(UpgradePlanItem.AttentionLevel.class);
		for (UpgradePlanItem item : items) {
			counts.merge(item.getAttentionLevel(), 1L, Long::sum);
		}

		StringBuilder summary = new StringBuilder();
		for (UpgradePlanItem.AttentionLevel level : UpgradePlanItem.AttentionLevel.values()) {

			Long count = counts.get(level);
			if (count == null || level == UpgradePlanItem.AttentionLevel.PATCH) {
				continue;
			}
			summary.append(" · ").append(getAttentionLabel(level, count));
		}

		return summary.toString();
	}

	private static String getAttentionLabel(UpgradePlanItem.AttentionLevel level, long count) {

		return switch (level) {
		case VULNERABILITY_FIX -> MessageBundle.message("plan.summary.attention.cve", count);
		case MAJOR -> MessageBundle.message("plan.summary.attention.major", count);
		case MINOR -> MessageBundle.message("plan.summary.attention.minor", count);
		case DOWNGRADE -> MessageBundle.message("plan.summary.attention.downgrade", count);
		case PATCH -> "";
		};
	}

	@Override
	public String toString() {
		return "UpgradePlan[" + items.size() + " items, scope=" + scope.getPresentablePaths() + "]";
	}

}
