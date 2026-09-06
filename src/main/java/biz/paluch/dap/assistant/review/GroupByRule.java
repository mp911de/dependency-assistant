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
 * Group candidates with the same rule-defined dependency name and package
 * system.
 * <p>The largest cohort agreeing on a current version forms the group. Drifting
 * candidates join it when they share a version property with a member.
 *
 * @author Mark Paluch
 */
class GroupByRule implements GroupingPolicy<SingleTableRow, GroupRow> {

	@Override
	public List<GroupRow> group(List<SingleTableRow> candidates) {

		Map<GroupKey, List<SingleTableRow>> buckets = new LinkedHashMap<>();
		Map<String, List<SingleTableRow>> byName = new LinkedHashMap<>();
		for (SingleTableRow candidate : candidates) {

			if (!isApplicable(candidate)) {
				continue;
			}

			GroupKey key = GroupKey.of(candidate);
			buckets.computeIfAbsent(key, it -> new ArrayList<>()).add(candidate);
			byName.computeIfAbsent(key.dependencyName(), it -> new ArrayList<>())
					.add(candidate);
		}

		List<GroupRow> groups = new ArrayList<>();
		buckets.values().forEach(bucket -> {

			VersionAgreement agreement = VersionAgreement.select(bucket);
			if (agreement == null || agreement.members().size() < 2) {
				return;
			}
			String name = bucket.getFirst().getName();

			groups.add(GroupRow.governed(name, withPropertySharingDrifters(bucket, agreement.members())));
		});

		return groups;
	}

	static boolean isApplicable(TableRow candidate) {
		DependencyRule rule = candidate.getRule();
		return rule.isPresent() && !StringUtils.isEmpty(rule.getDependencyName());
	}

	private static List<SingleTableRow> withPropertySharingDrifters(List<SingleTableRow> bucket,
			List<SingleTableRow> cohort) {

		Set<String> memberProperties = new LinkedHashSet<>();
		cohort.forEach(member -> memberProperties.addAll(member.getVersionPropertyNames()));

		List<SingleTableRow> members = new ArrayList<>(bucket.size());
		for (SingleTableRow candidate : bucket) {

			if (cohort.contains(candidate) || (candidate.getDeclaredVersions().hasVersionDrift()
					&& !Collections.disjoint(candidate.getVersionPropertyNames(), memberProperties))) {
				members.add(candidate);
			}
		}

		return members;
	}

	private record GroupKey(String dependencyName, PackageSystem packageSystem) {

		static GroupKey of(SingleTableRow candidate) {

			DependencyRule rule = candidate.getRule();
			return new GroupKey(rule.getDependencyName(), candidate.getPackageSystem());
		}

	}

}
