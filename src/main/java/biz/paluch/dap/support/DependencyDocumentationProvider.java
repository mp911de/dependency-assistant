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

package biz.paluch.dap.support;

import java.awt.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.VersionProperty;
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

		PsiElement target = originalElement != null ? originalElement : element;
		Project project = target.getProject();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project,
				target.getContainingFile());
		if (context == null) {
			return null;
		}

		VersionUpgradeLookupSupport lookup = context.getLookup(element);
		ArtifactReference artifactReference = lookup.resolveArtifactReference(element);
		if (!artifactReference.isResolved()) {
			return null;
		}

		Cache cache = lookup.getCache();

		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		ArtifactVersion currentVersion = declaration.isVersionDefined() ? declaration.getVersion() : null;
		if (lookup.hasCachedState()
				&& declaration
						.getVersionSource() instanceof VersionSource.VersionProperty propertySource) {

			ProjectState projectState = lookup.getProjectState();
			VersionProperty property = projectState.findProperty(propertySource.getProperty());
			if (property == null || property.artifacts().isEmpty()) {
				return null;
			}

			return new PropertyDocumentationTarget(target, cache, currentVersion,
					element.getText(), property);
		}

		return new DependencyVersionTarget(target, cache, artifactReference.getArtifactId(),
				currentVersion, element.getText());
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

		public PropertyDocumentationTarget(PsiElement target, Cache cache, @Nullable ArtifactVersion currentVersion,
				@Nullable String currentValue, VersionProperty property) {
			super(target, cache, currentVersion, currentValue);
			this.property = property;
		}

		@Override
		public Pointer<? extends DocumentationTarget> createPointer() {

			SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(target);
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null
						? new PropertyDocumentationTarget(element, cache, currentVersion, currentValue, property)
						: null;
			};
		}

		/**
		 * Builds the HTML body.
		 */
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return DependencyDocumentationProvider.buildHtmlBody(cache, property, currentVersion, iconImages);
		}

	}

	/**
	 * Documentation target for a concrete dependency version.
	 */
	protected static class DependencyVersionTarget extends DocumentationTargetSupport {

		private final ArtifactId artifactId;

		public DependencyVersionTarget(PsiElement target, Cache cache, ArtifactId artifactId,
				@Nullable ArtifactVersion currentVersion,
				@Nullable String currentValue) {
			super(target, cache, currentVersion, currentValue);
			this.artifactId = artifactId;
		}

		@Override
		public Pointer<? extends DocumentationTarget> createPointer() {

			SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(target);
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null
						? new DependencyVersionTarget(element, cache, artifactId, currentVersion, currentValue)
						: null;
			};
		}

		/**
		 * Builds the HTML body.
		 */
		protected @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {
			return DependencyDocumentationProvider.buildHtmlBody(cache, artifactId, currentVersion, iconImages);
		}

	}

	/**
	 * Builds the HTML body.
	 */
	private static @Nullable String buildHtmlBody(Cache cache, VersionProperty property,
			@Nullable ArtifactVersion artifactVersion, @Nullable Map<String, Image> iconImages) {

		if (property.artifacts().isEmpty()) {
			return null;
		}

		StringBuilder sb = new StringBuilder();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(property.name());
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		if (artifactVersion != null) {
			sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.current-value"),
					StringUtil.escapeXmlEntities(artifactVersion.toString())));
		}

		for (CachedArtifact artifact : property.artifacts()) {

			ArtifactId artifactId = artifact.toArtifactId();
			sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.controls"),
					artifactId));

			java.util.List<Release> versions = cache.getReleases(artifactId, false);
			if (versions.isEmpty()) {
				continue;
			}

			sb.append("<table>");
			int count = 0;
			for (Release v : versions) {
				if (count++ >= MAX_VERSIONS) {
					break;
				}
				sb.append("<tr>");

				if (iconImages != null && artifactVersion != null) {
					VersionAge age = VersionAge.fromVersions(artifactVersion, v);
					sb.append("<td>" + HtmlChunk.icon(age.getIconName(), age.getIcon()) + "</td>");
				}

				sb.append("<td>");
				boolean preview = v.isPreview();
				boolean current = v.version().equals(artifactVersion);
				if (preview) {
					sb.append("<i>");
				}
				if (current) {
					sb.append("<b>");
				}
				sb.append(v.version());
				if (current) {
					sb.append("</b>");
				}
				if (preview) {
					sb.append("</i>");
				}
				sb.append("</td><td>");
				if (v.releaseDate() != null) {
					sb.append(v.releaseDate().toLocalDate());
				}
				sb.append("</td></tr>");
			}
			sb.append("</table>");
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

	/**
	 * Builds the HTML body.
	 */
	protected static String buildHtmlBody(Cache cache, ArtifactId artifactId, @Nullable ArtifactVersion artifactVersion,
			@Nullable Map<String, Image> iconImages) {

		StringBuilder sb = new StringBuilder();

		sb.append(DocumentationMarkup.DEFINITION_START);
		sb.append(artifactId);
		sb.append(DocumentationMarkup.DEFINITION_END);

		sb.append(DocumentationMarkup.CONTENT_START);

		if (artifactVersion != null) {
			sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.current-value"),
					StringUtil.escapeXmlEntities(artifactVersion.toString())));
		}

		List<Release> versions = cache.getReleases(artifactId, false);
		if (!versions.isEmpty()) {
			sb.append("<table>");
			int count = 0;
			for (Release v : versions) {
				if (count++ >= MAX_VERSIONS) {
					break;
				}
				sb.append("<tr>");

				if (iconImages != null && artifactVersion != null) {
					VersionAge age = VersionAge.fromVersions(artifactVersion, v);
					sb.append("<td>" + HtmlChunk.icon(age.getIconName(), age.getIcon()) + "</td>");
				}

				sb.append("<td>");
				boolean preview = v.isPreview();
				boolean current = v.version().equals(artifactVersion);
				if (preview) {
					sb.append("<i>");
				}
				if (current) {
					sb.append("<b>");
				}
				sb.append(v.version());
				if (current) {
					sb.append("</b>");
				}
				if (preview) {
					sb.append("</i>");
				}
				sb.append("</td><td>");
				if (v.releaseDate() != null) {
					sb.append(v.releaseDate().toLocalDate());
				}
				sb.append("</td></tr>");
			}
			sb.append("</table>");
		}

		sb.append(DocumentationMarkup.CONTENT_END);

		return sb.toString();
	}

}
