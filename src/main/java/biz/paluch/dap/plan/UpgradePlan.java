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

import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
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

	private final FileScope scope;

	private final List<UpgradePlanItem> items;

	private UpgradePlan(FileScope scope, List<UpgradePlanItem> items) {
		this.scope = scope;
		this.items = List.copyOf(items);
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
