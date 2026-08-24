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

package biz.paluch.dap.assistant;

import java.util.Comparator;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.presentation.DependencyPresentation;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.ArtifactVersionChange;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import org.jetbrains.annotations.Nullable;

/**
 * Summary of a dependency update recorded after it changes a build file.
 *
 * <p>The summary retains the applied version change, its user-facing display
 * label, and the follow-up classification used by after-apply notifications.
 * Natural ordering uses only the display label. Sorted accumulators therefore
 * retain at most one summary for a label even though record equality includes
 * all three components.
 *
 * @author Mark Paluch
 * @param update the applied version change.
 * @param displayName the user-facing dependency label.
 * @param flag the follow-up classification for the applied change.
 */
public record AppliedDependencyUpdate(ArtifactVersionChange update,
		String displayName, Flag flag)
		implements Comparable<AppliedDependencyUpdate> {

	private static final Comparator<AppliedDependencyUpdate> COMPARATOR = Comparator
			.comparing(AppliedDependencyUpdate::displayName);

	public ArtifactVersion getFromVersion() {
		return update.from().getVersion();
	}

	public ArtifactVersion getTargetVersion() {
		return update.to();
	}

	/**
	 * Why an applied update is called out in the after-apply balloon.
	 */
	public enum Flag {

		/**
		 * No follow-up is required because the target complies with its governing rule
		 * and any classified upgrade strategy is enabled, or because no rule applies
		 * and the change does not cross a major version line.
		 */
		NONE,

		/**
		 * The target is rejected by the governing rule, or its classified upgrade
		 * strategy is disabled by that rule.
		 */
		COMPLIANCE,

		/**
		 * No governing rule, and the upgrade crosses a major version line.
		 */
		MAJOR_CROSSING

	}

	private static Flag flagFor(ArtifactVersionChange update, DependencyRule rule,
			@Nullable UpgradeStrategy upgradeStrategy) {

		if (rule.isPresent()) {

			boolean compliant = rule.test(update.to());
			if (upgradeStrategy != null) {
				compliant &= rule.isEnabled(upgradeStrategy);
			}
			return compliant ? Flag.NONE : Flag.COMPLIANCE;
		}

		return update.crossesMajor() ? Flag.MAJOR_CROSSING : Flag.NONE;
	}

	/**
	 * Return whether this update is called out in the after-apply balloon.
	 *
	 * @return {@code true} if the update carries a follow-up {@link Flag}.
	 */
	public boolean isFlagged() {
		return flag != Flag.NONE;
	}

	/**
	 * Create an applied update from a {@link DependencyUpdate} and its governing
	 * rule.
	 *
	 * @param update the update that changed a build file.
	 * @param rule the rule governing the dependency.
	 * @param presentation the source of the user-facing dependency label.
	 * @return the classified applied-update summary.
	 */
	public static AppliedDependencyUpdate from(DependencyUpdate update, DependencyRule rule,
			DependencyPresentation presentation) {
		return new AppliedDependencyUpdate(update, presentation.getDisplayName(),
				flagFor(update, rule, update.getUpgradeStrategy()));
	}

	/**
	 * Create an applied update from a {@link DependencyUpdate}.
	 *
	 * <p>Without a governing rule, only a major version crossing receives a
	 * follow-up flag.
	 *
	 * @param update the update that changed a build file.
	 * @param displayName the user-facing dependency label.
	 * @return the classified applied-update summary.
	 */
	public static AppliedDependencyUpdate from(DependencyUpdate update,
			String displayName) {
		return new AppliedDependencyUpdate(update, displayName,
				flagFor(update, DependencyRule.absent(), null));
	}

	@Override
	public int compareTo(AppliedDependencyUpdate o) {
		return COMPARATOR.compare(this, o);
	}

}
