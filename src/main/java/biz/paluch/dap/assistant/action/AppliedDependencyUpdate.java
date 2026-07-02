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

package biz.paluch.dap.assistant.action;

import java.util.Comparator;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.ArtifactVersionChange;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.StringUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Value object capturing an applied dependency update, carrying the display
 * label and the follow-up flag used by the after-apply balloon.
 *
 * <p>An update is flagged {@link Flag#OUT_OF_BOUNDS} when the applied version
 * is rejected by the dependency's governing rule ({@code !rule.test(to)}). For
 * a dependency with no constraining rule it is flagged
 * {@link Flag#MAJOR_CROSSING} when the upgrade crosses a major version line (a
 * versioning-scheme switch counts as one). The rule is the single source of
 * truth, so a Safe Version crossing a tier the rule forbids is flagged through
 * the rule branch while the major-only default applies where no rule
 * constrains. This is the safe-version footgun guard: friction lands after a
 * bulk apply, never on an explicit single fix.
 *
 * @param artifactId the upgraded coordinate.
 * @param from the version before the update.
 * @param to the version after the update.
 * @param displayLabel the rule/group label, falling back to the coordinate.
 * @param flag why the update needs follow-up attention, {@link Flag#NONE} when
 * it does not.
 * @author Mark Paluch
 */
public record AppliedDependencyUpdate(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion to,
		String displayLabel,
		Flag flag) implements Comparable<AppliedDependencyUpdate> {

	private static final Comparator<AppliedDependencyUpdate> COMPARATOR = Comparator
			.comparing(AppliedDependencyUpdate::displayLabel);

	/**
	 * Why an applied update is called out in the after-apply balloon.
	 */
	enum Flag {

		/** Within the governing rule, or an in-major upgrade with no rule. */
		NONE,

		/** Rejected by the dependency's governing rule. */
		OUT_OF_BOUNDS,

		/** No governing rule, and the upgrade crosses a major version line. */
		MAJOR_CROSSING

	}

	/**
	 * Create an applied update, computing the display label and the follow-up flag
	 * from the given rule.
	 *
	 * @param artifactId the upgraded coordinate.
	 * @param from the version before the update.
	 * @param to the version after the update.
	 * @param rule the dependency's governing rule.
	 * @param upgradeStrategy the applied upgrade strategy if available.
	 * @return the applied update.
	 */
	public static AppliedDependencyUpdate of(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion to,
			DependencyRule rule, @Nullable UpgradeStrategy upgradeStrategy) {

		String name = rule.isPresent() ? rule.getDependencyName() : null;
		String label = StringUtils.hasText(name) ? name : artifactId.toString();

		return new AppliedDependencyUpdate(artifactId, from, to, label,
				flagFor(artifactId, from, to, rule, upgradeStrategy));
	}

	private static Flag flagFor(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion to, DependencyRule rule,
			@Nullable UpgradeStrategy upgradeStrategy) {

		if (rule.isPresent()) {

			boolean compliant = rule.test(to);
			if (upgradeStrategy != null) {
				compliant &= rule.isEnabled(upgradeStrategy);
			}
			return compliant ? Flag.NONE : Flag.OUT_OF_BOUNDS;
		}

		return ArtifactVersionChange.of(artifactId, from, to).crossesMajor() ? Flag.MAJOR_CROSSING : Flag.NONE;
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
	 *
	 * @param update the applied update.
	 * @param rule the dependency's governing rule.
	 * @return the applied update.
	 */
	public static AppliedDependencyUpdate from(DependencyUpdate update, DependencyRule rule) {
		return of(update.artifactId(), update.from().getVersion(), update.version(), rule,
				update.getUpgradeStrategy());
	}

	@Override
	public int compareTo(AppliedDependencyUpdate o) {
		return COMPARATOR.compare(this, o);
	}

}
