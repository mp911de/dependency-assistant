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

import biz.paluch.dap.DependencyPresentation;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.ArtifactVersionChange;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import org.jetbrains.annotations.Nullable;

/**
 * Value object capturing an applied dependency update, carrying the display
 * label and the follow-up flag used by the after-apply balloon.
 *
 * @author Mark Paluch
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
		 * Within the governing rule, or an in-major upgrade with no rule.
		 */
		NONE,

		/**
		 * Rejected by the dependency's governing rule.
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
	 * @return {@literal true} if the update carries a follow-up {@link Flag};
	 * {@literal false} otherwise.
	 */
	public boolean isFlagged() {
		return flag != Flag.NONE;
	}

	/**
	 * Create an applied update from a {@link DependencyUpdate} and its governing
	 * rule.
	 */
	public static AppliedDependencyUpdate from(DependencyUpdate update, DependencyRule rule,
			DependencyPresentation presentation) {
		return new AppliedDependencyUpdate(update, presentation.getDisplayName(),
				flagFor(update, rule, update.getUpgradeStrategy()));
	}

	/**
	 * Create an applied update from a {@link DependencyUpdate}.
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
