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
package biz.paluch.dap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionOption;
import biz.paluch.dap.state.Artifact;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.Property;

import java.awt.Image;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import com.intellij.psi.xml.XmlTag;

/**
 * Contributes hover and Quick Documentation ({@code Ctrl+Q}) content for Maven {@code <properties>} tags whose name
 * maps to a known dependency artifact in the {@link Cache}.
 * <p>
 * Implemented via {@link PsiDocumentationTargetProvider} (IntelliJ 2022+ new documentation API) so that it runs in the
 * {@code psiDocumentationTargets()} pipeline independently of — and before — the legacy {@code DocumentationProvider}
 * chain where the Maven plugin's {@code MavenModelDocumentationProvider} takes precedence.
 * </p>
 * <p>
 * Shows the artifact the property controls together with the cached available versions, sorted newest-first. Each row
 * carries a {@link VersionAge} icon derived from the tag's current value: GA releases are additionally shown in bold.
 * The list is capped at {@value PropertyVersionDocTarget#MAX_VERSIONS} entries.
 * </p>
 */
public class PropertyVersionDocumentationProvider implements PsiDocumentationTargetProvider {

	/**
	 * Returns a {@link PropertyVersionDocTarget} when the element at the caret is inside a {@code <properties>} child tag
	 * in a Maven POM that maps to a cached artifact, {@code null} otherwise.
	 * <p>
	 * Returning {@code null} lets IntelliJ fall through to its own {@code PsiElementDocumentationTarget} (which
	 * eventually calls Maven's {@code MavenModelDocumentationProvider.generateDoc}), so documentation for all other POM
	 * elements continues to work normally.
	 * </p>
	 */
	@Override
	public @Nullable DocumentationTarget documentationTarget(@NotNull PsiElement targetElement,
			@Nullable PsiElement originalElement) {

		// Prefer the element directly at the caret; targetElement may be a resolved reference.
		PsiElement element = originalElement != null ? originalElement : targetElement;

		XmlTag propertyTag = XmlUtil.getPropertyTag(element);
		if (propertyTag == null) {
			return null;
		}

		Project project = element.getProject();
		Cache cache = DependencyAssistantService.getInstance(project).getState().getCache();
		Property property = cache.getProperty(propertyTag.getLocalName());
		if (property == null) {
			return null;
		}

		return new PropertyVersionDocTarget(propertyTag, property, cache);
	}

	/**
	 * Documentation target for a Maven property tag that controls a cached dependency version.
	 */
	static final class PropertyVersionDocTarget implements DocumentationTarget {

		static final int MAX_VERSIONS = 10;

		private final XmlTag propertyTag;
		private final Property property;
		private final Cache cache;

		PropertyVersionDocTarget(XmlTag propertyTag, Property property, Cache cache) {
			this.propertyTag = propertyTag;
			this.property = property;
			this.cache = cache;
		}

		@Override
		public @NotNull Pointer<? extends DocumentationTarget> createPointer() {
			SmartPsiElementPointer<XmlTag> psiPointer = SmartPointerManager.getInstance(propertyTag.getProject())
					.createSmartPsiElementPointer(propertyTag);
			Property capturedProperty = property;
			Cache capturedCache = cache;
			return () -> {
				XmlTag tag = psiPointer.getElement();
				return tag != null ? new PropertyVersionDocTarget(tag, capturedProperty, capturedCache) : null;
			};
		}

		@Override
		public @NotNull TargetPresentation computePresentation() {
			return TargetPresentation.builder(propertyTag.getLocalName()).presentation();
		}

		/**
		 * Full documentation shown in the Quick Documentation popup ({@code Ctrl+Q}). Version rows include
		 * {@link VersionAge} icons rendered relative to the tag's current value.
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

		/** Simplified content shown in the hover tooltip (no icons — plain HTML). */
		@Override
		public @Nullable String computeDocumentationHint() {
			return buildHtmlBody(null);
		}

		/**
		 * Builds the HTML body.
		 */
		private @Nullable String buildHtmlBody(@Nullable Map<String, Image> iconImages) {

			String propertyName = propertyTag.getLocalName();
			String currentValue = propertyTag.getValue().getText().trim();
			ArtifactVersion currentVersion = tryParseVersion(currentValue);

			StringBuilder sb = new StringBuilder();

			sb.append(DocumentationMarkup.DEFINITION_START);
			sb.append(propertyName);
			sb.append(DocumentationMarkup.DEFINITION_END);

			sb.append(DocumentationMarkup.CONTENT_START);

			if (!currentValue.isEmpty()) {
				sb.append("<p>Current value: <code>").append(currentValue).append("</code></p>");
			}

			boolean hasVersions = false;
			for (Artifact artifact : property.artifacts()) {
				ArtifactId artifactId = artifact.toArtifactId();
				sb.append("<p>Controls: <code>").append(artifactId).append("</code></p>");

				List<VersionOption> versions = sortedVersions(cache.getVersionOptions(artifactId, false));
				if (versions.isEmpty()) {
					continue;
				}

				hasVersions = true;
				sb.append("<table>");
				int count = 0;
				for (VersionOption v : versions) {
					if (count++ >= MAX_VERSIONS) {
						break;
					}
					sb.append("<tr>");

					if (iconImages != null && currentVersion != null) {
						VersionAge age = VersionAge.fromVersions(currentVersion, v.version());
						sb.append("<td>" + HtmlChunk.icon(age.getIconName(), age.getIcon()) + "</td>");
					}

					sb.append("<td>");
					boolean preview = v.isPreview();
					if (preview) {
						sb.append("<i>");
					}
					sb.append(v.version());
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

			return (hasVersions || !currentValue.isEmpty()) ? sb.toString() : null;
		}

		/**
		 * Tries to parse {@code versionString} as an {@link ArtifactVersion}. Returns {@code null} when the string is empty
		 * or unparseable (e.g. a Maven property placeholder like {@code ${revision}}).
		 */
		private static @Nullable ArtifactVersion tryParseVersion(String versionString) {
			if (versionString.isEmpty()) {
				return null;
			}
			try {
				return ArtifactVersion.of(versionString);
			} catch (Exception e) {
				return null;
			}
		}

		private static List<VersionOption> sortedVersions(List<VersionOption> versions) {
			List<VersionOption> sorted = new ArrayList<>(versions);
			sorted.sort(Comparator.reverseOrder());
			return sorted;
		}
	}
}
