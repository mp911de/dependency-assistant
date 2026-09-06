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

package biz.paluch.dap.artifact;

/**
 * A {@link DeclaredDependency} with an effective current version.
 * <p>The version remains fixed as additional sources are merged.
 *
 * @author Mark Paluch
 * @see DependencyCollector#registerUsage
 */
public class Dependency extends DeclaredDependency implements VersionedArtifact {

	private final ArtifactVersion currentVersion;

	public Dependency(PackageIdentity pkg, ArtifactVersion currentVersion) {
		super(pkg);
		this.currentVersion = currentVersion;
	}

	/**
	 * Create a usage with the given version and a copy of the declaration sources.
	 */
	public static Dependency from(DeclaredDependency declaredDependency, ArtifactVersion version) {

		Dependency dependency = new Dependency(declaredDependency.getPackageIdentity(), version);
		declaredDependency.getDeclarationSources().forEach(dependency::addDeclarationSource);
		declaredDependency.getVersionSources().forEach(dependency::addVersionSource);
		return dependency;
	}

	public ArtifactVersion getCurrentVersion() {
		return currentVersion;
	}

	@Override
	public boolean isVersioned() {
		return true;
	}

	@Override
	public ArtifactVersion getVersion() {
		return currentVersion;
	}

	@Override
	public String toString() {
		return getArtifactId() + "@" + currentVersion;
	}

}
