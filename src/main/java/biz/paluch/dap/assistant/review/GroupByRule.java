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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.util.StringUtils;

/**
 * Groups candidates governed by the same named {@link DependencyRule} within
 * one build ecosystem when they agree on one effective current version.
 *
 * <p>Per rule name only the largest agreeing cohort forms the group, extended
 * by drifting candidates that share a version property with a cohort member. A
 * candidate that is alone under its rule name is labeled by the dependency name
 * instead of forming a group.
 *
 * @author Mark Paluch
 */
class GroupByRule implements GroupingPolicy<TableRow, GroupRow> {

	@Override
	public List<GroupRow> group(List<TableRow> candidates) {

		Map<GroupKey, List<TableRow>> buckets = new LinkedHashMap<>();
		Map<String, List<TableRow>> byName = new LinkedHashMap<>();
		for (TableRow candidate : candidates) {

			if (!isApplicable(candidate)) {
				continue;
			}

			GroupKey key = GroupKey.of(candidate);
			buckets.computeIfAbsent(key, it -> new ArrayList<>()).add(candidate);
			byName.computeIfAbsent(key.dependencyName(), it -> new ArrayList<>())
					.add(candidate);
		}

		byName.values().forEach(named -> {
			if (named.size() == 1) {
				named.getFirst().labelByDependencyName();
			}
		});

		List<GroupRow> groups = new ArrayList<>();
		buckets.values().forEach(bucket -> {

			VersionAgreement agreement = VersionAgreement.select(bucket);
			if (agreement == null || agreement.members().size() < 2) {
				return;
			}

			groups.add(GroupRow.governed(withPropertySharingDrifters(bucket, agreement.members())));
		});

		return groups;
	}

	/**
	 * Return whether this policy is applicable to the given {@link TableRow}.
	 */
	static boolean isApplicable(TableRow candidate) {
		DependencyRule rule = candidate.getRule();
		return rule.isPresent() && !StringUtils.isEmpty(rule.getDependencyName());
	}

	private static List<TableRow> withPropertySharingDrifters(List<TableRow> bucket, List<TableRow> cohort) {

		Set<String> memberProperties = new LinkedHashSet<>();
		cohort.forEach(member -> memberProperties.addAll(member.getVersionPropertyNames()));

		List<TableRow> members = new ArrayList<>(bucket.size());
		for (TableRow candidate : bucket) {

			if (cohort.contains(candidate) || (candidate.getDeclaredVersions().hasVersionDrift()
					&& !Collections.disjoint(candidate.getVersionPropertyNames(), memberProperties))) {
				members.add(candidate);
			}
		}

		return members;
	}

	/**
	 * Grouping identity: the rule's dependency name within one build ecosystem.
	 */
	private record GroupKey(String dependencyName, PackageSystem packageSystem) {

		/**
		 * Return the group key for the candidate, or {@literal null} if the candidate
		 * is not governed by a named rule.
		 */
		static GroupKey of(TableRow candidate) {

			DependencyRule rule = candidate.getRule();
			return new GroupKey(rule.getDependencyName(), candidate.getUpgrade().getPresentation().getPackageSystem());
		}

	}

}
