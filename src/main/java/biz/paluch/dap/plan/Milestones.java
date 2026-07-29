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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Value object for milestones.
 *
 * @author Mark Paluch
 */
class Milestones {

	private static final Pattern BRANCH_VERSION = Pattern.compile("(\\d+)\\.(\\d+)");

	private final List<? extends Milestone> milestones;

	Milestones(List<? extends Milestone> milestones) {
		this.milestones = milestones;
	}

	/**
	 * Return whether the list of milestones is empty.
	 *
	 * @return {@literal true} if the list is empty.
	 */
	public boolean isEmpty() {
		return milestones.isEmpty();
	}

	public @Nullable Milestone findOrDefault(@Nullable String selectedTitle,
			@Nullable MilestoneSelector selector) {
		return findOrDefault(selectedTitle, selector != null ? selector.getBranch() : null,
				selector != null ? selector.getProjectVersion() : Versioned.unversioned());
	}

	public @Nullable Milestone findOrDefault(@Nullable String selectedTitle,
			@Nullable String branch, Versioned projectVersion) {

		Milestone selected = findMilestone(selectedTitle);
		return selected != null ? selected : findDefaultMilestone(branch, projectVersion);
	}

	public @Nullable Milestone findMilestone(@Nullable String selectedTitle) {
		if (selectedTitle == null) {
			return null;
		}
		for (Milestone milestone : milestones) {
			if (milestone.getTitle().equals(selectedTitle)) {
				return milestone;
			}
		}
		return null;
	}

	public @Nullable Milestone findDefaultMilestone(@Nullable String branch, Versioned projectVersion) {

		if (milestones.isEmpty()) {
			return null;
		}

		String prefix = StringUtils.hasText(branch) ? getVersionPrefix(branch) : null;
		if (prefix == null && projectVersion.isVersioned()) {
			prefix = getVersionPrefix(projectVersion.getVersion().toString());
		}
		if (prefix == null) {
			return null;
		}

		Milestone lowest = null;
		for (Milestone milestone : milestones) {

			String title = milestone.getTitle();
			if (!milestone.isOpen() || !title.equals(prefix) && !title.startsWith(prefix + ".")) {
				continue;
			}
			if (lowest == null || MilestoneComparator.INSTANCE.compare(milestone, lowest) < 0) {
				lowest = milestone;
			}
		}

		return lowest;
	}

	private static @Nullable String getVersionPrefix(String value) {
		Matcher matcher = BRANCH_VERSION.matcher(value);
		return matcher.find() ? matcher.group(1) + "." + matcher.group(2) : null;
	}

}
