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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

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
 * Immutable rendering context for dependency Quick Documentation.
 *
 * @author Mark Paluch
 */
class DocumentationContext {

	private static final int MAX_VERSIONS = 10;

	private final ArtifactReferenceContext artifactContext;

	private final InterfaceAssistant interfaceAssistant;

	private final @Nullable ArtifactVersion currentVersion;

	private final boolean linkable;

	DocumentationContext(ArtifactReferenceContext artifactContext, boolean linkable) {
		this.artifactContext = artifactContext;
		this.interfaceAssistant = artifactContext.getDependencyContext()
				.getInterfaceAssistant();
		ArtifactDeclaration declaration = artifactContext.getDeclaration();
		this.currentVersion = declaration.isVersionDefined() ? declaration.getVersion() : null;
		this.linkable = linkable;
	}

	/**
	 * Create a rendering context from a resolved {@link ArtifactReferenceContext},
	 * reusing its interface assistant, cache, and current version rather than
	 * re-resolving the element.
	 *
	 * @param context a {@link ArtifactReferenceContext#isPresent() present}
	 * reference context.
	 * @param linkable {@literal true} to wrap non-current version rows in upgrade
	 * links.
	 * @return the documentation context.
	 */
	static DocumentationContext create(ArtifactReferenceContext context, boolean linkable) {
		return new DocumentationContext(context, linkable);
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

		appendVersionsTable(sb, artifactId, artifactContext.getCache().getReleases(artifactId), withIcons, formatter);

		appendSecurityAdvisories(sb);

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	/**
	 * Append a "Security advisories" section for the current version when the cache
	 * holds vulnerabilities for it. Renders nothing when no current version is
	 * resolved or vulnerabilities are absent or clean, so an unscanned dependency
	 * shows no section. Reads the cache only and never fetches.
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

			Formatter format = new Formatter(Locale.ROOT).format("%1.1f", vulnerability.getCvssScore());
			sb.append(", CVSS ").append(format);
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

		for (ReleaseGroup group : ReleaseGroup.group(artifactContext.getCache(), MAX_VERSIONS, property.artifacts())) {

			sb.append(group.renderHeader());
			appendVersionsTable(sb, group.artifactIds.getFirst(), group.releases(), withIcons, formatter);

			appendSecurityAdvisories(sb);
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	private String renderCurrentVersion(ArtifactVersion version) {
		return "<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.current-value"),
				StringUtil.escapeXmlEntities(interfaceAssistant.getDocumentationText(version)));
	}

	/**
	 * Appends the release table for the rows a {@link ReleaseDigest} selects.
	 * Renders nothing when {@code releases} is empty. When {@link #linkable} and
	 * icons are rendered, every non-current row wraps its age icon in an upgrade
	 * link handled by {@link DependencyUpgradeLinkHandler}. Rows the digest hides
	 * are summarized in a grayed note, so truncation is never silent.
	 */
	private void appendVersionsTable(StringBuilder sb, ArtifactId artifactId, Releases releases,
			boolean withIcons, ReleaseDateFormatter formatter) {

		if (releases.isEmpty()) {
			return;
		}

		ReleaseDigest digest = ReleaseDigest.of(releases, currentVersion, MAX_VERSIONS);

		if (!digest.rows().isEmpty()) {
			sb.append("<table>");
			for (Release release : digest.rows()) {
				sb.append("<tr>");
				sb.append(new DocumentedRelease(artifactContext, artifactId.toString(), release,
						interfaceAssistant, linkable, withIcons).render(formatter));
				sb.append("</tr>");
			}
			sb.append("</table>");
		}

		appendHiddenNote(sb, digest);
	}

	private static void appendHiddenNote(StringBuilder sb, ReleaseDigest digest) {

		List<String> fragments = new ArrayList<>(2);
		if (digest.hiddenPreviews() > 0) {
			fragments.add(MessageBundle.message("documentation.hidden.previews", digest.hiddenPreviews()));
		}
		if (digest.hiddenReleases() > 0) {
			fragments.add(MessageBundle.message("documentation.hidden.releases", digest.hiddenReleases()));
		}

		if (fragments.isEmpty()) {
			return;
		}

		sb.append("<p>")
				.append(DocumentationMarkup.GRAYED_START)
				.append(MessageBundle.message("documentation.hidden", String.join(", ", fragments)))
				.append(DocumentationMarkup.GRAYED_END)
				.append("</p>");
	}

	public @Nullable ArtifactVersion currentVersion() {
		return currentVersion;
	}

	/**
	 * Bounded selection of the release rows the popup renders for one artifact: the
	 * stable releases newer than the current version (capped at a limit), the
	 * newest preview, and the current version as the anchor row. Everything else is
	 * carried as hidden counts, keeping the popup an upgrade decision aid rather
	 * than a release changelog.
	 *
	 * @param rows the releases to render, in the authoritative newest-first order
	 * of {@link Releases}.
	 * @param hiddenPreviews the number of distinct preview releases not rendered.
	 * @param hiddenReleases the number of distinct stable releases not rendered.
	 */
	record ReleaseDigest(List<Release> rows, int hiddenPreviews, int hiddenReleases) {

		/**
		 * Select the rows to render from the given releases. Without a current version
		 * every stable release qualifies; otherwise only releases newer than current
		 * qualify, and the current version itself is kept as the anchor row. Duplicate
		 * versions are skipped entirely, matching the release table's previous
		 * de-duplication.
		 *
		 * @param releases the artifact's analyzed release history.
		 * @param currentVersion the declared version, or {@literal null} when
		 * unresolved.
		 * @param limit the maximum number of stable rows.
		 * @return the digest.
		 */
		static ReleaseDigest of(Releases releases, @Nullable ArtifactVersion currentVersion, int limit) {

			List<Release> rows = new ArrayList<>();
			Set<String> seen = new HashSet<>();
			boolean previewShown = false;
			int stableShown = 0;
			int hiddenPreviews = 0;
			int hiddenReleases = 0;

			for (Release release : releases) {

				if (!seen.add(release.unwrap().toString())) {
					continue;
				}

				if (currentVersion != null && release.getVersion().matches(currentVersion)) {
					rows.add(release);
					continue;
				}

				boolean newer = currentVersion == null || release.isNewer(currentVersion);

				if (release.isPreview()) {
					if (newer && !previewShown) {
						previewShown = true;
						rows.add(release);
					} else {
						hiddenPreviews++;
					}
					continue;
				}

				if (newer && stableShown < limit) {
					stableShown++;
					rows.add(release);
				} else {
					hiddenReleases++;
				}
			}

			return new ReleaseDigest(List.copyOf(rows), hiddenPreviews, hiddenReleases);
		}

	}

	record ReleaseGroup(List<ArtifactId> artifactIds, Releases releases) {

		public static Collection<ReleaseGroup> group(Cache cache, int limit, List<CachedArtifact> artifacts) {

			Map<Set<String>, ReleaseGroup> groups = new LinkedHashMap<>();
			for (CachedArtifact artifact : artifacts) {
				ArtifactId artifactId = artifact.toArtifactId();
				Releases releases = cache.getReleases(artifactId);
				Set<String> versionKeys = releaseVersionKeys(releases, limit);

				ReleaseGroup group = groups.computeIfAbsent(versionKeys, key -> new ReleaseGroup(new ArrayList<>(),
						releases));
				group.artifactIds().add(artifactId);
			}
			return groups.values();
		}

		private static Set<String> releaseVersionKeys(Releases releases, int limit) {

			Set<String> versions = new LinkedHashSet<>();
			for (Release release : releases) {
				versions.add(release.unwrap().toString());
				if (versions.size() >= limit) {
					break;
				}
			}
			return versions;
		}

		public String renderHeader() {
			return "<p>%s %s</p>".formatted(MessageBundle.message("documentation.property-for"),
					formatArtifactIds(artifactIds()));
		}

		private static String formatArtifactIds(List<ArtifactId> artifactIds) {

			StringBuilder sb = new StringBuilder();
			for (ArtifactId artifactId : artifactIds) {
				if (!sb.isEmpty()) {
					sb.append(", ");
				}
				sb.append("<code>");
				sb.append(StringUtil.escapeXmlEntities(artifactId.toString()));
				sb.append("</code>");
			}
			return sb.toString();
		}

	}

	static class DocumentedRelease {

		private final Release release;

		private final String key;

		private final InterfaceAssistant interfaceAssistant;

		private final boolean current;

		private final boolean linkable;

		private final @Nullable HtmlChunk firstColumnIcon;

		public DocumentedRelease(ArtifactReferenceContext artifactContext, String name, Release release,
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

		public StringBuilder render(ReleaseDateFormatter formatter) {

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
