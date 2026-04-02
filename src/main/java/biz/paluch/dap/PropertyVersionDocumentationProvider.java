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
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.Property;

import java.awt.Image;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jetbrains.annotations.Nullable;

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
import com.intellij.psi.xml.XmlTag;

/**
 * Contributes hover and Quick Documentation ({@code Ctrl+Q}) content for Maven {@code <properties>} tags whose name
 * maps to a known dependency artifact in the {@link Cache}.
 * <p>
 * Implemented via {@link PsiDocumentationTargetProvider} (IntelliJ 2022+ new documentation API) so that it runs in the
 * {@code psiDocumentationTargets()} pipeline independently of — and before — the legacy {@code DocumentationProvider}
 * chain where the Maven plugin's {@code MavenModelDocumentationProvider} takes precedence.
 * <p>
 * Shows the artifact the property controls together with the cached available versions, sorted newest-first. Each row
 * carries a {@link VersionAge} icon derived from the tag's current value: GA releases are additionally shown in bold.
 * The list is capped at {@value PropertyVersionDocTarget#MAX_VERSIONS} entries.
 */
public class PropertyVersionDocumentationProvider implements PsiDocumentationTargetProvider {

	/**
	 * Returns a {@link PropertyVersionDocTarget} when the element at the caret is inside a {@code properties} child tag
	 * in a Maven POM that maps to a cached artifact, {@code null} otherwise to use
	 * {@code MavenModelDocumentationProvider}.
	 */
	@Override
	public @Nullable DocumentationTarget documentationTarget(PsiElement targetElement,
			@Nullable PsiElement originalElement) {

		// Prefer the element directly at the caret; targetElement may be a resolved reference.
		PsiElement element = originalElement != null ? originalElement : targetElement;
		Project project = element.getProject();
		MavenContext mavenContext = MavenContext.of(project, element.getContainingFile());

		XmlTag propertyTag = PomUtil.findPropertyTag(element);
		if (propertyTag == null || !mavenContext.isAvailable()) {
			return null;
		}

		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		ProjectState projectState = service.getProjectState(mavenContext.getMavenId());
		Property property = projectState.getProperty(propertyTag.getLocalName());
		if (property == null) {
			return null;
		}

		return new PropertyVersionDocTarget(propertyTag, service.getCache(), property);
	}

	/**
	 * Documentation target for a Maven property tag that controls a cached dependency version.
	 */
	static class PropertyVersionDocTarget implements DocumentationTarget {

		static final int MAX_VERSIONS = 10;

		private final XmlTag propertyTag;
		private final Cache cache;
		private final Property property;

		PropertyVersionDocTarget(XmlTag propertyTag, Cache cache, Property property) {
			this.propertyTag = propertyTag;
			this.property = property;
			this.cache = cache;
		}

		@Override
		public Pointer<? extends DocumentationTarget> createPointer() {

			SmartPsiElementPointer<XmlTag> psiPointer = SmartPointerManager.getInstance(propertyTag.getProject())
					.createSmartPsiElementPointer(propertyTag);

			return () -> {
				XmlTag tag = psiPointer.getElement();
				return tag != null ? new PropertyVersionDocTarget(tag, cache, property) : null;
			};
		}

		@Override
		public TargetPresentation computePresentation() {
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

			if (StringUtil.isEmpty(currentValue) || property.artifacts().isEmpty()) {
				return null;
			}

			ArtifactVersion currentVersion = tryParseVersion(currentValue);

			StringBuilder sb = new StringBuilder();

			sb.append(DocumentationMarkup.DEFINITION_START);
			sb.append(propertyName);
			sb.append(DocumentationMarkup.DEFINITION_END);

			sb.append(DocumentationMarkup.CONTENT_START);

			if (!currentValue.isEmpty()) {
				sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.current-value"),
						StringUtil.escapeXmlEntities(currentValue)));
			}

			for (CachedArtifact artifact : property.artifacts()) {

				ArtifactId artifactId = artifact.toArtifactId();
				sb.append("<p>%s: <code>%s</code></p>".formatted(MessageBundle.message("documentation.controls"), artifactId));

				List<Release> versions = cache.getReleases(artifactId, false);
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

					if (iconImages != null && currentVersion != null) {
						VersionAge age = VersionAge.fromVersions(currentVersion, v);
						sb.append("<td>" + HtmlChunk.icon(age.getIconName(), age.getIcon()) + "</td>");
					}

					sb.append("<td>");
					boolean preview = v.isPreview();
					boolean current = v.version().equals(currentVersion);
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
	}
}
