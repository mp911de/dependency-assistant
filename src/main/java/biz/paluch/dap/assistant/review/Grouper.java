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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One-shot accumulator applying {@link GroupingPolicy grouping policies} to the
 * dependency-check candidates and reassembling the display-ordered rows.
 *
 * <p>Each registered group replaces its members at the position of the first
 * member; candidates not claimed by any policy remain individual rows in their
 * original order.
 *
 * @author Mark Paluch
 */
class Grouper {

	private final List<TableRow> candidates;

	private final Map<TableRow, GroupRow> groupByFirstMember = new LinkedHashMap<>();

	private final Set<TableRow> grouped = new LinkedHashSet<>();

	Grouper(List<TableRow> candidates) {
		this.candidates = candidates;
	}

	/**
	 * Apply the given policy and register the groups it forms.
	 *
	 * @param policy the policy to apply.
	 * @return {@code this} for chaining.
	 */
	Grouper group(GroupingPolicy<TableRow, GroupRow> policy) {

		for (GroupRow group : policy.group(candidates)) {
			register(group);
		}
		return this;
	}

	/**
	 * Register a formed group: it replaces its members at the first member's
	 * position and suppresses all members from individual display.
	 */
	private void register(GroupRow group) {

		List<TableRow> members = group.getMembers();
		groupByFirstMember.put(members.getFirst(), group);
		grouped.addAll(members);
	}

	/**
	 * Reassemble the candidates into display order, emitting each registered group
	 * at its first member's position and skipping suppressed members.
	 *
	 * @return the rows in display order.
	 */
	List<TableRow> toRows() {

		List<TableRow> rows = new ArrayList<>(candidates.size());
		for (TableRow candidate : candidates) {

			GroupRow group = groupByFirstMember.get(candidate);
			if (group != null) {
				rows.add(group);
				continue;
			}

			if (!grouped.contains(candidate)) {
				rows.add(candidate);
			}
		}
		return rows;
	}

}
