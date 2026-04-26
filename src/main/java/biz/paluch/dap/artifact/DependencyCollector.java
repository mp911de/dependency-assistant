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
 * Mutable result object populated by parsers during a dependency scan.
 * <p>The collector maintains two distinct registries that together represent
 * how a dependency appears across the build files of a project slice:
 * <ul>
 * <li><em>Usages</em> — versioned dependency references at an active use site
 * (e.g. a {@code <dependency>} element with a resolved version, or a Gradle
 * dependency string). Registered via {@link #registerUsage}. The version
 * carried by a usage is the effective version that governs the artifact at that
 * location, whether declared inline or resolved from a property.</li>
 * <li><em>Declarations</em> — version-constraint entries that govern one or
 * more usages without being an active dependency themselves (e.g. a Maven
 * {@code <dependencyManagement>} entry or a Gradle {@code platform()} BOM
 * import). Registered via {@link #registerDeclaration}.</li>
 * </ul>
 * <p>In addition to the two dependency registries the collector accumulates
 * {@link #addProperties property names} observed in the scanned files (used for
 * property-version completion) and {@link #addAllReleaseSources release
 * sources} associated with the project's remote repositories (forwarded to
 * {@link biz.paluch.dap.artifact.ReleaseResolver} during version resolution).
 * <p>{@link #isEmpty()} reflects the usages registry only; declarations may be
 * present even when the collector is considered empty.
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
	 * <p>Collected property names are used by completion contributors to offer
	 * version-property candidates during editing.
	 *
	 * @param propertyNames the property names to register; must not be
	 * {@literal null}.
	 */
	public void addProperties(Collection<String> propertyNames) {
		this.properties.addAll(propertyNames);
	}

	/**
	 * Add release sources associated with the project's remote repositories.
	 * <p>The accumulated sources are later forwarded to
	 * {@link biz.paluch.dap.artifact.ReleaseResolver} so that version resolution
	 * queries the repositories that the project itself has configured.
	 *
	 * @param releaseSources the sources to add; must not be {@literal null}.
	 */
	public void addAllReleaseSources(Collection<? extends ReleaseSource> releaseSources) {
		this.releaseSources.addAll(releaseSources);
	}

	/**
	 * Return the release sources registered with this collector.
	 *
	 * @return the release sources; guaranteed to be not {@literal null} but may be
	 * empty.
	 */
	public Collection<ReleaseSource> getReleaseSources() {
		return releaseSources;
	}

	/**
	 * Register a versioned dependency usage found in the scanned build files.
	 * <p>A usage represents an active dependency reference whose effective version
	 * is known at the declaration site — either declared inline or resolved from a
	 * property or version catalog entry. Multiple declaration and version sources
	 * may be added to the same artifact as the scan progresses across files.
	 *
	 * @param artifactId the artifact coordinates; must not be {@literal null}.
	 * @param currentVersion the effective version at the use site; must not be
	 * {@literal null}.
	 * @param declarationSource the PSI location of the declaration; must not be
	 * {@literal null}.
	 * @param versionSource the PSI location of the version; must not be
	 * {@literal null}.
	 */
	public void registerUsage(ArtifactId artifactId, ArtifactVersion currentVersion,
			DeclarationSource declarationSource, VersionSource versionSource) {
		usages.computeIfAbsent(artifactId, ac -> new Dependency(ac, currentVersion))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Register a version-constraint declaration found in the scanned build files.
	 * <p>A declaration is a version-management entry that constrains one or more
	 * usages without being an active dependency itself — for example a Maven
	 * {@code <dependencyManagement>} entry or a Gradle BOM import. Declarations do
	 * not require a resolved version; their purpose is to anchor the PSI location
	 * so that updates can be written back to the correct element.
	 *
	 * @param artifactId the artifact coordinates; must not be {@literal null}.
	 * @param declarationSource the PSI location of the declaration; must not be
	 * {@literal null}.
	 * @param versionSource the PSI location of the version constraint; must not be
	 * {@literal null}.
	 */
	public void registerDeclaration(ArtifactId artifactId,
			DeclarationSource declarationSource, VersionSource versionSource) {
		declarations.computeIfAbsent(artifactId, DeclaredDependency::new)
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Return whether no dependency usages have been registered.
	 * <p>Returns {@literal true} when the usages registry is empty, regardless of
	 * whether any declarations have been registered.
	 *
	 * @return {@literal true} if no usages are present; {@literal false} otherwise.
	 */
	public boolean isEmpty() {
		return usages.isEmpty();
	}

	/**
	 * Return all version-constraint declarations registered with this collector.
	 *
	 * @return the declarations; guaranteed to be not {@literal null} but may be
	 * empty.
	 */
	public Collection<DeclaredDependency> getDeclarations() {
		return declarations.values();
	}

	/**
	 * Return all versioned dependency usages registered with this collector.
	 *
	 * @return the usages; guaranteed to be not {@literal null} but may be empty.
	 */
	public Collection<Dependency> getUsages() {
		return usages.values();
	}

	/**
	 * Return all property names registered with this collector.
	 *
	 * @return the property names; guaranteed to be not {@literal null} but may be
	 * empty.
	 */
	public Collection<String> getProperties() {
		return properties;
	}

	/**
	 * Return the registered usage for the given artifact, or {@literal null} if no
	 * usage has been registered.
	 *
	 * @param artifactId the artifact coordinates to look up; must not be
	 * {@literal null}.
	 * @return the registered usage, or {@literal null} if absent.
	 */
	public @Nullable Dependency getUsage(ArtifactId artifactId) {
		return usages.get(artifactId);
	}

	/**
	 * Return the registered usage for the given group ID and artifact ID, or
	 * {@literal null} if no usage has been registered.
	 *
	 * @param groupId the Maven group ID; must not be {@literal null}.
	 * @param artifactId the Maven artifact ID; must not be {@literal null}.
	 * @return the registered usage, or {@literal null} if absent.
	 */
	public @Nullable Dependency getUsage(String groupId, String artifactId) {
		return getUsage(ArtifactId.of(groupId, artifactId));
	}

}
