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
package biz.paluch.dap.maven;

import biz.paluch.dap.ProjectBuildContext;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.Property;
import biz.paluch.dap.support.VersionDocumentationProviderSupport;

import java.awt.Image;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
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
 */
public class PropertyVersionDocumentationProvider extends VersionDocumentationProviderSupport
		implements PsiDocumentationTargetProvider {

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
		ProjectBuildContext buildContext = MavenProjectContext.of(project, element.getContainingFile());

		XmlTag propertyTag = PomUtil.findPropertyTag(element);
		if (propertyTag == null || !buildContext.isAvailable()) {
			return null;
		}

		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		ProjectState projectState = service.getProjectState(buildContext.getProjectId());
		Property property = projectState.findProperty(propertyTag.getLocalName());
		if (property == null) {
			return null;
		}

		return new PropertyVersionDocTarget(propertyTag, service.getCache(), property);
	}

	/**
	 * Documentation target for a Maven property tag that controls a cached dependency version.
	 */
	static class PropertyVersionDocTarget implements DocumentationTarget {

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

		/**
		 * Simplified content shown in the hover tooltip (no icons — plain HTML).
		 */
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

			return VersionDocumentationProviderSupport.buildHtmlBody(cache, property, propertyName, currentValue, iconImages);
		}
	}

}
