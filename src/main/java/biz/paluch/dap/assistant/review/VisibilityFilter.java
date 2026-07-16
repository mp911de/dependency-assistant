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

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeDecision;
import biz.paluch.dap.upgrade.UpgradeSuggestion;
import biz.paluch.dap.upgrade.UpgradeSuggestions;

/**
 * Decides what the dependency upgrade review shows: which rows are visible and
 * which release options are offered within a row.
 *
 * <p>This is the single home for the "filtered versus all" decision that the
 * row list, the suggestion combo, and the upgrade-target buttons would
 * otherwise each re-derive.
 *
 * @param hideUpToDate whether up-to-date rows and noise releases are hidden.
 * @author Mark Paluch
 */
record VisibilityFilter(boolean hideUpToDate) {

	static final VisibilityFilter HIDE_UP_TO_DATE = new VisibilityFilter(true);

	static final VisibilityFilter SHOW_ALL = new VisibilityFilter(false);

	/**
	 * Return the release options to show for the given option.
	 */
	Releases visibleReleases(UpgradeDecision decision) {
		return hideUpToDate ? decision.getDisplayReleases() : decision.getReleases();
	}

	/**
	 * Return the upgrade targets to show for the given option.
	 */
	UpgradeSuggestions visibleTargets(UpgradeDecision decision) {
		return hideUpToDate ? decision.getDisplaySuggestions() : decision.getSuggestions();
	}

	/**
	 * Return whether the option's row is shown. When hiding up-to-date rows, an
	 * decision is shown only if it has an update target and is not preview-only.
	 */
	boolean includes(UpgradeDecision decision) {

		if (!hideUpToDate) {
			return true;
		}

		if (decision.isVulnerable()) {
			return true;
		}

		if (!decision.hasUpgradeTargets()) {
			return false;
		}

		UpgradeSuggestions targets = decision.getSuggestions();
		Dependency dependency = decision.getDependency();
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
