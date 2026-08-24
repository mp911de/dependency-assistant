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
 * Selects either the filtered display view or the complete release and row view
 * for a dependency upgrade review.
 *
 * <p>The row list, release combo, and strategy buttons use the same instance so
 * a strategy target cannot be offered when its release is hidden.
 *
 * @author Mark Paluch
 * @param hideUpToDate whether to use the filtered row set, display releases,
 * and display strategy targets instead of the complete views.
 */
record VisibilityFilter(boolean hideUpToDate) {

	static final VisibilityFilter HIDE_UP_TO_DATE = new VisibilityFilter(true);

	static final VisibilityFilter SHOW_ALL = new VisibilityFilter(false);

	/**
	 * Return the release options to show for the given upgrade.
	 *
	 * @param upgrade the candidate whose releases are requested.
	 * @return the display releases when filtering, otherwise all known releases.
	 */
	Releases visibleReleases(DependencyUpgradeCandidate upgrade) {
		return hideUpToDate ? upgrade.getDisplayReleases() : upgrade.getReleases();
	}

	/**
	 * Return the strategy target to offer for the given upgrade, or {@literal null}
	 * if the strategy has no target or the target is hidden by this filter.
	 *
	 * @param upgrade the candidate whose target is requested.
	 * @param strategy the strategy to resolve.
	 * @return the offered release, or {@literal null} if none is available.
	 */
	@Nullable
	Release findRelease(DependencyUpgradeCandidate upgrade, UpgradeStrategy strategy) {
		return hideUpToDate ? upgrade.findCuratedRelease(strategy) : upgrade.findRelease(strategy);
	}

	/**
	 * Return whether the upgrade's row is shown. Filtering always retains
	 * vulnerable rows. Other rows require an actionable display target; rows whose
	 * only targets are latest and/or preview remain hidden unless a preview current
	 * version has a newer preview target.
	 *
	 * @param upgrade the candidate to test.
	 * @return {@code true} if the candidate belongs in the active row view.
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
