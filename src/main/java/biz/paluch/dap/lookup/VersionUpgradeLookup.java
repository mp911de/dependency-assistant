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
 * Per-file facade for artifact-reference resolution, Dependency Site Find, and
 * current project-state lookup.
 *
 * <p>PSI resolution and search are delegated to the configured
 * {@link ArtifactReferenceResolver}. Current-version lookup uses runtime
 * project state and declaration data. Property lookup follows
 * {@link ProjectState} correlation semantics. These operations never fetch
 * remote release metadata.
 *
 * @author Mark Paluch
 * @see ArtifactReferenceResolver
 */
public class VersionUpgradeLookup {

	private final StateService stateService;

	private final ProjectState projectState;

	private final ArtifactReferenceResolver resolver;

	/**
	 * Create a {@code VersionUpgradeLookup} backed by the given state and resolver.
	 *
	 * @param stateService the state service exposing cached release data.
	 * @param projectState the project dependency state.
	 * @param resolver the build-tool-specific reference resolver.
	 */
	public VersionUpgradeLookup(StateService stateService, ProjectState projectState,
			ArtifactReferenceResolver resolver) {
		this.stateService = stateService;
		this.projectState = projectState;
		this.resolver = resolver;
	}

	/**
	 * Create a lookup from the state associated with the given project identity.
	 *
	 * @param project the IntelliJ project owning the file.
	 * @param projectId the project-state identity.
	 * @param referenceResolver the build-tool-specific resolver for the file.
	 * @return the configured lookup.
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
	 * Resolve the given PSI element into artifact declaration metadata through the
	 * configured resolver.
	 *
	 * @param element the PSI element under inspection.
	 * @return the resolved artifact reference, or
	 * {@link ArtifactReference#unresolved()} if no declaration can be resolved.
	 */
	public ArtifactReference resolveArtifactReference(PsiElement element) {
		return resolver.resolveArtifactReference(element);
	}

	/**
	 * Locate the dependency sites in this lookup's file that match the given query.
	 *
	 * @param query the Dependency Site Find criteria.
	 * @return the matching hits in this lookup's file, possibly empty.
	 */
	public DependencySearchResults search(DependencySiteQuery query) {
		return resolver.search(query);
	}

	/**
	 * Return the current version of the dependency with the given artifact
	 * reference.
	 *
	 * <p>The {@link ProjectState} version wins when present; on a project-state
	 * miss the reference's own declared version is returned, so versions resolved
	 * by the resolver are reported even before the dependency is scanned into
	 * project state.
	 * @param reference the artifact to locate.
	 * @return the current artifact version, or {@literal null} if the reference is
	 * unresolved or carries no defined version and project state has no entry.
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
	 * Find an artifact-associated version property by its bare name.
	 *
	 * <p>The matching correlation may come from another project entry in the
	 * persisted project cache.
	 *
	 * @param property the property name to locate.
	 * @return the matching property, or {@literal null} if no correlated property
	 * is known.
	 */
	public @Nullable VersionProperty findProperty(String property) {
		return projectState.findProperty(property);
	}

	/**
	 * Return the current version of the first artifact associated with the given
	 * property.
	 *
	 * @param property the property whose artifact association should be inspected.
	 * @return the current artifact version, or {@literal null} if the property has
	 * no artifact association or project state does not contain the dependency.
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
