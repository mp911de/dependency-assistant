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

import java.util.Iterator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import biz.paluch.dap.util.Sequence;

/**
 * The dependency-check rows after collapsing groupable upgrade candidates into
 * {@link GroupRow} rows through the {@link GroupByRule governed} and
 * {@link InferredGrouping inferred} grouping policies.
 *
 * <p>Each group replaces its members at the position of the first member; all
 * other candidates remain individual rows in their original order. Iterate the
 * result to obtain the rows in display order; build it from a complete
 * candidate list through {@link #of(List)}.
 *
 * @author Mark Paluch
 * @see GroupRow
 * @see GroupingPolicy
 */
class UpgradeRows implements Sequence<TableRow> {

	private final List<TableRow> rows;

	private UpgradeRows(List<TableRow> rows) {
		this.rows = rows;
	}

	/**
	 * Collapse groupable candidates into {@link GroupRow} rows, keeping all other
	 * candidates as individual rows.
	 *
	 * @param candidates the aggregated candidates in display order.
	 * @return the rows with each group replacing its members at the position of the
	 * first member.
	 */
	static UpgradeRows of(List<TableRow> candidates) {

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
	 * Return the collapsed rows in display order.
	 *
	 * @return the rows.
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
				.map(it -> " * " + it.getDependencyName() + "@" + it.getCurrentVersion())
				.collect(Collectors.joining(System.lineSeparator()));
		return toString;
	}

}
