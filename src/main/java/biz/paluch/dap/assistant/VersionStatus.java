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

import javax.swing.Icon;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.checker.ShieldStyle;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.util.ResolvableIcon;
import org.jspecify.annotations.Nullable;

/**
 * Version status for a single dependency upgrade candidate.
 *
 * @author Mark Paluch
 */
public class VersionStatus {

	private final DependencyRuleEvaluator evaluator;

	private final @Nullable ArtifactVersion currentVersion;

	private final ArtifactVersion candidate;

	private final Vulnerabilities vulnerabilities;

	private VersionStatus(DependencyRuleEvaluator evaluator, @Nullable ArtifactVersion currentVersion,
			ArtifactVersion candidate, Vulnerabilities vulnerabilities) {
		this.evaluator = evaluator;
		this.currentVersion = currentVersion;
		this.candidate = candidate;
		this.vulnerabilities = vulnerabilities;
	}

	public static VersionStatus of(DependencyRuleEvaluator evaluator, @Nullable ArtifactVersion currentVersion,
			ArtifactVersion candidate, Vulnerabilities vulnerabilities) {
		return new VersionStatus(evaluator, currentVersion, candidate, vulnerabilities);
	}

	public @Nullable ArtifactVersion getCurrentVersion() {
		return currentVersion;
	}

	public ArtifactVersion getArtifactVersion() {
		return candidate;
	}

	public Vulnerabilities getVulnerabilities() {
		return vulnerabilities;
	}

	/**
	 * @return {@literal true} when the candidate is the declared current version;
	 * {@literal false} otherwise.
	 */
	public boolean isCurrent() {
		return candidate.equals(currentVersion);
	}

	/**
	 * @return {@literal true} when the candidate is older than the declared current
	 * version; {@literal false} when no current version is known or the candidate
	 * is same-or-newer.
	 */
	public boolean isOlder() {
		return currentVersion != null && getVersionAge() == VersionAge.OLDER;
	}

	/**
	 * @return {@literal true} when the candidate is a preview version.
	 */
	public boolean isPreview() {
		return candidate.isPreview();
	}

	/**
	 * @return {@literal true} when the candidate violates the governing dependency
	 * rule; {@literal false} otherwise.
	 */
	public boolean isRuleViolation() {
		return !evaluator.test(candidate);
	}

	/**
	 * @return {@literal true} when the candidate carries a known vulnerability;
	 * {@literal false} otherwise.
	 */
	public boolean isVulnerable() {
		return vulnerabilities.isVulnerable();
	}

	/**
	 * Return the version-age category for callers that deliberately present age
	 * semantics. Rule violations do not erase this value; rule precedence applies
	 * only to shared icon selection.
	 *
	 * @return the version-age category.
	 */
	public VersionAge getVersionAge() {

		if (currentVersion == null) {
			return candidate.isPreview() ? VersionAge.PREVIEW : VersionAge.SAME_OR_UNKNOWN;
		}

		if (candidate.isPreview()) {
			return VersionAge.PREVIEW;
		}
		return VersionAge.between(currentVersion, candidate);
	}

	/**
	 * Return the icon for a surface (dialog combo, completion lookup).
	 *
	 * @param style the shield weight to use when the candidate is vulnerable.
	 * @return the icon.
	 */
	public Icon getIcon(ShieldStyle style) {
		return resolveIcon(style).getIcon();
	}

	/**
	 * Return the icon for a surface (dialog combo, completion lookup), pairing the
	 * Swing icon with the reflective path the {@code <icon src>} resolver
	 * re-resolves.
	 *
	 * @param style the shield weight to use when the candidate is vulnerable.
	 * @return the resolvable icon.
	 */
	public ResolvableIcon resolveIcon(ShieldStyle style) {

		if (vulnerabilities.isVulnerable()) {
			return style.shield(vulnerabilities.getHighestSeverity());
		}
		if (isRuleViolation()) {
			return DependencyUpgradeIcons.ruleWarning();
		}
		if (isRuleAlignedFallback()) {
			return DependencyUpgradeIcons.ruleCompliant();
		}
		return DependencyUpgradeIcons.resolve(getVersionAge());
	}

	private boolean isRuleAlignedFallback() {
		return evaluator.isPresent() && evaluator.isLocked()
				&& (currentVersion == null || getVersionAge() == VersionAge.OLDER);
	}

	/**
	 * Return the Swing icon for documentation surfaces, always rendered with the
	 * filled shield weight.
	 *
	 * @return the Swing icon.
	 */
	public Icon getFilledIcon() {
		return resolveFilledIcon().getIcon();
	}

	/**
	 * Return the documentation icon: the Swing icon paired with the reflective path
	 * the {@code <icon src>} resolver re-resolves, always the filled shield weight.
	 *
	 * @return the resolvable documentation icon.
	 */
	public ResolvableIcon resolveFilledIcon() {
		return resolveIcon(ShieldStyle.FILLED);
	}

	/**
	 * Return the compact vulnerability label used by completion tails, or
	 * {@literal null} when the candidate is not vulnerable.
	 */
	@Nullable
	public String getVulnerabilityTailLabel() {

		if (!vulnerabilities.isVulnerable()) {
			return null;
		}

		Vulnerability top = vulnerabilities.getTopVulnerability();
		String id = top.getIdentifier();
		int remaining = vulnerabilities.size() - 1;
		return remaining > 0 ? id + " + " + remaining : id;
	}

}
