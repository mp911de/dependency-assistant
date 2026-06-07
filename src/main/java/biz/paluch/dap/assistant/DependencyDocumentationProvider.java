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
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.ReleaseDateFormatter;
import biz.paluch.dap.support.VersionUpgradeLookup;
import biz.paluch.dap.util.PsiElements;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jspecify.annotations.Nullable;

/**
 * Provides Quick Documentation for supported dependency build files.
 *
 * @author Mark Paluch
 */
public class DependencyDocumentationProvider
		implements PsiDocumentationTargetProvider {

	static final int MAX_VERSIONS = 10;

	@Override
	public @Nullable DocumentationTarget documentationTarget(PsiElement element, @Nullable PsiElement originalElement) {
		return createTarget(PsiElements.unleaf(originalElement != null ? originalElement : element));
	}

	/**
	 * Resolve the given element into a documentation target, or {@literal null}
	 * when the element does not resolve to a dependency declaration.
	 * <p>Used both for the initial documentation request and for re-resolving the
	 * target after an upgrade has rewritten the version literal, so the re-rendered
	 * popup reflects the live declaration state.
	 */
	static @Nullable DocumentationTarget createTarget(PsiElement target) {

		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(target.getProject(),
				target.getContainingFile());
		if (context.isAbsent()) {
			return null;
		}

		VersionUpgradeLookup lookup = context.getLookup(target, target.getContainingFile().getVirtualFile());
		ArtifactReference artifactReference = lookup.resolveArtifactReference(target);
		if (!artifactReference.isResolved()) {
			return null;
		}

		Cache cache = lookup.getCache();
		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		ArtifactVersion currentVersion = declaration.isVersionDefined() ? declaration.getVersion() : null;
		boolean linkable = declaration.getVersionLiteral() != null;
		InterfaceAssistant interfaceAssistant = context.getInterfaceAssistant();

		if (declaration.getVersionSource() instanceof VersionSource.VersionProperty propertySource) {

			VersionProperty property = lookup.findProperty(propertySource.getProperty());
			if (property == null || property.artifacts().isEmpty()) {
				return null;
			}

			return new PropertyDocumentationTarget(target, cache, currentVersion, linkable, interfaceAssistant,
					property);
		}

		return new DependencyVersionTarget(target, cache, artifactReference.getArtifactId(), currentVersion, linkable,
				interfaceAssistant);
	}

	private abstract static class DocumentationTargetSupport implements DocumentationTarget, DependencyUpgradeTarget {

		final PsiElement target;

		final SmartPsiElementPointer<PsiElement> pointer;

		final Cache cache;

		final @Nullable ArtifactVersion currentVersion;

		final boolean linkable;

		final InterfaceAssistant interfaceAssistant;

		DocumentationTargetSupport(PsiElement target, Cache cache, @Nullable ArtifactVersion currentVersion,
				boolean linkable, InterfaceAssistant interfaceAssistant) {
			this.target = target;
			this.pointer = SmartPointerManager.createPointer(target);
			this.cache = cache;
			this.currentVersion = currentVersion;
			this.linkable = linkable;
			this.interfaceAssistant = interfaceAssistant;
		}

		@Override
		public TargetPresentation computePresentation() {
			return TargetPresentation.builder(target.toString()).presentation();
		}

		@Override
		public Project getProject() {
			return target.getProject();
		}

		@Override
		public final Pointer<? extends DocumentationTarget> createPointer() {
			SmartPsiElementPointer<PsiElement> pointer = this.pointer;
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null ? createTarget(element) : null;
			};
		}

		/**
		 * Re-resolve the live declaration and rewrite its version literal through the
		 * shared update path. Runs inside the write action opened by the link handler.
		 */
		@Override
		public void applyVersion(String version) {

			PsiElement element = pointer.getElement();
			if (element == null) {
				return;
			}

			ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(element);
			if (context.isAbsent()) {
				return;
			}

			VersionUpgradeLookup lookup = context.getLookup(element, element.getContainingFile().getVirtualFile());
			ArtifactReference reference = lookup.resolveArtifactReference(element);
			if (!reference.isResolved()) {
				return;
			}

			PsiElement versionLiteral = reference.getDeclaration().getVersionLiteral();
			if (versionLiteral == null) {
				return;
			}

			Release release = findRelease(lookup.getCache(), reference.getArtifactId(), version);
			context.applyUpdate(versionLiteral, DependencyUpdate.from(reference.toDependency(), release));
		}

		/**
		 * Full documentation shown in the Quick Documentation popup ({@code Ctrl+Q}).
		 * Version rows include {@link VersionAge} icons rendered relative to the tag's
		 * current value; upgradeable rows wrap the icon in an upgrade link.
		 */
		@Override
		public @Nullable DocumentationResult computeDocumentation() {

			Map<String, Image> iconImages = new LinkedHashMap<>();
			String html = buildHtmlBody(iconImages);
			if (html == null) {
				return null;
			}
			return DocumentationResult.documentation(html).images(iconImages);
		}

		/**
		 * Simplified content shown in the hover tooltip (no icons and no links - plain
		 * HTML).
		 */
		@Override
		public @Nullable String computeDocumentationHint() {
			return buildHtmlBody(null);
		}

		/**
		 * Builds the documentation HTML body, or {@literal null} when nothing can be
		 * rendered.
		 *
		 * @param iconImages sink for the {@link VersionAge} icons referenced by the
		 * HTML, or {@literal null} to render plain HTML without icons or links (hover
		 * hint).
		 * @return the HTML body, or {@literal null} if no documentation is available.
		 */
		protected abstract @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages);

	}

	private static class PropertyDocumentationTarget extends DocumentationTargetSupport {

		private final VersionProperty property;

		PropertyDocumentationTarget(PsiElement target, Cache cache, @Nullable ArtifactVersion currentVersion,
				boolean linkable, InterfaceAssistant interfaceAssistant, VersionProperty property) {
			super(target, cache, currentVersion, linkable, interfaceAssistant);
			this.property = property;
		}

		@Override
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return DependencyDocumentationProvider.buildHtmlBody(interfaceAssistant, cache, property, currentVersion,
					linkable, iconImages);
		}

	}

	/**
	 * Documentation target for a concrete dependency version.
	 */
	protected static class DependencyVersionTarget extends DocumentationTargetSupport {

		private final ArtifactId artifactId;

		DependencyVersionTarget(PsiElement target, Cache cache, ArtifactId artifactId,
				@Nullable ArtifactVersion currentVersion, boolean linkable, InterfaceAssistant interfaceAssistant) {
			super(target, cache, currentVersion, linkable, interfaceAssistant);
			this.artifactId = artifactId;
		}

		@Override
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return DependencyDocumentationProvider.buildHtmlBody(interfaceAssistant, cache, artifactId, currentVersion,
					linkable, iconImages);
		}

	}

	/**
	 * Builds the documentation HTML body for a version property, rendering one
	 * release table per artifact the property drives.
	 */
	static @Nullable String buildHtmlBody(InterfaceAssistant interfaceAssistant, Cache cache,
			VersionProperty property, @Nullable ArtifactVersion artifactVersion, boolean linkable,
			@Nullable Map<String, Image> iconImages) {

		if (property.artifacts().isEmpty()) {
			return null;
		}

		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();
		StringBuilder sb = new StringBuilder();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(property.name());
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		appendCurrentValue(sb, interfaceAssistant, artifactVersion);

		for (ReleaseGroup group : groupByVersions(cache, property.artifacts())) {

			sb.append("<p>%s %s</p>".formatted(MessageBundle.message("documentation.property-for"),
					formatArtifactIds(group.artifactIds())));

			appendVersionsTable(sb, group.releases(), artifactVersion, linkable, iconImages, formatter);
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	private static List<ReleaseGroup> groupByVersions(Cache cache, List<CachedArtifact> artifacts) {

		Map<Set<String>, ReleaseGroup> groups = new LinkedHashMap<>();
		for (CachedArtifact artifact : artifacts) {
			ArtifactId artifactId = artifact.toArtifactId();
			List<Release> releases = cache.getReleases(artifactId);
			Set<String> versionKeys = releaseVersionKeys(releases);

			ReleaseGroup group = groups.computeIfAbsent(versionKeys, key -> new ReleaseGroup(new ArrayList<>(),
					releases));
			group.artifactIds().add(artifactId);
		}
		return List.copyOf(groups.values());
	}

	private static Set<String> releaseVersionKeys(List<Release> releases) {

		Set<String> versions = new LinkedHashSet<>();
		for (Release release : releases) {
			versions.add(releaseVersionKey(release));
		}
		return versions;
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

	/**
	 * Builds the documentation HTML body for a single concrete artifact.
	 */
	protected static String buildHtmlBody(InterfaceAssistant interfaceAssistant, Cache cache, ArtifactId artifactId,
			@Nullable ArtifactVersion artifactVersion, boolean linkable,
			@Nullable Map<String, Image> iconImages) {

		StringBuilder sb = new StringBuilder();
		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(artifactId);
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		appendCurrentValue(sb, interfaceAssistant, artifactVersion);

		appendVersionsTable(sb, cache.getReleases(artifactId), artifactVersion, linkable, iconImages, formatter);

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	private static void appendCurrentValue(StringBuilder sb, InterfaceAssistant interfaceAssistant,
			@Nullable ArtifactVersion artifactVersion) {

		if (artifactVersion == null) {
			return;
		}

		sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.current-value"),
				StringUtil.escapeXmlEntities(interfaceAssistant.getDocumentationText(artifactVersion))));
	}

	/**
	 * Appends a release table for a single artifact, rendering at most
	 * {@link #MAX_VERSIONS} distinct versions and skipping duplicates. Renders
	 * nothing when {@code versions} is empty. When {@code linkable} and icons are
	 * rendered, every non-current row wraps its age icon in an upgrade link handled
	 * by {@link DependencyUpgradeLinkHandler}.
	 */
	private static void appendVersionsTable(StringBuilder sb, List<Release> versions,
			@Nullable ArtifactVersion artifactVersion, boolean linkable, @Nullable Map<String, Image> iconImages,
			ReleaseDateFormatter formatter) {

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

			boolean current = v.version().equals(artifactVersion);
			if (iconImages != null && artifactVersion != null) {

				VersionAge age = VersionAge.between(artifactVersion, v.getVersion());
				HtmlChunk icon = HtmlChunk.icon(age.getIconName(), age.getIcon());
				HtmlChunk cell = linkable && !current
						? HtmlChunk.tag("a").attr("href", DependencyUpgradeLinkHandler.SCHEME + versionKey)
								.attr("title", MessageBundle.message("documentation.upgrade-to", versionKey))
								.child(icon)
						: icon;
				sb.append("<td>").append(cell).append("</td>");
			}

			sb.append("<td>");
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

	private static Release findRelease(Cache cache, ArtifactId artifactId, String version) {

		for (Release release : cache.getReleases(artifactId)) {
			if (releaseVersionKey(release).equals(version)) {
				return release;
			}
		}
		return Release.of(ArtifactVersion.of(version));
	}

	private record ReleaseGroup(List<ArtifactId> artifactIds, List<Release> releases) {
	}

}
