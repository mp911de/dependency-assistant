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
			String identifier = vulnerability.getCveId() != null ? vulnerability.getCveId()
					: vulnerability.getAdvisoryId();

			HtmlChunk.Element link = HtmlChunk.link(vulnerability.getSourceUrl(), identifier);
			sb.append(link);

			Formatter format = new Formatter(Locale.ROOT).format("%1.1f", vulnerability.getCvssScore());
			sb.append(", CVSS ").append(format);
			sb.append(" ").append(vulnerability.getSeverity().getLabel());
			sb.append(")</li>");
		}
		sb.append("</ul>");
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
	 * Appends a release table, rendering at most {@link #MAX_VERSIONS} distinct
	 * releases and skipping duplicates. Renders nothing when {@code releases} is
	 * empty. When {@link #linkable} and icons are rendered, every non-current row
	 * wraps its age icon in an upgrade link handled by
	 * {@link DependencyUpgradeLinkHandler}.
	 */
	private void appendVersionsTable(StringBuilder sb, ArtifactId artifactId, Releases releases,
			boolean withIcons, ReleaseDateFormatter formatter) {

		if (releases.isEmpty()) {
			return;
		}

		Set<String> seen = new HashSet<>();
		sb.append("<table>");
		int count = 0;
		for (Release release : releases) {
			DocumentedRelease documented = new DocumentedRelease(artifactContext, artifactId.toString(), release,
					interfaceAssistant, linkable, currentVersion, withIcons);

			if (!seen.add(documented.getKey())) {
				continue;
			}
			if (count++ >= MAX_VERSIONS) {
				break;
			}
			sb.append("<tr>");
			sb.append(documented.render(formatter));
			sb.append("</tr>");
		}
		sb.append("</table>");
	}

	public @Nullable ArtifactVersion currentVersion() {
		return currentVersion;
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
				InterfaceAssistant interfaceAssistant, boolean linkable, @Nullable ArtifactVersion currentVersion,
				boolean withIcons) {

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

		public String getKey() {
			return key;
		}

		public StringBuilder render(ReleaseDateFormatter formatter) {

			StringBuilder sb = new StringBuilder();
			if (firstColumnIcon != null) {
				sb.append(firstColumnIcon);
			}

			sb.append("<td>");
			if (linkable) {
				sb.append("<a href=\"").append(DependencyUpgradeLinkHandler.SCHEME)
						.append(key).append("\">");
			}
			boolean preview = release.isPreview();
			if (preview) {
				sb.append("<i>");
			}
			sb.append(renderVersion());
			if (preview) {
				sb.append("</i>");
			}
			if (linkable) {
				sb.append("</a>");
			}
			sb.append("</td><td>");
			if (release.releaseDate() != null) {
				sb.append(formatter.formatLong(release.releaseDate()));
			}
			sb.append("</td>");

			return sb;
		}

		private String renderVersion() {
			String text = StringUtil.escapeXmlEntities(interfaceAssistant.getDocumentationText(release.version()));
			return current ? "<b>" + text + "</b>" : text;
		}
	}

}
