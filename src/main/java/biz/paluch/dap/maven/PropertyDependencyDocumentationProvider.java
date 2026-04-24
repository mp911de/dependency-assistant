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
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.Property;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencyDocumentationProviderSupport;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlTag;
import org.jspecify.annotations.Nullable;

/**
 * Contributes hover and Quick Documentation ({@code Ctrl+Q}) content for Maven
 * {@code <properties>} tags whose name maps to a known dependency artifact in
 * the {@link Cache}.
 */
public class PropertyDependencyDocumentationProvider extends DependencyDocumentationProviderSupport
		implements PsiDocumentationTargetProvider {

	/**
	 * Returns a {@link PropertyDocumentationTarget} when the element at the caret
	 * is inside a {@code properties} child tag in a Maven POM that maps to a cached
	 * artifact, {@code null} otherwise to use
	 * {@code MavenModelDocumentationProvider}.
	 */
	@Override
	public @Nullable DocumentationTarget documentationTarget(PsiElement targetElement,
			@Nullable PsiElement originalElement) {

		// Prefer the element directly at the caret; targetElement may be a resolved
		// reference.
		PsiElement element = originalElement != null ? originalElement : targetElement;
		Project project = element.getProject();
		ProjectBuildContext buildContext = MavenProjectContext.of(project, element.getContainingFile());

		XmlTag propertyTag = PomUtil.findPropertyTag(element);
		if (propertyTag == null || !buildContext.isAvailable()) {
			return null;
		}
		VersionUpgradeLookupService lookupService = VersionUpgradeLookupService.create(element);
		ArtifactReference artifactReference = lookupService.resolveArtifactReference(element);

		if (!artifactReference.isResolved()) {
			return null;
		}

		ProjectState projectState = lookupService.getProjectState();
		Property property = projectState.findProperty(propertyTag.getLocalName());
		if (property == null) {
			return null;
		}

		return new PropertyDocumentationTarget(propertyTag, lookupService.getCache(),
				artifactReference.getDeclaration().getVersion(), element.getText(), property);
	}


}
