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

package biz.paluch.dap.lookup;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Per-file deep module that resolves a build-file element to its
 * {@link ArtifactReference} and current version.
 *
 * <p>Lookup methods are cache-only and never trigger remote repository access.
 *
 * @author Mark Paluch
 * @see ArtifactReferenceResolver
 */
public class VersionUpgradeLookup {

	private final StateService stateService;

	private final @Nullable ProjectState projectState;

	private final ArtifactReferenceResolver resolver;

	/**
	 * Create a {@code VersionUpgradeLookup} backed by the given state and resolver.
	 * @param stateService the state service exposing cached release data.
	 * @param projectState the project dependency state, or {@literal null} if it is
	 * unavailable.
	 * @param resolver the build-tool-specific reference resolver .
	 */
	public VersionUpgradeLookup(StateService stateService, @Nullable ProjectState projectState,
			ArtifactReferenceResolver resolver) {
		this.stateService = stateService;
		this.projectState = projectState;
		this.resolver = resolver;
	}

	/**
	 * Return the state service backing this lookup.
	 */
	public StateService getStateService() {
		return this.stateService;
	}

	/**
	 * Resolve the given PSI element into artifact declaration metadata.
	 * @param element the PSI element under inspection.
	 * @return a resolved artifact reference, or
	 * {@link ArtifactReference#unresolved()}.
	 */
	public ArtifactReference resolveArtifactReference(PsiElement element) {
		return resolver.resolveArtifactReference(element);
	}

	/**
	 * Locate every site in this lookup's file that participates in the given
	 * query's version, for a Dependency Site Find.
	 * @param query the version this find is centered on .
	 * @return the hits in this lookup's file, possibly empty.
	 */
	public DependencySearchResults search(DependencySiteQuery query) {
		return resolver.search(query);
	}

	/**
	 * Return the current version of the dependency with the given artifact
	 * reference.
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
		return declaration.isVersionDefined() ? declaration.getVersion() : null;
	}

	public @Nullable VersionProperty findProperty(String property) {
		return projectState != null ? projectState.findProperty(property) : null;
	}

	/**
	 * Return the current version of the first artifact associated with the given
	 * property.
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

		if (projectState == null) {
			return null;
		}

		Dependency dependency = projectState.findDependency(artifactId);
		return dependency != null ? dependency.getCurrentVersion() : null;
	}

}
