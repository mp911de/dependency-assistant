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

package biz.paluch.dap.assistant;

import java.util.Comparator;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.StringUtils;
import org.jetbrains.annotations.Nullable;

/**
 * Value object capturing an applied dependency update, carrying the display
 * label and the out-of-bounds result used by the after-apply balloon.
 *
 * <p>An update is <em>out of bounds</em> when the applied version is rejected
 * by the dependency's governing rule ({@code !rule.test(to)}); for a dependency
 * with no constraining rule it means the upgrade crosses a major. The rule is
 * the single source of truth, so a Safe Version crossing a tier the rule
 * forbids is flagged through the rule branch while the major-only default
 * applies where no rule constrains. This is the safe-version footgun guard:
 * friction lands after a bulk apply, never on an explicit single fix.
 *
 * @param artifactId the upgraded coordinate.
 * @param from the version before the update.
 * @param to the version after the update.
 * @param displayLabel the rule/group label, falling back to the coordinate.
 * @param outOfBounds whether the applied version is outside the dependency's
 * governing bounds.
 * @author Mark Paluch
 */
record AppliedDependencyUpdate(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion to, String displayLabel,
		boolean outOfBounds) implements Comparable<AppliedDependencyUpdate> {

	private static final Comparator<AppliedDependencyUpdate> COMPARATOR = Comparator
			.comparing(AppliedDependencyUpdate::displayLabel);

	/**
	 * Create an applied update, computing the display label and the out-of-bounds
	 * result from the given rule.
	 *
	 * @param artifactId the upgraded coordinate.
	 * @param from the version before the update.
	 * @param to the version after the update.
	 * @param rule the dependency's governing rule.
	 * @param upgradeStrategy the applied upgrade strategy if available.
	 * @return the applied update.
	 */
	static AppliedDependencyUpdate of(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion to,
			DependencyRule rule, @Nullable UpgradeStrategy upgradeStrategy) {

		String name = rule.isPresent() ? rule.getDependencyName() : null;
		String label = StringUtils.hasText(name) ? name : artifactId.toString();
		boolean compliant;
		if (rule.isPresent()) {
			compliant = rule.test(to);
			if (upgradeStrategy != null) {
				compliant &= rule.isEnabled(upgradeStrategy);
			}
		} else {
			compliant = to.hasSameMajor(from);
		}

		return new AppliedDependencyUpdate(artifactId, from, to, label,
				!compliant);
	}

	/**
	 * Create an applied update from a {@link DependencyUpdate} and its governing
	 * rule.
	 *
	 * @param update the applied update.
	 * @param rule the dependency's governing rule.
	 * @return the applied update.
	 */
	static AppliedDependencyUpdate from(DependencyUpdate update, DependencyRule rule) {
		return of(update.artifactId(), update.from().getVersion(), update.version(), rule,
				update.getUpgradeStrategy());
	}

	@Override
	public int compareTo(AppliedDependencyUpdate o) {
		return COMPARATOR.compare(this, o);
	}

}
