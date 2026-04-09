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
package biz.paluch.dap.gradle;

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

import com.intellij.lang.properties.IProperty;
import com.intellij.model.Pointer;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationResult;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.platform.backend.presentation.TargetPresentation;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;

/**
 * Provides Quick Documentation ({@code Ctrl+Q}) for Gradle property values in {@code gradle.properties} and version
 * references in {@code libs.versions.toml} that map to known dependency artifacts.
 *
 * @author Mark Paluch
 */
public class PropertyVersionDocumentationProvider extends VersionDocumentationProviderSupport
		implements PsiDocumentationTargetProvider {

	@Override
	public @Nullable DocumentationTarget documentationTarget(PsiElement element, @Nullable PsiElement originalElement) {

		PsiElement target = originalElement != null ? originalElement : element;
		Project project = target.getProject();

		if (!GradleUtils.isGradleFile(target.getContainingFile())) {
			return null;
		}

		GradleProjectContext buildContext = GradleProjectContext.of(project, target.getContainingFile());
		if (!buildContext.isAvailable()) {
			return null;
		}

		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		ProjectState projectState = service.getProjectState(buildContext.getProjectId());
		Cache cache = service.getCache();

		// gradle.properties: property value element
		if (GradleUtils.isGradlePropertiesFile(target.getContainingFile().getVirtualFile())) {

			IProperty property = findParentProperty(target);
			if (property == null) {
				return null;
			}
			String key = property.getKey();
			Property cachedProp = key != null ? projectState.findProperty(key) : null;
			if (cachedProp == null || cachedProp.artifacts().isEmpty()) {
				return null;
			}
			return new GradleVersionDocTarget(target, cache, property.getValue(), cachedProp);
		}

		return null;
	}

	@SuppressWarnings("unchecked")
	private static <T> @Nullable T findParent(PsiElement element, Class<T> type) {
		PsiElement parent = element.getParent();
		while (parent != null && !(parent instanceof PsiFile)) {
			if (type.isInstance(parent)) {
				return (T) parent;
			}
			parent = parent.getParent();
		}
		return null;
	}

	private static @Nullable IProperty findParentProperty(PsiElement element) {
		return findParent(element, IProperty.class);
	}

	private static class GradleVersionDocTarget implements DocumentationTarget {

		private final PsiElement target;
		private final Cache cache;
		private final @Nullable String currentValue;
		private final Property property;

		public GradleVersionDocTarget(PsiElement target, Cache cache, @Nullable String currentValue, Property property) {
			this.target = target;
			this.cache = cache;
			this.currentValue = currentValue;
			this.property = property;
		}

		@Override
		public Pointer<? extends DocumentationTarget> createPointer() {

			SmartPsiElementPointer<PsiElement> pointer = SmartPointerManager.createPointer(target);
			return () -> {
				PsiElement element = pointer.getElement();
				return element != null ? new GradleVersionDocTarget(element, cache, currentValue, property) : null;
			};
		}

		@Override
		public TargetPresentation computePresentation() {
			return TargetPresentation.builder(target.toString()).presentation();
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
		private @Nullable String buildHtmlBody(@org.jetbrains.annotations.Nullable Map<String, Image> iconImages) {
			return PropertyVersionDocumentationProvider.buildHtmlBody(cache, property, property.name(), currentValue,
					iconImages);
		}

	}

}
