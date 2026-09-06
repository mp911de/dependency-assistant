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

import javax.swing.Icon;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.checker.SecurityShieldIcons;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.util.ResolvableIcon;
import org.jspecify.annotations.Nullable;

/**
 * Presentation status of a candidate version, combining age, rule compliance,
 * and known vulnerabilities.
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

	/**
	 * @param evaluator the rule evaluated against the current version.
	 * @param currentVersion the current version, or {@literal null} if unresolved.
	 */
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

	public boolean isCurrent() {
		return candidate.equals(currentVersion);
	}

	/**
	 * Return whether a non-preview candidate is older than a known current version.
	 */
	public boolean isOlder() {
		return currentVersion != null && getVersionAge() == VersionAge.OLDER;
	}

	public boolean isPreview() {
		return candidate.isPreview();
	}

	public boolean isRuleViolation() {
		return !evaluator.test(candidate);
	}

	public boolean isVulnerable() {
		return vulnerabilities.isVulnerable();
	}

	/**
	 * Return the version age independently of rule compliance and vulnerabilities.
	 * <p>Preview candidates remain previews even when the current version is
	 * unknown.
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

	public Icon getIcon(SecurityShieldIcons style) {
		return resolveIcon(style).getIcon();
	}

	/**
	 * Resolve the status icon.
	 * <p>Known vulnerabilities take precedence over rule violations, then a
	 * compliant lock fallback, then version age. The lock applies to older
	 * candidates or an unknown current version.
	 * @param style the style used only for vulnerability shields.
	 */
	public ResolvableIcon resolveIcon(SecurityShieldIcons style) {

		if (vulnerabilities.isVulnerable()) {
			return style.resolve(vulnerabilities.getHighestSeverity());
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
	 * Return the status icon with a filled vulnerability shield.
	 */
	public Icon getFilledIcon() {
		return resolveFilledIcon().getIcon();
	}

	/**
	 * Return the status icon and documentation reference with a filled shield.
	 */
	public ResolvableIcon resolveFilledIcon() {
		return resolveIcon(SecurityShieldIcons.FILLED);
	}

	/**
	 * Return a compact vulnerability label, or {@literal null} if not vulnerable.
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
