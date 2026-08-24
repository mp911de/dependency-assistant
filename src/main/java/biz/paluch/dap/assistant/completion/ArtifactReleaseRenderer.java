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

package biz.paluch.dap.assistant.completion;

import java.time.Duration;
import java.time.LocalDateTime;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersioningScheme;
import biz.paluch.dap.assistant.VersionStatus;
import biz.paluch.dap.assistant.presentation.DependencyPresentation;
import biz.paluch.dap.checker.SecurityShieldIcons;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementPresentation;
import com.intellij.codeInsight.lookup.LookupElementRenderer;
import com.intellij.icons.AllIcons;
import org.jspecify.annotations.Nullable;

/**
 * Renders release lookup rows with release metadata and their
 * {@link VersionStatus}.
 *
 * <p>Instances belong to one completion result. Every proposed release must be
 * registered through {@link #withVersion(ArtifactRelease)} before rendering so
 * the row tails use a common version-column width.
 *
 * @author Mark Paluch
 */
class ArtifactReleaseRenderer extends LookupElementRenderer<LookupElement> {

	private static final int MAX_VERSION_LENGTH_PADDING = 15;

	private final ReleaseDateFormatter formatter = ReleaseDateFormatter.create();

	private final @Nullable ArtifactVersion currentVersion;

	private final DependencyRule rule;

	private final VulnerabilityRepository vulnerabilities;

	private final DependencyPresentation presentation;

	private int versionLength = 0;

	/**
	 * Create a renderer for the proposed releases of one artifact.
	 *
	 * @param currentVersion the currently declared version, or {@literal null} when
	 * no comparable version is available. Opaque versions are treated as absent.
	 * @param rule the dependency rule governing the artifact.
	 * @param vulnerabilities the read-only per-version vulnerability view.
	 * @param presentation the dependency names rendered in row tails.
	 */
	public ArtifactReleaseRenderer(@Nullable ArtifactVersion currentVersion, DependencyRule rule,
			VulnerabilityRepository vulnerabilities, DependencyPresentation presentation) {
		this.currentVersion = currentVersion != null && currentVersion.scheme() == VersioningScheme.OPAQUE ? null
				: currentVersion;
		this.rule = rule;
		this.vulnerabilities = vulnerabilities;
		this.presentation = presentation;
	}

	/**
	 * Format the release date for a lookup row.
	 *
	 * @param release the release whose date to format.
	 * @return the formatted date, or an empty string when the release has no date.
	 */
	public String formatReleaseDate(ArtifactRelease release) {
		LocalDateTime releaseDate = release.getReleaseDate();
		return releaseDate == null ? "" : formatter.format(releaseDate);
	}

	@Override
	public void renderElement(LookupElement element, LookupElementPresentation presentation) {

		if (!(element.getObject() instanceof ArtifactRelease release)) {
			return;
		}

		ArtifactVersion version = release.getVersion().unwrap();
		String itemText = version.toString();
		presentation.setItemText(itemText);

		DependencyRuleEvaluator evaluator = DependencyRuleEvaluator.create(rule,
				version);

		String typeText = "";
		LocalDateTime releaseDate = release.getReleaseDate();

		if (releaseDate != null) {
			Duration age = Duration.between(releaseDate, LocalDateTime.now());
			if (age.toDays() < 5) {
				presentation.setItemTextUnderlined(true);
			}
			typeText = formatReleaseDate(release);
		}

		if (release.getVersion() instanceof GitVersion gitVersion && gitVersion.hasSha()) {
			typeText = gitVersion.getRequiredShortSha() + " " + typeText;
			presentation.setTypeText(typeText.trim(), AllIcons.Vcs.CommitNode);
		} else {
			presentation.setTypeText(typeText.trim());
		}

		VersionStatus status = VersionStatus.of(evaluator, currentVersion, release.getVersion(),
				vulnerabilities.getVulnerabilities(release.getVersion()));
		presentation.setItemTextItalic(status.isOlder());
		presentation.setItemTextBold(status.isCurrent());
		presentation.setStrikeout(status.isRuleViolation());
		presentation.setIcon(status.getIcon(SecurityShieldIcons.OUTLINE));

		String tailText;

		if (itemText.length() < MAX_VERSION_LENGTH_PADDING) {
			int padding = this.versionLength - itemText.length();
			tailText = " ".repeat(padding);
		} else {
			tailText = " ";
		}

		String tailLabel = status.getVulnerabilityTailLabel();
		if (StringUtils.hasText(tailLabel)) {
			tailText += tailLabel;
		} else {

			if (this.presentation.hasDependencyName()) {
				tailText += this.presentation.getDependencyName();
			} else {
				tailText += this.presentation.getCoordinates();
			}
		}

		presentation.setTailText(" " + tailText, true);
	}

	/**
	 * Register a proposed release for version-column width calculation.
	 *
	 * <p>All proposed releases must be registered before
	 * {@link #renderElement(LookupElement, LookupElementPresentation)} is called.
	 *
	 * @param release the proposed release to register.
	 */
	public void withVersion(ArtifactRelease release) {

		ArtifactVersion version = release.getVersion().unwrap();
		this.versionLength = Math.max(version.toString().length(), versionLength);
	}

}
