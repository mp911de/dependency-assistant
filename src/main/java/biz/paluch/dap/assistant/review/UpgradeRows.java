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

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import biz.paluch.dap.util.Sequence;

/**
 * Name-sorted review rows with groupable candidates collapsed into one
 * selection.
 *
 * @author Mark Paluch
 */
class UpgradeRows implements Sequence<TableRow> {

	private final List<TableRow> rows;

	private UpgradeRows(List<TableRow> rows) {
		this.rows = rows;
	}

	/**
	 * Group the complete candidate list. Member order is retained within each
	 * group.
	 */
	static UpgradeRows of(List<SingleTableRow> candidates) {

		return new UpgradeRows(new Grouper(candidates)
				.group(new GroupByRule())
				.group(new InferredGrouping())
				.toRows());
	}

	@Override
	public Iterator<TableRow> iterator() {
		return rows.iterator();
	}

	@Override
	public Stream<TableRow> stream() {
		return rows.stream();
	}

	/**
	 * Return the backing row list in display order.
	 */
	@Override
	public List<TableRow> toList() {
		return rows;
	}

	@Override
	public boolean isEmpty() {
		return rows.isEmpty();
	}

	@Override
	public String toString() {

		String toString = "UpgradeRows: " + System.lineSeparator();
		toString += rows.stream()
				.map(it -> " * " + it.getDisplayName() + "@" + it.getCurrentVersion())
				.collect(Collectors.joining(System.lineSeparator()));
		return toString;
	}

}
