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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import org.jspecify.annotations.Nullable;

/**
 * Mutable result object populated during a dependency scan.
 *
 * <p>
 * The collector keeps active dependency usages separate from managed
 * declarations such as Maven dependency management entries or Gradle platform
 * imports. It also records version properties and release sources discovered
 * while parsing build files.
 *
 * <p>
 * {@link #isEmpty()} reflects dependency usages only; managed declarations
 * may still be present.
 *
 * @author Mark Paluch
 * @see Dependency
 * @see DeclaredDependency
 */
public class DependencyCollector {

	private final Map<ArtifactId, DeclaredDependency> declarations = new TreeMap<>();

	private final Map<ArtifactId, Dependency> usages = new TreeMap<>();

	private final Set<ReleaseSource> releaseSources = new LinkedHashSet<>();

	private final Set<String> properties = new TreeSet<>();

	/**
	 * Add property names observed in the scanned build files.
	 * @param propertyNames the property names to register.
	 */
	public void addProperties(Collection<String> propertyNames) {
		this.properties.addAll(propertyNames);
	}

	/**
	 * Add a release source associated with the project's remote repositories.
	 * @param releaseSource the source to add.
	 */
	public void addReleaseSource(ReleaseSource releaseSource) {
		this.releaseSources.add(releaseSource);
	}

	/**
	 * Add release sources associated with the project's remote repositories.
	 * @param releaseSources the sources to add.
	 */
	public void addAllReleaseSources(Collection<? extends ReleaseSource> releaseSources) {
		this.releaseSources.addAll(releaseSources);
	}

	/**
	 * Return the release sources registered with this collector.
	 */
	public Collection<ReleaseSource> getReleaseSources() {
		return releaseSources;
	}

	/**
	 * Register a versioned dependency usage found in the scanned build files.
	 * @param artifactId the artifact coordinates.
	 * @param currentVersion the effective version at the use site.
	 * @param declarationSource the source of the dependency declaration.
	 * @param versionSource the source of the version declaration.
	 */
	public void registerUsage(ArtifactId artifactId, ArtifactVersion currentVersion,
			DeclarationSource declarationSource, VersionSource versionSource) {
		usages.computeIfAbsent(artifactId, ac -> new Dependency(ac, currentVersion))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Register a version-constraint declaration found in the scanned build files.
	 * @param artifactId the artifact coordinates.
	 * @param declarationSource the source of the managed declaration.
	 * @param versionSource the source of the version constraint.
	 */
	public void registerDeclaration(ArtifactId artifactId,
			DeclarationSource declarationSource, VersionSource versionSource) {
		declarations.computeIfAbsent(artifactId, DeclaredDependency::new)
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Return whether no dependency usages have been registered.
	 */
	public boolean isEmpty() {
		return usages.isEmpty();
	}

	/**
	 * Return all version-constraint declarations registered with this collector.
	 */
	public Collection<DeclaredDependency> getDeclarations() {
		return declarations.values();
	}

	/**
	 * Return all versioned dependency usages registered with this collector.
	 */
	public Collection<Dependency> getUsages() {
		return usages.values();
	}

	/**
	 * Return all property names registered with this collector.
	 */
	public Collection<String> getProperties() {
		return properties;
	}

	/**
	 * Return the registered usage for the given artifact, or {@literal null} if no
	 * usage has been registered.
	 * @param artifactId the artifact coordinates to look up.
	 */
	public @Nullable Dependency getUsage(ArtifactId artifactId) {
		return usages.get(artifactId);
	}

	/**
	 * Return the registered usage for the given group ID and artifact ID, or
	 * {@literal null} if no usage has been registered.
	 * @param groupId the Maven group ID.
	 * @param artifactId the Maven artifact ID.
	 */
	public @Nullable Dependency getUsage(String groupId, String artifactId) {
		return getUsage(ArtifactId.of(groupId, artifactId));
	}

	@Override
	public String toString() {
		return "DependencyCollector[Declarations: %d, Usages: %d, Properties: %d, Release Sources: %d]"
				.formatted(declarations.size(), usages.size(), properties.size(), releaseSources.size());
	}

}
