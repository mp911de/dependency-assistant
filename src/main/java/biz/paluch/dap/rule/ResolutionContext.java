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

package biz.paluch.dap.rule;

import java.util.Collection;
import java.util.Objects;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Immutable input for resolving the Dependency Rule of one artifact.
 *
 * <p>The context carries the branch lookup file and project version separately.
 * Plugin declarations suppress inferred semver upgrading while retaining
 * Artifact Display Name, Generation, and explicitly declared upgrade-strategy
 * governance.
 *
 * @author Mark Paluch
 */
public class ResolutionContext {

	private final ArtifactId artifactId;

	private final boolean suppressSemanticUpgrading;

	private final @Nullable VirtualFile branchFile;

	private final Versioned projectVersion;

	ResolutionContext(ArtifactId artifactId, boolean suppressSemanticUpgrading,
			@Nullable VirtualFile branchFile, Versioned projectVersion) {
		this.artifactId = artifactId;
		this.suppressSemanticUpgrading = suppressSemanticUpgrading;
		this.branchFile = branchFile;
		this.projectVersion = projectVersion;
	}

	/**
	 * Create a context that suppresses semantic inference for a plugin declaration.
	 *
	 * @param branchFile the file used for branch lookup, or {@code null} if
	 * unavailable.
	 */
	public static ResolutionContext forDeclaration(ArtifactDeclaration declaration,
			@Nullable VirtualFile branchFile, Versioned projectVersion) {

		return new ResolutionContext(declaration.getArtifactId(),
				declaration.getDeclarationSource().isPlugin(),
				branchFile, projectVersion);
	}

	/**
	 * Create a context that suppresses semantic inference only for a nonempty,
	 * plugin-only set of declaration sources.
	 *
	 * @param branchFile the file used for branch lookup, or {@code null} if
	 * unavailable.
	 */
	public static ResolutionContext forAggregate(DeclaredDependency dependency, @Nullable VirtualFile branchFile,
			Versioned projectVersion) {
		return forAggregate(dependency.getArtifactId(), dependency.getDeclarationSources(), branchFile,
				projectVersion);
	}

	/**
	 * Create a context that suppresses semantic inference only for a nonempty,
	 * plugin-only set of declaration sources.
	 *
	 * @param branchFile the file used for branch lookup, or {@code null} if
	 * unavailable.
	 */
	public static ResolutionContext forAggregate(ArtifactId artifactId,
			Collection<DeclarationSource> declarationSources, @Nullable VirtualFile branchFile,
			Versioned projectVersion) {

		return new ResolutionContext(artifactId, DeclarationSource.isPlugin(declarationSources), branchFile,
				projectVersion);
	}

	public ArtifactId getArtifactId() {
		return artifactId;
	}

	public boolean suppressSemanticUpgrading() {
		return suppressSemanticUpgrading;
	}

	public @Nullable VirtualFile getBranchFile() {
		return branchFile;
	}

	public Versioned getProjectVersion() {
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
				Objects.equals(this.branchFile, that.branchFile) &&
				Objects.equals(this.projectVersion, that.projectVersion);
	}

	@Override
	public int hashCode() {
		return Objects.hash(artifactId, suppressSemanticUpgrading, branchFile, projectVersion);
	}

	@Override
	public String toString() {
		return "ResolutionContext[" +
				"artifactId=" + artifactId + ", " +
				"suppressSemanticUpgrading=" + suppressSemanticUpgrading + ", " +
				"branchFile=" + branchFile + ", " +
				"projectVersion=" + projectVersion + ']';
	}

}
