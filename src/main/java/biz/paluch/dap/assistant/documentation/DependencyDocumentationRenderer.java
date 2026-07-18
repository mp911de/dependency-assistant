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

package biz.paluch.dap.assistant.documentation;

import java.net.URI;
import java.text.NumberFormat;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.assistant.VersionStatus;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import org.jspecify.annotations.Nullable;

/**
 * Renders the Quick Documentation HTML body for one dependency declaration or
 * release lookup item: the coordinate header, the current value, the
 * {@link ReleaseDigest} release table, and known security advisories. Immutable
 * and constructed from its rendering inputs; resolution and target lifecycle
 * live in {@link DependencyDocumentationProvider}.
 *
 * @author Mark Paluch
 */
class DependencyDocumentationRenderer {

	private static final int MAX_VERSIONS = 6;

	private static final int MAX_PREVIEWS = 2;

	private final InterfaceAssistant interfaceAssistant;

	private final StateService stateService;

	private final DependencyRuleEvaluator evaluator;

	private final @Nullable ArtifactVersion currentVersion;

	private final boolean linkable;

	private final NumberFormat decimalFormat = NumberFormat.getIntegerInstance();

	DependencyDocumentationRenderer(InterfaceAssistant interfaceAssistant,
			StateService stateService,
			DependencyRuleEvaluator evaluator, @Nullable ArtifactVersion currentVersion, boolean linkable) {
		this.interfaceAssistant = interfaceAssistant;
		this.stateService = stateService;
		this.evaluator = evaluator;
		this.currentVersion = currentVersion;
		this.linkable = linkable;
	}

	/**
	 * Create a renderer from a resolved {@link ArtifactReferenceContext}.
	 *
	 * @param context a {@link ArtifactReferenceContext#isPresent() present}
	 * reference context.
	 * @param linkable {@literal true} to wrap non-current version rows in upgrade
	 * links.
	 * @return the renderer.
	 */
	static DependencyDocumentationRenderer from(ArtifactReferenceContext context, boolean linkable) {

		ProjectDependencyContext dependencyContext = context.getDependencyContext();
		return new DependencyDocumentationRenderer(dependencyContext.getInterfaceAssistant(),
				context.getStateService(), context.getEvaluator(),
				context.getVersion(), linkable);
	}

	/**
	 * Render the documentation body for a single concrete artifact.
	 *
	 * @param artifactId the artifact to document.
	 * @param withIcons {@literal true} to render the full body with
	 * {@link VersionAge} icons and upgrade links; {@literal false} for plain HTML
	 * without icons or links (hover hint).
	 * @return the HTML body.
	 */
	String render(ArtifactId artifactId, boolean withIcons) {

		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();
		HtmlBuilder content = new HtmlBuilder();

		if (currentVersion != null) {
			content.append(renderCurrentVersion(currentVersion));
		}

		Releases releases = stateService.getCache().getReleases(artifactId);

		if (!releases.isEmpty()) {
			ReleaseDigest digest = ReleaseDigest.of(releases, currentVersion, MAX_PREVIEWS, MAX_VERSIONS);
			content.append(versionsTable(artifactId, digest, withIcons, formatter));
		}

		content.append(securityAdvisories(artifactId));

		return document(HtmlChunk.text(artifactId.toString()), content);
	}

	/**
	 * Render the documentation body for a single release lookup item.
	 *
	 * @param release the release represented by the lookup item.
	 * @return the HTML body.
	 */
	String render(ArtifactRelease release) {

		ArtifactId artifactId = release.artifactId();
		ArtifactVersion version = release.getVersion().unwrap();
		Vulnerabilities vulnerabilities = stateService.getVulnerabilities(artifactId,
				release.getVersion());
		VersionStatus status = VersionStatus.of(evaluator, currentVersion, release.getVersion(), vulnerabilities);

		HtmlBuilder content = new HtmlBuilder();
		content.append(releaseRelation(release));

		List<HtmlChunk> sections = new ArrayList<>();
		LocalDateTime releaseDate = release.getReleaseDate();
		if (releaseDate != null) {
			sections.add(section("documentation.release.released",
					HtmlChunk.text(ReleaseDateFormatter.create().formatDetailed(releaseDate))));
		}

		if (release.getVersion() instanceof GitVersion gitVersion && StringUtils.hasText(gitVersion.getSha())) {
			sections.add(section("documentation.release.commit", HtmlChunk.text(gitVersion.getShortSha()).code()));
		}

		String advisoriesNote = null;
		if (currentVersion != null && !release.getVersion().matches(currentVersion)) {

			Vulnerabilities currentVulnerabilities = stateService.getVulnerabilities(artifactId, currentVersion);
			List<Vulnerability> fixed = fixedVulnerabilities(currentVulnerabilities, vulnerabilities);
			if (!fixed.isEmpty()) {
				sections.add(section("documentation.release.fixes", fixesCell(fixed, currentVersion)));
			}

			if (hasSameAdvisories(currentVulnerabilities, vulnerabilities)) {
				advisoriesNote = MessageBundle.message("documentation.release.advisories.same");
			}
		}

		if (evaluator.isPresent() && status.isRuleViolation()) {
			sections.add(section("documentation.release.rule",
					HtmlChunk.text(MessageBundle.message("documentation.release.rule-violation"))));
		}

		if (!sections.isEmpty()) {

			HtmlBuilder rows = new HtmlBuilder();
			sections.forEach(rows::append);
			content.append(rows.wrapWith(DocumentationMarkup.SECTIONS_TABLE));
		}

		content.append(securityAdvisories(vulnerabilities, advisoriesNote));

		return document(HtmlChunk.text(artifactId + " " + version), content);
	}

	/**
	 * Render how the release relates to the current version: the upgrade kind
	 * (patch, minor, major, preview) or a downgrade, fused with the release-date
	 * distance when both release dates are known. The date direction is reported
	 * honestly, so a version-older maintenance release published after the current
	 * version reads as released after it.
	 */
	private HtmlChunk releaseRelation(ArtifactRelease release) {

		ArtifactVersion currentVersion = this.currentVersion;
		if (currentVersion == null) {
			return HtmlChunk.empty();
		}

		if (release.getVersion().matches(currentVersion)) {
			return HtmlChunk.p().addText(MessageBundle.message("documentation.release.current"));
		}

		VersionAge age = VersionAge.between(currentVersion, release.getVersion().unwrap());
		if (age == VersionAge.SAME_OR_UNKNOWN) {
			return HtmlChunk.empty();
		}

		// Relation messages carry inline <i> emphasis on the date direction, so they
		// render raw; all interpolated values are plugin-generated.
		return HtmlChunk.p().child(HtmlChunk.raw(relationMessage(release, age, currentVersion))).addText(" ")
				.child(versionCode(currentVersion));
	}

	private String relationMessage(ArtifactRelease release, VersionAge age, ArtifactVersion currentVersion) {

		String distance = null;
		boolean releasedAfterCurrent = false;
		LocalDateTime releaseDate = release.getReleaseDate();
		Release currentRelease = findRelease(release.artifactId(), currentVersion);
		LocalDateTime currentDate = currentRelease != null ? currentRelease.releaseDate() : null;

		if (releaseDate != null && currentDate != null) {
			distance = ReleaseDateFormatter.create().formatAge(currentDate, releaseDate);
			releasedAfterCurrent = releaseDate.isAfter(currentDate);
		}

		if (age == VersionAge.OLDER) {

			if (distance == null) {
				return MessageBundle.message("documentation.release.downgrade");
			}
			return MessageBundle.message(releasedAfterCurrent ? "documentation.release.downgrade.after"
					: "documentation.release.downgrade.before", distance);
		}

		String kind = upgradeKind(age);
		if (distance == null) {
			return MessageBundle.message("documentation.release.upgrade", kind);
		}
		return MessageBundle.message(releasedAfterCurrent ? "documentation.release.upgrade.after"
				: "documentation.release.upgrade.before", kind, distance);
	}

	private static String upgradeKind(VersionAge age) {
		return switch (age) {
		case NEWER_PATCH -> MessageBundle.message("upgrade-strategy.PATCH");
		case NEWER_MINOR -> MessageBundle.message("upgrade-strategy.MINOR");
		case NEWER_MAJOR -> MessageBundle.message("upgrade-strategy.MAJOR");
		case PREVIEW -> MessageBundle.message("upgrade-strategy.PREVIEW");
		default -> throw new IllegalStateException("Unexpected version age %s".formatted(age));
		};
	}

	private @Nullable Release findRelease(ArtifactId artifactId, ArtifactVersion version) {

		for (Release release : stateService.getCache().getReleases(artifactId)) {
			if (release.getVersion().matches(version)) {
				return release;
			}
		}
		return null;
	}

	/**
	 * Return the advisories affecting the current version that no longer affect the
	 * candidate release.
	 */
	private static List<Vulnerability> fixedVulnerabilities(Vulnerabilities current, Vulnerabilities candidate) {

		if (!current.isVulnerable()) {
			return List.of();
		}

		Set<String> remaining = identifiers(candidate);
		List<Vulnerability> fixed = new ArrayList<>();
		for (Vulnerability vulnerability : current) {

			if (!remaining.contains(vulnerability.getIdentifier())) {
				fixed.add(vulnerability);
			}
		}
		return fixed;
	}

	private static boolean hasSameAdvisories(Vulnerabilities current, Vulnerabilities candidate) {

		if (!current.isVulnerable() || !candidate.isVulnerable()) {
			return false;
		}
		return identifiers(current).equals(identifiers(candidate));
	}

	private static Set<String> identifiers(Vulnerabilities vulnerabilities) {

		if (!vulnerabilities.isVulnerable()) {
			return Set.of();
		}

		Set<String> identifiers = new HashSet<>();
		for (Vulnerability vulnerability : vulnerabilities) {
			identifiers.add(vulnerability.getIdentifier());
		}
		return identifiers;
	}

	private HtmlChunk fixesCell(List<Vulnerability> fixed, ArtifactVersion currentVersion) {

		List<HtmlChunk> advisories = fixed.stream()
				.map(vulnerability -> new HtmlBuilder()
						.append(advisoryIdentifier(vulnerability))
						.append(" (" + vulnerability.getSeverity().getLabel() + ")")
						.toFragment())
				.toList();

		return new HtmlBuilder()
				.appendWithSeparators(HtmlChunk.text(", "), advisories)
				.append(" " + MessageBundle.message("documentation.release.fixes.affecting") + " ")
				.append(versionCode(currentVersion))
				.toFragment();
	}

	private HtmlChunk versionCode(ArtifactVersion version) {
		return HtmlChunk.text(version.toDocumentationString()).code();
	}

	private static HtmlChunk section(String labelKey, HtmlChunk value) {
		return HtmlChunk.tag("tr").children(
				DocumentationMarkup.SECTION_HEADER_CELL.child(HtmlChunk.p().addText(MessageBundle.message(labelKey))),
				DocumentationMarkup.SECTION_CONTENT_CELL.child(value));
	}

	private static String document(HtmlChunk definition, HtmlBuilder content) {
		return new HtmlBuilder()
				.append(DocumentationMarkup.DEFINITION_ELEMENT
						.child(DocumentationMarkup.PRE_ELEMENT.child(definition)))
				.append(content.wrapWith(DocumentationMarkup.CONTENT_ELEMENT))
				.toString();
	}

	/**
	 * Render the security-advisories section for the current version of the given
	 * artifact; cache-only, empty for clean or unscanned dependencies.
	 */
	private HtmlChunk securityAdvisories(ArtifactId artifactId) {

		if (currentVersion == null) {
			return HtmlChunk.empty();
		}

		Vulnerabilities vulnerabilities = stateService.getVulnerabilities(artifactId, currentVersion);
		return securityAdvisories(vulnerabilities);
	}

	/**
	 * Render the security-advisories section for the given vulnerabilities; empty
	 * for clean or unscanned versions.
	 */
	private static HtmlChunk securityAdvisories(Vulnerabilities vulnerabilities) {
		return securityAdvisories(vulnerabilities, null);
	}

	/**
	 * Render the security-advisories section for the given vulnerabilities, with an
	 * optional plain-text note behind the section header; empty for clean or
	 * unscanned versions.
	 */
	private static HtmlChunk securityAdvisories(Vulnerabilities vulnerabilities, @Nullable String note) {

		if (!vulnerabilities.isVulnerable()) {
			return HtmlChunk.empty();
		}

		HtmlBuilder advisories = new HtmlBuilder();
		for (Vulnerability vulnerability : vulnerabilities) {
			advisories.append(advisory(vulnerability));
		}

		HtmlChunk.Element headline = HtmlChunk.p()
				.child(HtmlChunk.text(MessageBundle.message("documentation.security-advisories")).bold());
		if (note != null) {
			headline = headline.addText(" " + note);
		}

		return new HtmlBuilder()
				.append(headline)
				.append(advisories.wrapWith("ul"))
				.toFragment();
	}

	private static HtmlChunk advisory(Vulnerability vulnerability) {

		return HtmlChunk.li().children(
				HtmlChunk.raw(Markdown.of(vulnerability.getTitle()).toHtml()),
				HtmlChunk.text(" ("),
				advisoryIdentifier(vulnerability),
				HtmlChunk.text(", CVSS " + String.format(Locale.ROOT, "%.1f", vulnerability.getCvssScore()) + " "
						+ vulnerability.getSeverity().getLabel() + ")"));
	}

	/**
	 * Render the advisory identifier, linked to its source when the source URL uses
	 * a supported scheme.
	 */
	private static HtmlChunk advisoryIdentifier(Vulnerability vulnerability) {

		HtmlChunk identifier = HtmlChunk.text(vulnerability.getIdentifier());
		return isHttpLink(vulnerability.getSourceUrl())
				? HtmlChunk.link(vulnerability.getSourceUrl(), identifier)
				: identifier;
	}

	private static boolean isHttpLink(String url) {

		try {
			URI uri = URI.create(url);
			String scheme = uri.getScheme();
			return "http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme);
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	/**
	 * Render the documentation body for a version property, with one release table
	 * per group of artifacts sharing the same available versions.
	 *
	 * @param property the version property to document .
	 * @param withIcons {@literal true} to render the full body with
	 * {@link VersionAge} icons and upgrade links; {@literal false} for plain HTML
	 * without icons or links (hover hint).
	 * @return the HTML body, or {@literal null} if the property drives no
	 * artifacts.
	 */
	@Nullable
	String render(VersionProperty property, boolean withIcons) {

		if (property.artifacts().isEmpty()) {
			return null;
		}

		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();
		HtmlBuilder content = new HtmlBuilder();

		if (currentVersion != null) {
			content.append(renderCurrentVersion(currentVersion));
		}

		for (ReleaseGroup group : ReleaseGroup.group(interfaceAssistant, stateService.getCache(), MAX_VERSIONS,
				property.artifacts())) {

			content.append(group.renderHeader());
			if (group.hasReleases()) {

				ReleaseDigest digest = group.digest(currentVersion, MAX_PREVIEWS, MAX_VERSIONS);
				content.append(versionsTable(group.artifactIds.getFirst(), digest, withIcons, formatter));
			}

			content.append(securityAdvisories(group.artifactIds.getFirst()));
		}

		return document(HtmlChunk.text(property.name()), content);
	}

	private HtmlChunk renderCurrentVersion(ArtifactVersion version) {
		return HtmlChunk.p().addText(MessageBundle.message("documentation.current-value")).addText(": ")
				.child(versionCode(version));
	}

	/**
	 * Render the release table for the {@link ReleaseDigest} rows; hidden rows are
	 * summarized by notes linking to the Dependency Check dialog so truncation is
	 * never silent.
	 */
	private HtmlChunk versionsTable(ArtifactId artifactId, ReleaseDigest digest, boolean withIcons,
			ReleaseDateFormatter formatter) {

		HtmlBuilder section = new HtmlBuilder();

		if (!digest.previewRows().isEmpty() || !digest.releaseRows().isEmpty()) {

			HtmlBuilder rows = new HtmlBuilder();
			appendRows(rows, artifactId, digest.previewRows(), withIcons, formatter);

			if (digest.morePreviews() > 0) {
				rows.append(morePreviewsRow(digest.morePreviews(), withIcons));
			}

			appendRows(rows, artifactId, digest.releaseRows(), withIcons, formatter);
			section.append(rows.wrapWith("table"));
		}

		if (digest.moreReleases() > 0) {
			section.append(HtmlChunk.p().child(checkDialogLink(MessageBundle.message("documentation.more-releases",
					decimalFormat.format(digest.moreReleases()), digest.moreReleases()))));
		}

		return section.toFragment();
	}

	private void appendRows(HtmlBuilder rows, ArtifactId artifactId, List<Release> releases, boolean withIcons,
			ReleaseDateFormatter formatter) {

		for (Release release : releases) {

			Vulnerabilities vulnerabilities = stateService.getVulnerabilities(artifactId, release.getVersion());
			VersionStatus status = VersionStatus.of(evaluator, currentVersion, release.getVersion(),
					vulnerabilities);
			rows.append(new DocumentedRelease(status, artifactId.toString(), release, linkable, withIcons)
					.render(formatter));
		}
	}

	private HtmlChunk morePreviewsRow(int morePreviews, boolean withIcons) {

		HtmlChunk.Element note = HtmlChunk.tag("td").attr("colspan", 2)
				.child(checkDialogLink(
						MessageBundle.message("documentation.more-previews", decimalFormat.format(morePreviews))));

		HtmlChunk.Element row = HtmlChunk.tag("tr");
		return withIcons ? row.children(emptyCell(), note) : row.child(note);
	}

	private static HtmlChunk.Element emptyCell() {
		return HtmlChunk.tag("td").child(HtmlChunk.empty());
	}

	/**
	 * Wrap the text in a link opening the Dependency Check dialog focused on the
	 * documented declaration.
	 */
	private static HtmlChunk checkDialogLink(String text) {
		return HtmlChunk.link(DependencyUpgradeLinkHandler.CHECK_SCHEME, text);
	}

	/**
	 * Bounded selection of the release rows the popup renders for one artifact,
	 * keeping the popup an upgrade decision aid rather than a release changelog.
	 * Previews newer than the current version fill a top section capped at a few
	 * rows; every other release, stable or the current anchor, fills the release
	 * section capped separately. Rows beyond the caps are carried as counts.
	 *
	 * @param previewRows the previews newer than the current version, newest first.
	 * @param releaseRows the stable releases and the current-version anchor, newest
	 * first.
	 * @param morePreviews the number of newer previews beyond {@code previewRows}.
	 * @param moreReleases the number of releases hidden from the release section,
	 * including previews at or below the current version.
	 */
	record ReleaseDigest(List<Release> previewRows, List<Release> releaseRows, int morePreviews, int moreReleases) {

		/**
		 * Select the rows to render from the given releases. Previews count against
		 * {@code previewLimit} only when newer than the current version; previews at or
		 * below the current version are folded into {@link #moreReleases()}. Stable
		 * releases fill up to {@code releaseLimit} rows regardless of the current
		 * version, and the release matching the current version is always kept as the
		 * anchor row. Duplicate versions are skipped entirely, matching the release
		 * table's previous de-duplication.
		 *
		 * @param releases the artifact's analyzed release history.
		 * @param currentVersion the declared version, or {@literal null} when
		 * unresolved.
		 * @param previewLimit the maximum number of newer-preview rows.
		 * @param releaseLimit the maximum number of stable rows.
		 * @return the digest.
		 */
		static ReleaseDigest of(Releases releases, @Nullable ArtifactVersion currentVersion, int previewLimit,
				int releaseLimit) {

			List<Release> previewRows = new ArrayList<>();
			List<Release> releaseRows = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			int shownReleases = 0;
			int morePreviews = 0;
			int moreReleases = 0;

			for (Release release : releases) {

				if (!seen.add(release.unwrap().toString())) {
					continue;
				}

				if (release.getVersion().matches(currentVersion)) {
					releaseRows.add(release);
					if (!release.isPreview()) {
						shownReleases++;
					}
					continue;
				}

				if (release.isPreview()) {

					boolean newer = currentVersion == null || release.isNewer(currentVersion);
					if (!newer) {
						moreReleases++;
					} else if (previewRows.size() < previewLimit) {
						previewRows.add(release);
					} else {
						morePreviews++;
					}
					continue;
				}

				if (shownReleases < releaseLimit) {
					shownReleases++;
					releaseRows.add(release);
				} else {
					moreReleases++;
				}
			}

			return new ReleaseDigest(previewRows, releaseRows, morePreviews, moreReleases);
		}

	}

	static class ReleaseGroup {

		private final List<ArtifactId> artifactIds = new ArrayList<>();

		private final InterfaceAssistant assistant;

		private final Releases releases;

		ReleaseGroup(InterfaceAssistant assistant, Releases releases) {
			this.assistant = assistant;
			this.releases = releases;
		}

		public boolean hasReleases() {
			return !releases.isEmpty();
		}

		static Collection<ReleaseGroup> group(InterfaceAssistant assistant, Cache cache, int limit,
				List<CachedArtifact> artifacts) {

			Map<Set<String>, ReleaseGroup> groups = new LinkedHashMap<>();
			for (CachedArtifact artifact : artifacts) {
				ArtifactId artifactId = artifact.toArtifactId();
				Releases releases = cache.getReleases(artifactId);
				Set<String> versionKeys = releaseVersionKeys(releases, limit);

				ReleaseGroup group = groups.computeIfAbsent(versionKeys, key -> new ReleaseGroup(assistant, releases));
				group.add(artifactId);
			}
			return groups.values();
		}

		private void add(ArtifactId artifactId) {
			this.artifactIds.add(artifactId);
		}

		private static Set<String> releaseVersionKeys(Releases releases, int limit) {
			return releases.stream().limit(limit)
					.map(Release::unwrap)
					.map(Object::toString)
					.collect(Collectors.toCollection(LinkedHashSet::new));
		}

		HtmlChunk renderHeader() {
			return HtmlChunk.p()
					.addText(MessageBundle.message("documentation.property-for"))
					.addText(" ")
					.child(formatArtifactIds(assistant));
		}

		private HtmlChunk formatArtifactIds(InterfaceAssistant assistant) {
			return new HtmlBuilder()
					.appendWithSeparators(HtmlChunk.text(", "), artifactIds.stream()
							.map(artifactId -> HtmlChunk.text(assistant.getDisplayName(artifactId)).code())
							.toList())
					.toFragment();
		}

		public Releases releases() {
			return releases;
		}

		public ReleaseDigest digest(@Nullable ArtifactVersion currentVersion, int maxPreviews, int maxVersions) {
			return ReleaseDigest.of(releases, currentVersion, maxPreviews, maxVersions);
		}

		@Override
		public String toString() {
			return "ReleaseGroup[" +
					"artifactIds=" + artifactIds + ", " +
					"releases=" + releases + ']';
		}

	}

	static class DocumentedRelease {

		private final Release release;

		private final String key;

		private final boolean current;

		private final boolean linkable;

		private final @Nullable HtmlChunk firstColumnIcon;

		DocumentedRelease(VersionStatus status, String name, Release release, boolean linkable, boolean withIcons) {

			this.release = release;
			this.key = release.unwrap().toString();
			this.current = status.isCurrent();
			this.linkable = linkable && !current;

			if (withIcons) {

				HtmlChunk htmlIcon = status.resolveFilledIcon().asHtml();
				HtmlChunk content = this.linkable
						? HtmlChunk.tag("a")
								.attr("href", DependencyUpgradeLinkHandler.SCHEME + key)
								.attr("title", MessageBundle.message("documentation.upgrade-to", name, key))
								.child(htmlIcon)
						: htmlIcon;
				this.firstColumnIcon = HtmlChunk.tag("td").child(content);
			} else {
				this.firstColumnIcon = null;
			}
		}

		HtmlChunk render(ReleaseDateFormatter formatter) {

			HtmlChunk version = linkable
					? HtmlChunk.link(DependencyUpgradeLinkHandler.SCHEME + key, renderVersion())
					: renderVersion();
			if (release.isPreview()) {
				version = version.italic();
			}

			HtmlChunk.Element row = HtmlChunk.tag("tr");
			if (firstColumnIcon != null) {
				row = row.child(firstColumnIcon);
			}

			return row.children(HtmlChunk.tag("td").child(version), dateCell(formatter));
		}

		private HtmlChunk.Element dateCell(ReleaseDateFormatter formatter) {

			HtmlChunk.Element cell = HtmlChunk.tag("td");
			return release.releaseDate() != null
					? cell.addText(formatter.format(release.releaseDate()))
					: cell.child(HtmlChunk.empty());
		}

		private HtmlChunk renderVersion() {
			HtmlChunk text = HtmlChunk.text(release.version().toDocumentationString());
			return current ? text.bold() : text;
		}

	}

}
