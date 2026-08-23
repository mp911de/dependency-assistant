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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.PackageIdentity;

/**
 * One-shot accumulator applying {@link GroupingPolicy grouping policies} to the
 * dependency-check candidates and reassembling the display-ordered rows.
 *
 * @author Mark Paluch
 */
class Grouper {

	private final List<SingleTableRow> candidates;

	private final Set<GroupRow> grouped = new LinkedHashSet<>();

	Grouper(List<SingleTableRow> candidates) {
		this.candidates = candidates;
	}

	/**
	 * Apply the given policy and register the groups it forms.
	 *
	 * @param policy the policy to apply.
	 * @return {@code this} for chaining.
	 */
	Grouper group(GroupingPolicy<SingleTableRow, GroupRow> policy) {
		grouped.addAll(policy.group(candidates));
		return this;
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

			GroupRow matchingGroup = null;
			PackageIdentity pkg = candidate.getUpgrade().getPackageIdentity();

			for (GroupRow groupRow : grouped) {
				if (groupRow.represents(pkg)) {
					matchingGroup = groupRow;
					break;
				}
			}

			if (matchingGroup != null) {
				grouped.remove(matchingGroup);
				rows.add(matchingGroup);
				continue;
			}

			boolean represented = false;
			for (TableRow row : rows) {
				if (row.represents(pkg)) {
					represented = true;
					break;
				}
			}

			if (represented) {
				continue;

			}

			rows.add(candidate);
		}

		rows.sort(TableRow::compareTo);
		return rows;
	}

}
