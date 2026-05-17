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

package biz.paluch.dap.artifact;

/**
 * A versioned dependency usage found in the scanned build files.
 * <p>
 * Extends {@link DeclaredDependency} with a {@link #getCurrentVersion()
 * current version} - the effective version that governs this artifact at its
 * declaration site, whether specified inline, resolved from a property, or
 * derived from a version catalog entry. The version is fixed at construction
 * and does not change as additional declaration or version sources are added to
 * the parent.
 *
 * @author Mark Paluch
 * @see DeclaredDependency
 * @see DependencyCollector#registerUsage
 */
public class Dependency extends DeclaredDependency implements HasArtifactId {

	private final ArtifactVersion currentVersion;

	/**
	 * Create a dependency usage with its effective current version.
	 */
	public Dependency(ArtifactId artifactId, ArtifactVersion currentVersion) {
		super(artifactId);
		this.currentVersion = currentVersion;
	}

	/**
	 * Create a dependency usage with its effective current version from a declared
	 * dependency.
	 * @param declaredDependency the source of the dependency declaration.
	 * @param version the effective version of the dependency.
	 * @return the new dependency.
	 */
	public static Dependency from(DeclaredDependency declaredDependency, ArtifactVersion version) {

		Dependency dependency = new Dependency(declaredDependency.getArtifactId(), version);
		declaredDependency.getDeclarationSources().forEach(dependency::addDeclarationSource);
		declaredDependency.getVersionSources().forEach(dependency::addVersionSource);
		return dependency;
	}

	/**
	 * Return the effective version of this dependency at its declaration site.
	 *
	 * @return the current version; guaranteed to be not {@literal null}.
	 */
	public ArtifactVersion getCurrentVersion() {
		return currentVersion;
	}

	@Override
	public String toString() {
		return getArtifactId() + "@" + currentVersion;
	}

}
