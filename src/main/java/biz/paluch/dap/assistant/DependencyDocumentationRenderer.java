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

import java.net.URI;
import java.text.NumberFormat;
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
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.Vulnerability;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * Renders the Quick Documentation HTML body for one dependency declaration: the
 * coordinate header, the current value, the {@link ReleaseDigest} release
 * table, and known security advisories. Immutable and bound to one resolved
 * {@link ArtifactReferenceContext}; resolution and target lifecycle live in
 * {@link DependencyDocumentationProvider}.
 *
 * @author Mark Paluch
 */
class DependencyDocumentationRenderer {

	private static final int MAX_VERSIONS = 6;

	private static final int MAX_PREVIEWS = 2;

	private final ArtifactReferenceContext artifactContext;

	private final InterfaceAssistant interfaceAssistant;

	private final @Nullable ArtifactVersion currentVersion;

	private final boolean linkable;

	private final NumberFormat decimalFormat = NumberFormat.getIntegerInstance();

	/**
	 * Create a renderer from a resolved {@link ArtifactReferenceContext}, reusing
	 * its interface assistant, cache, and current version rather than re-resolving
	 * the element.
	 *
	 * @param artifactContext a {@link ArtifactReferenceContext#isPresent() present}
	 * reference context.
	 * @param linkable {@literal true} to wrap non-current version rows in upgrade
	 * links.
	 */
	DependencyDocumentationRenderer(ArtifactReferenceContext artifactContext, boolean linkable) {
		this.artifactContext = artifactContext;
		this.interfaceAssistant = artifactContext.getDependencyContext()
				.getInterfaceAssistant();
		ArtifactDeclaration declaration = artifactContext.getDeclaration();
		this.currentVersion = declaration.isVersionDefined() ? declaration.getVersion() : null;
		this.linkable = linkable;
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
		StringBuilder sb = new StringBuilder();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(artifactId);
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		if (currentVersion != null) {
			sb.append(renderCurrentVersion(currentVersion));
		}

		Releases releases = artifactContext.getCache().getReleases(artifactId);

		if (!releases.isEmpty()) {
			ReleaseDigest digest = ReleaseDigest.of(releases, currentVersion, MAX_PREVIEWS, MAX_VERSIONS);
			appendVersionsTable(sb, artifactId, digest, withIcons, formatter);
		}

		appendSecurityAdvisories(sb);

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	/**
	 * Append the security-advisories section for a vulnerable current version;
	 * cache-only, renders nothing for clean or unscanned dependencies.
	 */
	private void appendSecurityAdvisories(StringBuilder sb) {

		if (currentVersion == null) {
			return;
		}

		Vulnerabilities vulnerabilities = artifactContext.getCurrentVulnerabilities();
		if (!vulnerabilities.isVulnerable()) {
			return;
		}

		sb.append("<p><b>")
				.append(MessageBundle.message("documentation.security-advisories"))
				.append("</b></p>");
		sb.append("<ul>");

		for (Vulnerability vulnerability : vulnerabilities) {
			sb.append("<li>");
			sb.append(Markdown.of(vulnerability.getTitle()).toHtml());
			sb.append(" (");
			String identifier = vulnerability.getIdentifier();

			HtmlChunk identifierText = HtmlChunk.text(identifier);
			if (isHttpLink(vulnerability.getSourceUrl())) {
				HtmlChunk.Element link = HtmlChunk.tag("a")
						.attr("href", vulnerability.getSourceUrl())
						.child(identifierText);
				sb.append(link);
			} else {
				sb.append(identifierText);
			}

			sb.append(", CVSS ").append(String.format(Locale.ROOT, "%.1f", vulnerability.getCvssScore()));
			sb.append(" ").append(vulnerability.getSeverity().getLabel());
			sb.append(")</li>");
		}
		sb.append("</ul>");
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
	 * @param property the version property to document; must not be
	 * {@literal null}.
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
		StringBuilder sb = new StringBuilder();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(property.name());
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		if (currentVersion != null) {
			sb.append(renderCurrentVersion(currentVersion));
		}

		for (ReleaseGroup group : ReleaseGroup.group(interfaceAssistant, artifactContext.getCache(), MAX_VERSIONS,
				property.artifacts())) {

			sb.append(group.renderHeader());
			if (group.hasReleases()) {

				ReleaseDigest digest = group.digest(currentVersion, MAX_PREVIEWS, MAX_VERSIONS);
				appendVersionsTable(sb, group.artifactIds.getFirst(), digest, withIcons, formatter);
			}

			appendSecurityAdvisories(sb);
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	private String renderCurrentVersion(ArtifactVersion version) {
		HtmlChunk.Element code = HtmlChunk.text(interfaceAssistant.getDocumentationText(version))
				.code();
		return HtmlChunk.p().addText(MessageBundle.message("documentation.current-value")).addText(": ").child(code)
				.toString();
	}

	/**
	 * Append the release table for the {@link ReleaseDigest} rows; hidden rows are
	 * summarized by grayed notes linking to the Dependency Check dialog so
	 * truncation is never silent.
	 */
	private void appendVersionsTable(StringBuilder sb, ArtifactId artifactId, ReleaseDigest digest,
			boolean withIcons, ReleaseDateFormatter formatter) {


		if (!digest.previewRows().isEmpty() || !digest.releaseRows().isEmpty()) {

			sb.append("<table>");
			appendRows(sb, artifactId, digest.previewRows(), withIcons, formatter);

			if (digest.morePreviews() > 0) {
				sb.append("<tr>");
				if (withIcons) {
					sb.append("<td></td>");
				}
				HtmlChunk link = checkDialogLink(
						MessageBundle.message("documentation.more-previews",
								decimalFormat.format(digest.morePreviews())));
				sb.append("<td colspan='2'>")
						.append(link)
						.append("</td></tr>");
			}

			appendRows(sb, artifactId, digest.releaseRows(), withIcons, formatter);
			sb.append("</table>");
		}

		if (digest.moreReleases() > 0) {

			String message = MessageBundle.message("documentation.more-releases",
					decimalFormat.format(digest.moreReleases()), digest.moreReleases());
			HtmlChunk link = checkDialogLink(message);
			sb.append(HtmlChunk.p().child(link));
		}
	}

	private void appendRows(StringBuilder sb, ArtifactId artifactId, List<Release> rows, boolean withIcons,
			ReleaseDateFormatter formatter) {

		for (Release release : rows) {
			sb.append("<tr>");
			sb.append(new DocumentedRelease(artifactContext, artifactId.toString(), release,
					interfaceAssistant, linkable, withIcons).render(formatter));
			sb.append("</tr>");
		}
	}

	/**
	 * Wrap the text in a link opening the Dependency Check dialog focused on the
	 * documented declaration.
	 */
	private HtmlChunk checkDialogLink(String text) {

		HtmlChunk chunk = HtmlChunk.text(text);
		return HtmlChunk.tag("a")
				.attr("href", DependencyUpgradeLinkHandler.CHECK_SCHEME)
				.child(chunk);
	}

	private static HtmlChunk grayed(HtmlChunk chunk) {
		return DocumentationMarkup.GRAYED_ELEMENT.child(chunk);
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

		String renderHeader() {
			return HtmlChunk.p()
					.addText(MessageBundle.message("documentation.property-for"))
					.addText(" ")
					.addRaw(formatArtifactIds(assistant)).toString();
		}

		private String formatArtifactIds(InterfaceAssistant assistant) {

			StringBuilder sb = new StringBuilder();
			for (ArtifactId artifactId : artifactIds) {
				if (!sb.isEmpty()) {
					sb.append(", ");
				}
				sb.append("<code>");
				sb.append(StringUtil.escapeXmlEntities(assistant.getDisplayName(artifactId)));
				sb.append("</code>");
			}
			return sb.toString();
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

		private final InterfaceAssistant interfaceAssistant;

		private final boolean current;

		private final boolean linkable;

		private final @Nullable HtmlChunk firstColumnIcon;

		DocumentedRelease(ArtifactReferenceContext artifactContext, String name, Release release,
				InterfaceAssistant interfaceAssistant, boolean linkable, boolean withIcons) {

			this.release = release;
			this.key = release.unwrap().toString();
			this.interfaceAssistant = interfaceAssistant;

			VersionStatus status = artifactContext.getStatus(release.getVersion());
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

		StringBuilder render(ReleaseDateFormatter formatter) {

			StringBuilder sb = new StringBuilder();
			if (firstColumnIcon != null) {
				sb.append(firstColumnIcon);
			}

			sb.append("<td>");
			boolean preview = release.isPreview();
			if (preview) {
				sb.append("<i>");
			}
			HtmlChunk version = renderVersion();
			if (linkable) {
				sb.append(HtmlChunk.tag("a")
						.attr("href", DependencyUpgradeLinkHandler.SCHEME + key)
						.child(version));
			} else {
				sb.append(version);
			}
			if (preview) {
				sb.append("</i>");
			}
			sb.append("</td><td>");
			if (release.releaseDate() != null) {
				sb.append(formatter.formatLong(release.releaseDate()));
			}
			sb.append("</td>");

			return sb;
		}

		private HtmlChunk renderVersion() {
			HtmlChunk text = HtmlChunk.text(interfaceAssistant.getDocumentationText(release.version()));
			return current ? text.bold() : text;
		}

	}

}
