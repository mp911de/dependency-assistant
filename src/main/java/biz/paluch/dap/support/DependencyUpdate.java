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
import biz.paluch.dap.upgrade.UpgradeSuggestion;

/**
 * A selected dependency update with the sources needed by a file writer. Use
 * {@link UpgradeSuggestion} for a target that has not yet been selected.
 *
 * <p>Source collections are retained and exposed without copying. Callers
 * remain responsible for changes to mutable collections.
 *
 * @author Mark Paluch
 */
public class DependencyUpdate extends ArtifactVersionChange {

	private final Collection<DeclarationSource> declarationSources;

	private final Collection<VersionSource> versionSources;

	public DependencyUpdate(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion version,
			Collection<DeclarationSource> declarationSources, Collection<VersionSource> versionSources) {
		super(artifactId, from, version);
		this.declarationSources = declarationSources;
		this.versionSources = versionSources;
	}

	public DependencyUpdate(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion version,
			DeclarationSource declarationSource, VersionSource versionSource) {
		this(artifactId, from, version, List.of(declarationSource), List.of(versionSource));
	}

	/**
	 * Create an update from the declaration.
	 *
	 * @throws IllegalStateException if the declaration has no resolved version.
	 */
	public static DependencyUpdate from(ArtifactDeclaration declaration, VersionAware version) {
		return from(declaration, version.getVersion());
	}

	/**
	 * Create an update from the declaration.
	 *
	 * @throws IllegalStateException if the declaration has no resolved version.
	 */
	public static DependencyUpdate from(ArtifactDeclaration declaration, ArtifactVersion version) {
		return new DependencyUpdate(declaration.getArtifactId(), declaration.getVersion(), version,
				declaration.getDeclarationSource(), declaration.getVersionSource());
	}

	public static DependencyUpdate from(Dependency dependency, VersionAware release) {
		return from(dependency, release.getVersion());
	}

	public static DependencyUpdate from(Dependency dependency, ArtifactVersion version) {
		return new DependencyUpdate(dependency.getArtifactId(), dependency.getCurrentVersion(), version,
				dependency.getDeclarationSources(), dependency.getVersionSources());
	}

	public static DependencyUpdate from(ArtifactId artifactId, Dependency dependency, ArtifactVersion version) {
		return new DependencyUpdate(artifactId, dependency.getCurrentVersion(), version,
				dependency.getDeclarationSources(), dependency.getVersionSources());
	}

	/**
	 * Create an inline dependency update using the target as its source version.
	 */
	public static DependencyUpdate create(ArtifactId artifactId, ArtifactVersion version) {
		return create(artifactId, version, DeclarationSource.dependency(),
				VersionSource.declared(version.toString()));
	}

	/**
	 * Create an update using the target as its source version.
	 */
	public static DependencyUpdate create(ArtifactId artifactId, ArtifactVersion version,
			DeclarationSource declarationSource, VersionSource versionSource) {
		return new DependencyUpdate(artifactId, version, version, List.of(declarationSource), List.of(versionSource));
	}

	public boolean hasVersionSource(Predicate<VersionSource> versionSourcePredicate) {
		for (VersionSource versionSource : versionSources) {
			if (versionSourcePredicate.test(versionSource)) {
				return true;
			}
		}
		return false;
	}

	public Collection<DeclarationSource> declarationSources() {
		return declarationSources;
	}

	public Collection<VersionSource> versionSources() {
		return versionSources;
	}

}
