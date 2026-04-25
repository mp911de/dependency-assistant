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

import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.DependencyDocumentationProviderSupport;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.openapi.project.Project;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.platform.backend.documentation.PsiDocumentationTargetProvider;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Provides Quick Documentation ({@code Ctrl+Q}) for Gradle elements.
 *
 * @author Mark Paluch
 */
public class DependencyDocumentationProvider extends DependencyDocumentationProviderSupport
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

		VersionUpgradeLookupSupport lookupService = VersionUpgradeLookupService.create(element);
		ArtifactReference artifactReference = lookupService.resolveArtifactReference(element);
		if (!artifactReference.isResolved()) {
			return null;
		}

		Cache cache = lookupService.getCache();

		if (lookupService.hasCachedState()
				&& artifactReference.getDeclaration()
						.getVersionSource() instanceof VersionSource.VersionProperty property) {

			ProjectState projectState = lookupService.getProjectState();
			VersionProperty cachedProperty = projectState.findProperty(property.getProperty());
			if (cachedProperty == null || cachedProperty.artifacts().isEmpty()) {
				return null;
			}

			return new PropertyDocumentationTarget(target, cache, artifactReference.getDeclaration().getVersion(),
					element.getText(), cachedProperty);
		}

		return new DependencyVersionTarget(target, cache, artifactReference.getArtifactId(),
				artifactReference.getDeclaration().getVersion(), element.getText());
	}

}
