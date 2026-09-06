/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.lookup;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Per-file dependency lookup backed by PSI and project state. These operations
 * do not fetch remote release metadata.
 *
 * @author Mark Paluch
 * @see ArtifactReferenceResolver
 */
public class VersionUpgradeLookup {

	private final StateService stateService;

	private final ProjectState projectState;

	private final ArtifactReferenceResolver resolver;

	public VersionUpgradeLookup(StateService stateService, ProjectState projectState,
			ArtifactReferenceResolver resolver) {
		this.stateService = stateService;
		this.projectState = projectState;
		this.resolver = resolver;
	}

	/**
	 * Create a lookup for the given project-state identity.
	 */
	public static VersionUpgradeLookup of(Project project, ProjectId projectId,
			ArtifactReferenceResolver referenceResolver) {

		StateService stateService = StateService.getInstance(project);
		ProjectState projectState = stateService.getProjectState(projectId);
		return new VersionUpgradeLookup(stateService, projectState,
				referenceResolver);
	}

	public StateService getStateService() {
		return this.stateService;
	}

	/**
	 * Resolve a dependency reference through the configured resolver.
	 *
	 * @see ArtifactReferenceResolver#resolveArtifactReference(PsiElement)
	 */
	public ArtifactReference resolveArtifactReference(PsiElement element) {
		return resolver.resolveArtifactReference(element);
	}

	/**
	 * Find matching sites in this lookup's file.
	 *
	 * @see ArtifactReferenceResolver#search(DependencySiteQuery)
	 */
	public DependencySearchResults search(DependencySiteQuery query) {
		return resolver.search(query);
	}

	/**
	 * Return the project-state version, falling back to the declared version. This
	 * also supports references resolved before the dependency is scanned.
	 *
	 * @return {@code null} for an unresolved reference or if neither source has a
	 * version.
	 */
	public @Nullable ArtifactVersion getCurrentVersion(ArtifactReference reference) {

		if (!reference.isResolved()) {
			return null;
		}

		ArtifactVersion stateVersion = findCurrentVersion(reference.getArtifactId());
		if (stateVersion != null) {
			return stateVersion;
		}

		ArtifactDeclaration declaration = reference.getDeclaration();
		return declaration.isVersioned() ? declaration.getVersion() : null;
	}

	/**
	 * Find a correlated version property by its bare name. The correlation may come
	 * from another project entry in the persisted cache.
	 *
	 * @return {@code null} if no correlated property is known.
	 */
	public @Nullable VersionProperty findProperty(String property) {
		return projectState.findProperty(property);
	}

	/**
	 * Return the current version of the first artifact associated with the
	 * property.
	 *
	 * @return {@code null} if there is no artifact association or the dependency is
	 * absent from project state.
	 */
	public @Nullable ArtifactVersion getCurrentVersion(VersionProperty property) {

		if (property.artifacts().isEmpty()) {
			return null;
		}
		return findCurrentVersion(property.artifacts().getFirst().toArtifactId());
	}

	private @Nullable ArtifactVersion findCurrentVersion(ArtifactId artifactId) {

		Dependency dependency = projectState.findDependency(artifactId);
		return dependency != null ? dependency.getCurrentVersion() : null;
	}

}
