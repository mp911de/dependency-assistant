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
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
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

	static final int MAX_VERSIONS = 23;

	@Override
	public @Nullable DocumentationTarget documentationTarget(PsiElement element, @Nullable PsiElement originalElement) {

		PsiElement target = PsiElements.unleaf(originalElement != null ? originalElement : element);
		Project project = target.getProject();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project,
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
		if (declaration
				.getVersionSource() instanceof VersionSource.VersionProperty propertySource) {

			VersionProperty property = lookup.findProperty(propertySource.getProperty());
			if (property == null || property.artifacts().isEmpty()) {
				return null;
			}

			return new PropertyDocumentationTarget(target, cache, currentVersion,
					element.getText(), property, context.getInterfaceAssistant());
		}

		return new DependencyVersionTarget(target, cache, artifactReference.getArtifactId(),
				currentVersion, element.getText(), context.getInterfaceAssistant());
	}

	private abstract static class DocumentationTargetSupport implements DocumentationTarget {

		final PsiElement target;

		final Cache cache;

		final @Nullable ArtifactVersion currentVersion;

		final @Nullable String currentValue;

		public DocumentationTargetSupport(PsiElement target, Cache cache, @Nullable ArtifactVersion currentVersion,
				@Nullable String currentValue) {
			this.target = target;
			this.cache = cache;
			this.currentVersion = currentVersion;
			this.currentValue = currentValue;
		}

		@Override
		public TargetPresentation computePresentation() {
			return TargetPresentation.builder(target.toString()).presentation();
		}

		/**
		 * Full documentation shown in the Quick Documentation popup ({@code Ctrl+Q}).
		 * Version rows include {@link VersionAge} icons rendered relative to the tag's
		 * current value.
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
		 * Simplified content shown in the hover tooltip (no icons - plain HTML).
		 */
		@Override
		public @Nullable String computeDocumentationHint() {
			return buildHtmlBody(null);
		}

		/**
		 * Builds the HTML body.
		 */
		protected abstract @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages);

	}

	private static class PropertyDocumentationTarget extends DocumentationTargetSupport {

		private final VersionProperty property;

		private final InterfaceAssistant interfaceAssistant;

		private final SmartPsiElementPointer<PsiElement> pointer;

		public PropertyDocumentationTarget(PsiElement target, Cache cache, @Nullable ArtifactVersion currentVersion,
				@Nullable String currentValue, VersionProperty property, InterfaceAssistant interfaceAssistant) {
			super(target, cache, currentVersion, currentValue);
			this.property = property;
			this.interfaceAssistant = interfaceAssistant;
			this.pointer = SmartPointerManager.createPointer(target);
		}

		@Override
		public Pointer<? extends DocumentationTarget> createPointer() {
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null
						? new PropertyDocumentationTarget(element, cache, currentVersion, currentValue, property,
								interfaceAssistant)
						: null;
			};
		}

		/**
		 * Builds the HTML body.
		 */
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return DependencyDocumentationProvider.buildHtmlBody(interfaceAssistant, cache, property, currentVersion,
					iconImages);
		}

	}

	/**
	 * Documentation target for a concrete dependency version.
	 */
	protected static class DependencyVersionTarget extends DocumentationTargetSupport {

		private final ArtifactId artifactId;

		private final InterfaceAssistant interfaceAssistant;

		private final SmartPsiElementPointer<PsiElement> pointer;

		public DependencyVersionTarget(PsiElement target, Cache cache, ArtifactId artifactId,
				@Nullable ArtifactVersion currentVersion, @Nullable String currentValue,
				InterfaceAssistant interfaceAssistant) {
			super(target, cache, currentVersion, currentValue);
			this.artifactId = artifactId;
			this.interfaceAssistant = interfaceAssistant;
			this.pointer = SmartPointerManager.createPointer(target);
		}

		@Override
		public Pointer<? extends DocumentationTarget> createPointer() {
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null
						? new DependencyVersionTarget(element, cache, artifactId, currentVersion, currentValue,
								interfaceAssistant)
						: null;
			};
		}

		/**
		 * Builds the HTML body.
		 */
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return DependencyDocumentationProvider.buildHtmlBody(interfaceAssistant, cache, artifactId, currentVersion,
					iconImages);
		}

	}

	/**
	 * Builds the HTML body for a version property, listing the releases of every
	 * artifact the property drives.
	 */
	private static @Nullable String buildHtmlBody(InterfaceAssistant interfaceAssistant, Cache cache,
			VersionProperty property, @Nullable ArtifactVersion artifactVersion,
			@Nullable Map<String, Image> iconImages) {

		if (property.artifacts().isEmpty()) {
			return null;
		}

		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();
		StringBuilder sb = new StringBuilder();

		appendDefinition(sb, property.name());

		sb.append(DocumentationMarkup.CONTENT_START);
		appendCurrentValue(sb, interfaceAssistant, artifactVersion);

		for (CachedArtifact artifact : property.artifacts()) {

			ArtifactId artifactId = artifact.toArtifactId();
			sb.append("<p>%s <code>%s</code></p>".formatted(MessageBundle.message("documentation.property-for"),
					artifactId));
			appendVersionsTable(sb, cache.getReleases(artifactId), artifactVersion, iconImages, formatter);
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	/**
	 * Builds the HTML body for a concrete artifact.
	 */
	protected static String buildHtmlBody(InterfaceAssistant interfaceAssistant, Cache cache, ArtifactId artifactId,
			@Nullable ArtifactVersion artifactVersion, @Nullable Map<String, Image> iconImages) {

		ReleaseDateFormatter formatter = ReleaseDateFormatter.create();
		StringBuilder sb = new StringBuilder();

		appendDefinition(sb, artifactId);

		sb.append(DocumentationMarkup.CONTENT_START);
		appendCurrentValue(sb, interfaceAssistant, artifactVersion);
		appendVersionsTable(sb, cache.getReleases(artifactId), artifactVersion, iconImages, formatter);

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	private static void appendDefinition(StringBuilder sb, Object header) {

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(header);
		sb.append(DocumentationMarkup.DEFINITION_END);
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
	 * Renders a table of releases, de-duplicated and with preview runs condensed by
	 * {@link ReleaseIterator}, capped at {@link #MAX_VERSIONS} rows. Renders
	 * nothing when there are no releases.
	 */
	private static void appendVersionsTable(StringBuilder sb, List<Release> versions,
			@Nullable ArtifactVersion artifactVersion, @Nullable Map<String, Image> iconImages,
			ReleaseDateFormatter formatter) {

		if (versions.isEmpty()) {
			return;
		}

		sb.append("<table>");
		Iterator<Release> iterator = versions.iterator();
		int count = 0;
		while (iterator.hasNext() && count < MAX_VERSIONS) {
			appendVersionRow(sb, iterator.next(), artifactVersion, iconImages, formatter);
			count++;
		}
		sb.append("</table>");
	}

	private static void appendVersionRow(StringBuilder sb, Release release,
			@Nullable ArtifactVersion artifactVersion, @Nullable Map<String, Image> iconImages,
			ReleaseDateFormatter formatter) {

		sb.append("<tr>");

		if (iconImages != null && artifactVersion != null) {
			VersionAge age = VersionAge.between(artifactVersion, release.getVersion());
			sb.append("<td>").append(HtmlChunk.icon(age.getIconName(), age.getIcon())).append("</td>");
		}

		sb.append("<td>");
		boolean preview = release.isPreview();
		boolean current = release.version().equals(artifactVersion);
		if (preview) {
			sb.append("<i>");
		}
		if (current) {
			sb.append("<b>");
		}
		sb.append(ReleaseIterator.versionString(release));
		if (current) {
			sb.append("</b>");
		}
		if (preview) {
			sb.append("</i>");
		}
		sb.append("</td><td>");
		if (release.releaseDate() != null) {
			sb.append(formatter.formatLong(release.releaseDate()));
		}
		sb.append("</td></tr>");
	}

}
