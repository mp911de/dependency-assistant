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

package biz.paluch.dap.support;

import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.VersionAware;
import biz.paluch.dap.artifact.VersionSource;

/**
 * Value object to represent a dependency update.
 *
 * @param coordinate the dependency coordinate to update.
 * @param version the selected target version.
 * @param declarationSources the declaration sources to update.
 * @param versionSources the version sources to update.
 */
public record DependencyUpdate(ArtifactId coordinate, ArtifactVersion from, ArtifactVersion version,
		Collection<DeclarationSource> declarationSources, Collection<VersionSource> versionSources) {

	public DependencyUpdate(ArtifactId coordinate, ArtifactVersion from, ArtifactVersion version,
			DeclarationSource declarationSource, VersionSource versionSource) {
		this(coordinate, from, version, List.of(declarationSource), List.of(versionSource));
	}

	/**
	 * Create an update for a dependency and release.
	 *
	 * @param reference the artifact reference to update.
	 * @param version the selected version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(ArtifactReference reference, VersionAware version) {
		return from(reference, version.getVersion());
	}

	/**
	 * Create an update for a dependency and release.
	 *
	 * @param reference the artifact reference to update.
	 * @param version the selected version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(ArtifactReference reference, ArtifactVersion version) {
		ArtifactDeclaration declaration = reference.getDeclaration();
		return new DependencyUpdate(declaration.getArtifactId(), declaration.getVersion(), version,
				declaration.getDeclarationSource(), declaration.getVersionSource());
	}


	/**
	 * Create an update for a dependency and release.
	 * @param dependency the dependency to update.
	 * @param release the selected release.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(Dependency dependency, VersionAware release) {
		return from(dependency, release.getVersion());
	}

	/**
	 * Create an update for a dependency and release.
	 *
	 * @param dependency the dependency to update.
	 * @param version the selected version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(Dependency dependency, ArtifactVersion version) {
		return new DependencyUpdate(dependency.getArtifactId(), dependency.getCurrentVersion(), version,
				dependency.getDeclarationSources(), dependency.getVersionSources());
	}

	/**
	 * Create an update for a dependency and release.
	 *
	 * @param artifactId the artifact Id to update.
	 * @param dependency the dependency to update.
	 * @param version the selected version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(ArtifactId artifactId, Dependency dependency, ArtifactVersion version) {
		return new DependencyUpdate(artifactId, dependency.getCurrentVersion(), version,
				dependency.getDeclarationSources(), dependency.getVersionSources());
	}

	/**
	 * Create an update for a direct dependency declaration with an inline version.
	 * @param artifactId the artifact coordinate to update.
	 * @param version the selected target version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate create(ArtifactId artifactId, ArtifactVersion version) {
		return create(artifactId, version, DeclarationSource.dependency(),
				VersionSource.declared(version.toString()));
	}

	/**
	 * Create an update with explicit declaration and version sources.
	 * @param artifactId the artifact coordinate to update.
	 * @param version the selected target version.
	 * @param declarationSource the declaration source to update.
	 * @param versionSource the version source to update.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate create(ArtifactId artifactId, ArtifactVersion version,
			DeclarationSource declarationSource, VersionSource versionSource) {
		return new DependencyUpdate(artifactId, version, version, List.of(declarationSource), List.of(versionSource));
	}

	/**
	 * Evaluate the {@link Predicate} against the {@link VersionSource}s and return
	 * {@code true} if any match.
	 * @param versionSourcePredicate the conditional to evaluate.
	 * @return {@code true} if any version source matches; {@code false} otherwise.
	 */
	public boolean hasVersionSource(Predicate<VersionSource> versionSourcePredicate) {
		for (VersionSource versionSource : versionSources) {
			if (versionSourcePredicate.test(versionSource)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return the {@link #version()} as String.
	 * @return the string representation of the target version.
	 * @see #version()
	 */
	public String versionAsString() {
		return version.toString();
	}

}
