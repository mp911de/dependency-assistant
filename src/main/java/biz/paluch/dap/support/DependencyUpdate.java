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
import biz.paluch.dap.upgrade.UpgradeSuggestions;

/**
 * Apply-ready version change for one dependency declaration.
 *
 * <p>A {@code DependencyUpdate} extends an {@link ArtifactVersionChange} with
 * the declaration sources and version sources that an update writer needs to
 * rewrite a build file. It represents a chosen target, not a suggestion. Use
 * {@link UpgradeSuggestion} or {@link UpgradeSuggestions} for proposed targets
 * before the user or policy has selected one.
 *
 * @author Mark Paluch
 * @see ArtifactVersionChange
 * @see UpgradeSuggestion
 */
public class DependencyUpdate extends ArtifactVersionChange {

	private final Collection<DeclarationSource> declarationSources;

	private final Collection<VersionSource> versionSources;

	/**
	 * Create an apply-ready update with explicit source locations.
	 *
	 * @param artifactId the dependency coordinate to update.
	 * @param from the dependency version currently in use.
	 * @param version the selected target version.
	 * @param declarationSources the declaration sources participating in the
	 * update.
	 * @param versionSources the version sources that can be rewritten.
	 */
	public DependencyUpdate(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion version,
			Collection<DeclarationSource> declarationSources, Collection<VersionSource> versionSources) {
		super(artifactId, from, version);
		this.declarationSources = declarationSources;
		this.versionSources = versionSources;
	}

	/**
	 * Create an apply-ready update for one declaration source and one version
	 * source.
	 *
	 * @param artifactId the dependency coordinate to update.
	 * @param from the dependency version currently in use.
	 * @param version the selected target version.
	 * @param declarationSource the declaration source participating in the update.
	 * @param versionSource the version source that can be rewritten.
	 */
	public DependencyUpdate(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion version,
			DeclarationSource declarationSource, VersionSource versionSource) {
		this(artifactId, from, version, List.of(declarationSource), List.of(versionSource));
	}

	/**
	 * Create an update from a resolved artifact declaration and target version.
	 *
	 * @param declaration the resolved artifact declaration to update.
	 * @param version the selected target version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(ArtifactDeclaration declaration, VersionAware version) {
		return from(declaration, version.getVersion());
	}

	/**
	 * Create an update from a resolved artifact declaration and target version.
	 *
	 * @param declaration the resolved artifact declaration to update.
	 * @param version the selected target version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(ArtifactDeclaration declaration, ArtifactVersion version) {
		return new DependencyUpdate(declaration.getArtifactId(), declaration.getVersion(), version,
				declaration.getDeclarationSource(), declaration.getVersionSource());
	}

	/**
	 * Create an update from a dependency and target version.
	 *
	 * @param dependency the dependency whose declarations should be updated.
	 * @param release the selected target release.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(Dependency dependency, VersionAware release) {
		return from(dependency, release.getVersion());
	}

	/**
	 * Create an update from a dependency and target version.
	 *
	 * @param dependency the dependency whose declarations should be updated.
	 * @param version the selected target version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(Dependency dependency, ArtifactVersion version) {
		return new DependencyUpdate(dependency.getArtifactId(), dependency.getCurrentVersion(), version,
				dependency.getDeclarationSources(), dependency.getVersionSources());
	}

	/**
	 * Create an update from a dependency while using a replacement artifact id.
	 *
	 * @param artifactId the artifact id to write into the update.
	 * @param dependency the dependency whose declarations should be updated.
	 * @param version the selected target version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate from(ArtifactId artifactId, Dependency dependency, ArtifactVersion version) {
		return new DependencyUpdate(artifactId, dependency.getCurrentVersion(), version,
				dependency.getDeclarationSources(), dependency.getVersionSources());
	}

	/**
	 * Create an update for a direct dependency declaration with an inline version.
	 *
	 * @param artifactId the dependency coordinate to update.
	 * @param version the selected target version.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate create(ArtifactId artifactId, ArtifactVersion version) {
		return create(artifactId, version, DeclarationSource.dependency(),
				VersionSource.declared(version.toString()));
	}

	/**
	 * Create an update with explicit declaration and version sources.
	 *
	 * @param artifactId the dependency coordinate to update.
	 * @param version the selected target version.
	 * @param declarationSource the declaration source participating in the update.
	 * @param versionSource the version source that can be rewritten.
	 * @return the dependency update to apply.
	 */
	public static DependencyUpdate create(ArtifactId artifactId, ArtifactVersion version,
			DeclarationSource declarationSource, VersionSource versionSource) {
		return new DependencyUpdate(artifactId, version, version, List.of(declarationSource), List.of(versionSource));
	}

	/**
	 * Evaluate the {@link Predicate} against the {@link VersionSource}s and return
	 * {@code true} if any match.
	 *
	 * @param versionSourcePredicate the predicate to apply to the version sources.
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
	 * Return the declaration sources participating in this update.
	 *
	 * @return the declaration sources supplied when the update was created.
	 */
	public Collection<DeclarationSource> declarationSources() {
		return declarationSources;
	}

	/**
	 * Return the version sources that can be rewritten by this update.
	 *
	 * @return the version sources supplied when the update was created.
	 */
	public Collection<VersionSource> versionSources() {
		return versionSources;
	}

}
