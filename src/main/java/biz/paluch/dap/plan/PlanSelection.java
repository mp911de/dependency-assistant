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
 * The plan items a user action targets, resolved from an {@link AnActionEvent}
 * selection or handed over directly from the plan tree.
 *
 * <p>Holds the selected items in tree order and adds first-item and fallback
 * access for actions that operate on the selection, or on a supplied fallback
 * when nothing is selected. Non-plan-item entries in the source selection (such
 * as member rows) are dropped during resolution.
 *
 * @author Mark Paluch
 */
class PlanSelection implements Sequence<UpgradePlanItem> {

	private static final PlanSelection EMPTY = new PlanSelection(List.of());

	private final List<UpgradePlanItem> items;

	private PlanSelection(List<UpgradePlanItem> items) {
		this.items = items;
	}

	/**
	 * Returns an empty {@code PlanSelection} instance.
	 *
	 * @return an empty {@code PlanSelection}.
	 */
	static PlanSelection empty() {
		return EMPTY;
	}

	/**
	 * Wrap an already-resolved selection, such as the plan tree's selected items.
	 *
	 * @param items the selected plan items in tree order.
	 * @return the plan selection.
	 */
	static PlanSelection of(List<UpgradePlanItem> items) {
		return new PlanSelection(List.copyOf(items));
	}

	/**
	 * Create the selection from the given action event.
	 *
	 * @param e the action event carrying the tree selection.
	 * @return the plan selection; empty when the event carries none.
	 */
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

	/**
	 * Return the selected plan items in tree order; empty without a selection.
	 */
	List<UpgradePlanItem> items() {
		return items;
	}

	/**
	 * Return the first selected plan item, or {@literal null} when the selection is
	 * empty.
	 */
	@Nullable
	UpgradePlanItem first() {
		return items.isEmpty() ? null : items.getFirst();
	}

	/**
	 * Run the given action on the first selected item, doing nothing when the
	 * selection is empty.
	 *
	 * @param consumer the action to run on the first selected item.
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
	 * Return the plan items the selection covers: the selected items, or the
	 * supplied fallback when nothing is selected. The supplier is consulted only
	 * for an empty selection.
	 *
	 * @param supplier the fallback items, evaluated only when the selection is
	 * empty.
	 * @return the selected items, or the supplier's result when nothing is
	 * selected.
	 */
	List<UpgradePlanItem> orElseGet(Supplier<? extends List<UpgradePlanItem>> supplier) {
		return items.isEmpty() ? supplier.get() : items;
	}

}
