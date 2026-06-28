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

package biz.paluch.dap.rule;

import java.util.Collection;
import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.support.ArtifactReference;

/**
 * Input used to resolve a dependency rule for one artifact.
 *
 * @author Mark Paluch
 */
public class ResolutionContext {

	private final ArtifactId artifactId;

	private final boolean suppressSemanticUpgrading;

	private final BranchSource branchSource;

	private final Versioned projectVersion;

	ResolutionContext(ArtifactId artifactId, boolean suppressSemanticUpgrading,
			BranchSource branchSource, Versioned projectVersion) {
		this.artifactId = artifactId;
		this.suppressSemanticUpgrading = suppressSemanticUpgrading;
		this.branchSource = branchSource;
		this.projectVersion = projectVersion;
	}

	/**
	 * Create a resolution context from a single resolved artifact reference.
	 *
	 * @param artifactReference the artifact reference.
	 * @param branchSource the source used for branch lookup.
	 * @param projectVersion the project version used for branch rule selection.
	 * @return a resolution context using per-declaration plugin semantics.
	 */
	public static ResolutionContext of(ArtifactReference artifactReference, BranchSource branchSource,
			Versioned projectVersion) {

		return new ResolutionContext(artifactReference.getArtifactId(),
				artifactReference.getDeclaration().getDeclarationSource()
						.isPlugin(),
				branchSource, projectVersion);
	}

	/**
	 * Create a resolution context for a bare artifact without plugin semantics.
	 *
	 * @param artifactId the artifact to resolve.
	 * @param branchSource the source used for branch lookup.
	 * @param projectVersion the project version used for branch rule selection.
	 * @return a resolution context treating the artifact as a non-plugin
	 * dependency.
	 */
	public static ResolutionContext of(ArtifactId artifactId, BranchSource branchSource,
			Versioned projectVersion) {

		return new ResolutionContext(artifactId, false, branchSource, projectVersion);
	}

	/**
	 * Create a resolution context from an aggregate declared dependency.
	 *
	 * @param dependency the aggregate dependency declaration.
	 * @param branchSource the source used for branch lookup.
	 * @param projectVersion the project version used for branch rule selection.
	 * @return a resolution context using aggregate plugin-only semantics.
	 */
	public static ResolutionContext of(DeclaredDependency dependency, BranchSource branchSource,
			Versioned projectVersion) {
		return of(dependency.getArtifactId(), dependency.getDeclarationSources(), branchSource, projectVersion);
	}

	/**
	 * Create a resolution context from an artifact and its declaration sources
	 * using aggregate plugin-only semantics.
	 *
	 * @param artifactId the artifact to resolve.
	 * @param declarationSources the declaration sources backing the artifact.
	 * @param branchSource the source used for branch lookup.
	 * @param projectVersion the project version used for branch rule selection.
	 * @return a resolution context using aggregate plugin-only semantics.
	 */
	public static ResolutionContext of(ArtifactId artifactId, Collection<DeclarationSource> declarationSources,
			BranchSource branchSource, Versioned projectVersion) {

		return new ResolutionContext(artifactId, DeclarationSource.isPlugin(declarationSources), branchSource,
				projectVersion);
	}

	public ArtifactId getArtifactId() {
		return artifactId;
	}

	public boolean suppressSemanticUpgrading() {
		return suppressSemanticUpgrading;
	}

	BranchSource getBranchSource() {
		return branchSource;
	}

	Versioned getProjectVersion() {
		return projectVersion;
	}

	@Override
	public boolean equals(Object obj) {
		if (obj == this) {
			return true;
		}
		if (obj == null || obj.getClass() != this.getClass()) {
			return false;
		}
		ResolutionContext that = (ResolutionContext) obj;
		return Objects.equals(this.artifactId, that.artifactId) &&
				this.suppressSemanticUpgrading == that.suppressSemanticUpgrading &&
				Objects.equals(this.branchSource, that.branchSource) &&
				Objects.equals(this.projectVersion, that.projectVersion);
	}

	@Override
	public int hashCode() {
		return Objects.hash(artifactId, suppressSemanticUpgrading, branchSource, projectVersion);
	}

	@Override
	public String toString() {
		return "ResolutionContext[" +
				"artifactId=" + artifactId + ", " +
				"suppressSemanticUpgrading=" + suppressSemanticUpgrading + ", " +
				"branchSource=" + branchSource + ", " +
				"projectVersion=" + projectVersion + ']';
	}

}
