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

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeSuggestion;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import org.jspecify.annotations.Nullable;

/**
 * Consistent visibility for review rows, release options and strategy targets.
 *
 * @author Mark Paluch
 * @param hideUpToDate whether to use curated display views instead of all
 * releases.
 */
record VisibilityFilter(boolean hideUpToDate) {

	static final VisibilityFilter HIDE_UP_TO_DATE = new VisibilityFilter(true);

	static final VisibilityFilter SHOW_ALL = new VisibilityFilter(false);

	Releases visibleReleases(DependencyUpgradeCandidate upgrade) {
		return hideUpToDate ? upgrade.getDisplayReleases() : upgrade.getReleases();
	}

	/**
	 * Return the strategy target, or {@literal null} if absent or hidden.
	 */
	@Nullable
	Release findRelease(DependencyUpgradeCandidate upgrade, UpgradeStrategy strategy) {
		return hideUpToDate ? upgrade.findCuratedRelease(strategy) : upgrade.findRelease(strategy);
	}

	/**
	 * Return whether the row is visible. All rows are visible when filtering is
	 * disabled. Filtering retains vulnerable rows and otherwise requires an
	 * actionable display target. Latest-only and preview-only targets do not
	 * qualify unless a preview current version has a newer preview target.
	 */
	boolean includes(DependencyUpgradeCandidate upgrade) {

		if (!hideUpToDate) {
			return true;
		}

		if (upgrade.isVulnerable()) {
			return true;
		}

		if (!upgrade.hasUpgradeTargets()) {
			return false;
		}

		UpgradeSuggestions targets = upgrade.getSuggestions();
		Dependency dependency = upgrade.getDependency();
		if (dependency.getCurrentVersion().isPreview()) {
			UpgradeSuggestion release = targets.get(UpgradeStrategy.PREVIEW);
			if (release.isPresent()) {
				return release.getRelease().isNewer(dependency.getCurrentVersion());
			}
		}
		if (targets.size() == 2 && targets.contains(UpgradeStrategy.LATEST)
				&& targets.contains(UpgradeStrategy.PREVIEW)) {
			return false;
		}

		if (targets.size() == 1 && (targets.contains(UpgradeStrategy.PREVIEW)
				|| targets.contains(UpgradeStrategy.LATEST))) {
			return false;
		}

		return !targets.isEmpty();
	}

}
