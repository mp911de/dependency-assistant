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

import java.awt.Image;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.ReleaseDateFormatter;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import org.jspecify.annotations.Nullable;

/**
 * Immutable rendering context for dependency Quick Documentation.
 *
 * <p>Bundles everything needed to render a documentation body besides the PSI
 * element itself: the release {@link Cache}, the declaration's current version,
 * whether upgrades may be offered as links, and the {@link InterfaceAssistant}
 * that formats versions. It owns HTML construction for both single-artifact and
 * version-property documentation through {@link #render(ArtifactId, Map)} and
 * {@link #render(VersionProperty, Map)}.
 *
 * @param interfaceAssistant the UI support used to format versions; must not be
 * {@literal null}.
 * @param cache the release metadata cache to read versions from; must not be
 * {@literal null}.
 * @param currentVersion the declaration's current version, or {@literal null}
 * when no version is resolved.
 * @param linkable whether release rows may carry an upgrade link;
 * {@literal true} only when the declaration exposes a writable version literal.
 * @author Mark Paluch
 */
record DocumentationContext(InterfaceAssistant interfaceAssistant, Cache cache,
		@Nullable ArtifactVersion currentVersion, boolean linkable) {

	private static final int MAX_VERSIONS = 10;

	/**
	 * Render the documentation body for a single concrete artifact.
	 * @param artifactId the artifact to document; must not be {@literal null}.
	 * @param iconImages sink for the {@link VersionAge} icons referenced by the
	 * HTML, or {@literal null} to render plain HTML without icons or links (hover
	 * hint).
	 * @return the HTML body.
	 */
	String render(ArtifactId artifactId, @Nullable Map<String, Image> iconImages) {

		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();
		StringBuilder sb = new StringBuilder();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(artifactId);
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		if (currentVersion != null) {
			sb.append(renderCurrentVersion(currentVersion));
		}

		appendVersionsTable(sb, artifactId, cache.getReleases(artifactId), iconImages, formatter);

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	/**
	 * Render the documentation body for a version property, with one release table
	 * per group of artifacts sharing the same available versions.
	 * @param property the version property to document; must not be
	 * {@literal null}.
	 * @param iconImages sink for the {@link VersionAge} icons referenced by the
	 * HTML, or {@literal null} to render plain HTML without icons or links (hover
	 * hint).
	 * @return the HTML body, or {@literal null} if the property drives no
	 * artifacts.
	 */
	@Nullable
	String render(VersionProperty property, @Nullable Map<String, Image> iconImages) {

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

		for (ReleaseGroup group : ReleaseGroup.group(cache, property.artifacts())) {

			sb.append(group.renderHeader());

			appendVersionsTable(sb, group.artifactIds.getFirst(), group.releases(), iconImages, formatter);
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	/**
	 * Locate the release matching the given version string, falling back to a
	 * version-only release when the cache holds no matching entry.
	 * @param cache the release cache to search; must not be {@literal null}.
	 * @param artifactId the artifact whose releases to search; must not be
	 * {@literal null}.
	 * @param version the canonical version string to match; must not be
	 * {@literal null}.
	 * @return the matching release, never {@literal null}.
	 */
	static Release findRelease(Cache cache, ArtifactId artifactId, String version) {

		for (Release release : cache.getReleases(artifactId)) {
			if (releaseVersionKey(release).equals(version)) {
				return release;
			}
		}
		return Release.of(ArtifactVersion.of(version));
	}

	private String renderCurrentVersion(ArtifactVersion version) {
		return "<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.current-value"),
				StringUtil.escapeXmlEntities(interfaceAssistant.getDocumentationText(version)));
	}

	/**
	 * Appends a release table, rendering at most {@link #MAX_VERSIONS} distinct
	 * versions and skipping duplicates. Renders nothing when {@code versions} is
	 * empty. When {@link #linkable} and icons are rendered, every non-current row
	 * wraps its age icon in an upgrade link handled by
	 * {@link DependencyUpgradeLinkHandler}.
	 */
	private void appendVersionsTable(StringBuilder sb, ArtifactId artifactId, List<Release> versions,
			@Nullable Map<String, Image> iconImages, ReleaseDateFormatter formatter) {

		if (versions.isEmpty()) {
			return;
		}

		Set<String> seen = new HashSet<>();
		sb.append("<table>");
		int count = 0;
		for (Release v : versions) {

			String versionKey = releaseVersionKey(v);
			if (!seen.add(versionKey)) {
				continue;
			}
			if (count++ >= MAX_VERSIONS) {
				break;
			}
			sb.append("<tr>");

			boolean current = v.version().equals(currentVersion);
			if (iconImages != null && currentVersion != null) {

				VersionAge age = VersionAge.between(currentVersion, v.getVersion());
				HtmlChunk icon = HtmlChunk.icon(age.getIconName(), age.getIcon());
				HtmlChunk cell = linkable && !current
						? HtmlChunk.tag("a").attr("href", DependencyUpgradeLinkHandler.SCHEME + versionKey)
								.attr("title",
										MessageBundle.message("documentation.upgrade-to", artifactId, versionKey))
								.child(icon)
						: icon;
				sb.append("<td>").append(cell).append("</td>");
			}

			sb.append("<td>");
			if (linkable && !current) {
				sb.append("<a href=\"").append(DependencyUpgradeLinkHandler.SCHEME).append(versionKey).append("\">");
			}
			boolean preview = v.isPreview();
			if (preview) {
				sb.append("<i>");
			}
			if (current) {
				sb.append("<b>");
			}
			sb.append(versionKey);
			if (current) {
				sb.append("</b>");
			}
			if (preview) {
				sb.append("</i>");
			}
			if (linkable && !current) {
				sb.append("</a>");
			}
			sb.append("</td><td>");
			if (v.releaseDate() != null) {
				sb.append(formatter.formatLong(v.releaseDate()));
			}
			sb.append("</td></tr>");
		}
		sb.append("</table>");
	}

	private static String releaseVersionKey(Release release) {
		return release.version().getVersion().getVersion().toString();
	}

	private record ReleaseGroup(List<ArtifactId> artifactIds, List<Release> releases) {

		public static Collection<ReleaseGroup> group(Cache cache, List<CachedArtifact> artifacts) {

			Map<Set<String>, ReleaseGroup> groups = new LinkedHashMap<>();
			for (CachedArtifact artifact : artifacts) {
				ArtifactId artifactId = artifact.toArtifactId();
				List<Release> releases = cache.getReleases(artifactId);
				Set<String> versionKeys = releaseVersionKeys(releases);

				ReleaseGroup group = groups.computeIfAbsent(versionKeys, key -> new ReleaseGroup(new ArrayList<>(),
						releases));
				group.artifactIds().add(artifactId);
			}
			return groups.values();
		}

		private static Set<String> releaseVersionKeys(List<Release> releases) {

			Set<String> versions = new LinkedHashSet<>();
			for (Release release : releases) {
				versions.add(releaseVersionKey(release));
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

}
