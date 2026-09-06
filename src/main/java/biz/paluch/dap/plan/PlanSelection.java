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
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import org.jspecify.annotations.Nullable;

/**
 * Plan items targeted by an action, in source order.
 * <p>Event resolution ignores non-item entries. The plan tree supplies plan
 * order regardless of its display sorting.
 *
 * @author Mark Paluch
 */
class PlanSelection implements Sequence<UpgradePlanItem> {

	private static final PlanSelection EMPTY = new PlanSelection(List.of());

	private final List<UpgradePlanItem> items;

	private PlanSelection(List<UpgradePlanItem> items) {
		this.items = items;
	}

	static PlanSelection empty() {
		return EMPTY;
	}

	/**
	 * Copy the selected items in action-processing order.
	 */
	static PlanSelection of(List<UpgradePlanItem> items) {
		return new PlanSelection(List.copyOf(items));
	}

	static PlanSelection from(AnActionEvent e) {

		Object[] selection = e.getData(PlatformDataKeys.SELECTED_ITEMS);
		if (selection == null || selection.length == 0) {
			return new PlanSelection(List.of());
		}

		List<UpgradePlanItem> items = new ArrayList<>(selection.length);
		for (Object value : selection) {
			if (value instanceof UpgradePlanItem item) {
				items.add(item);
			}
		}

		return new PlanSelection(items);
	}

	List<UpgradePlanItem> items() {
		return items;
	}

	/**
	 * Return the first item, or {@literal null} for an empty selection.
	 */
	@Nullable
	UpgradePlanItem first() {
		return items.isEmpty() ? null : items.getFirst();
	}

	/**
	 * Run the action for the first item if present.
	 */
	void doWithFirst(Consumer<UpgradePlanItem> consumer) {
		if (!items.isEmpty()) {
			consumer.accept(items.getFirst());
		}
	}

	@Override
	public boolean isEmpty() {
		return items.isEmpty();
	}

	@Override
	public Stream<UpgradePlanItem> stream() {
		return items.stream();
	}

	@Override
	public Iterator<UpgradePlanItem> iterator() {
		return items.iterator();
	}

	/**
	 * Return selected items, evaluating the fallback only for an empty selection.
	 */
	List<UpgradePlanItem> orElseGet(Supplier<? extends List<UpgradePlanItem>> supplier) {
		return items.isEmpty() ? supplier.get() : items;
	}

}
