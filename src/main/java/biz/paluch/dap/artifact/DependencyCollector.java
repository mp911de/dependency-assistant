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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

/**
 * Mutable aggregate populated during a dependency scan.
 *
 * <p>The collector keeps active dependency usages separate from managed
 * declarations such as Maven dependency management entries or Gradle platform
 * imports. It also records version properties and release sources discovered
 * while parsing build files.
 *
 * <p>{@link #isEmpty()} reflects dependency usages only; managed declarations
 * may still be present.
 *
 * @author Mark Paluch
 * @see Dependency
 * @see DeclaredDependency
 */
public class DependencyCollector {

	private final PackageSystem packageSystem;

	private final Map<ArtifactId, DeclaredDependency> declarations = new TreeMap<>();

	private final Map<ArtifactId, Dependency> usages = new TreeMap<>();

	private final Set<BillOfMaterials> billOfMaterials = new LinkedHashSet<>();

	private final Set<ReleaseSource> releaseSources = new LinkedHashSet<>();

	private final Set<String> properties = new TreeSet<>();

	private final Map<String, String> propertyValues = new LinkedHashMap<>();

	public DependencyCollector(HasPackageSystem aware) {
		this(aware.getPackageSystem());
	}

	public DependencyCollector(PackageSystem packageSystem) {
		this.packageSystem = packageSystem;
	}

	/**
	 * Add property names observed in the scanned build files.
	 * @param propertyNames the property names to register.
	 */
	public void addProperties(Collection<String> propertyNames) {
		this.properties.addAll(propertyNames);
	}

	/**
	 * Register effective property values observed for the scanned build file.
	 * <p>Used by integrations that perform scan-wide property promotion during
	 * {@link biz.paluch.dap.IntrospectedDependencies#complete(DependencyCollector)
	 * completion}. Each value should be the effective value visible from this
	 * collector's anchor file, including inherited values from parent build
	 * descriptors.
	 * @param values the property values keyed by property name.
	 */
	public void addPropertyValues(Map<String, String> values) {
		this.propertyValues.putAll(values);
	}

	/**
	 * Return the effective property values registered with this collector keyed by
	 * property name.
	 * @return the mutable live property-value map.
	 */
	public Map<String, String> getPropertyValues() {
		return propertyValues;
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
	 * @return the mutable live collection in registration order.
	 */
	public Collection<ReleaseSource> getReleaseSources() {
		return releaseSources;
	}

	/**
	 * Register a versioned dependency usage found in the scanned build files.
	 *
	 * <p>The first registered effective version is retained for an artifact.
	 * Subsequent registrations merge only their declaration and version sources.
	 *
	 * @param artifactId the artifact coordinates.
	 * @param currentVersion the effective version to retain on first registration.
	 * @param declarationSource the source of the dependency declaration.
	 * @param versionSource the source of the version declaration.
	 */
	public void registerUsage(ArtifactId artifactId, ArtifactVersion currentVersion,
			DeclarationSource declarationSource, VersionSource versionSource) {
		usages.computeIfAbsent(artifactId, ac -> new Dependency(PackageIdentity.of(ac, packageSystem), currentVersion))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Register a Bill of Materials resolved while scanning the build files.
	 * <p>{@link BillOfMaterials} identity is its coordinates and version, so the
	 * same BOM imported at two versions contributes two entries while a repeated
	 * registration of one coordinate-version pair keeps the first. A Bill of
	 * Materials with no members records the BOM identity and version without any
	 * known member pins.
	 * @param bom the resolved Bill of Materials.
	 */
	public void registerBillOfMaterials(BillOfMaterials bom) {
		billOfMaterials.add(bom);
	}

	/**
	 * Return the Bills of Materials registered while scanning the build files.
	 * @return the mutable live collection in registration order. The collection is
	 * empty when the scan found none.
	 */
	public Collection<BillOfMaterials> getBillOfMaterials() {
		return billOfMaterials;
	}

	/**
	 * Register a version-constraint declaration found in the scanned build files.
	 * @param artifactId the artifact coordinates.
	 * @param declarationSource the source of the managed declaration.
	 * @param versionSource the source of the version constraint.
	 */
	public void registerDeclaration(ArtifactId artifactId,
			DeclarationSource declarationSource, VersionSource versionSource) {
		declarations.computeIfAbsent(artifactId, pkg -> new DeclaredDependency(PackageIdentity.of(pkg, packageSystem)))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Promote each unresolved declaration to a usage when the given resolver
	 * yields a {@link Dependency}.
	 * <p>
	 * Declarations whose artifact already has a registered usage are left
	 * untouched. For each remaining declaration the resolver is invoked, and a
	 * non-{@literal null} result is registered as a usage using the resolved
	 * dependency's first declaration source and first version source.
	 * @param resolver function that resolves a declaration to a usage, or returns
	 * {@literal null} when no resolution is available.
	 */
	public void promoteResolvedDeclarations(Function<DeclaredDependency, @Nullable Dependency> resolver) {

		for (DeclaredDependency declaration : declarations.values()) {

			if (usages.containsKey(declaration.getArtifactId())) {
				continue;
			}

			Dependency resolved = resolver.apply(declaration);
			if (resolved == null) {
				continue;
			}

			DeclarationSource declarationSource = resolved.getDeclarationSources().iterator().next();
			VersionSource versionSource = resolved.getVersionSources().iterator().next();
			registerUsage(resolved.getArtifactId(), resolved.getCurrentVersion(), declarationSource, versionSource);
		}
	}

	/**
	 * Return whether no dependency usages have been registered.
	 * @return {@code true} if no usages have been registered.
	 */
	public boolean isEmpty() {
		return usages.isEmpty();
	}

	/**
	 * Return all version-constraint declarations registered with this collector.
	 * @return the live declarations in artifact-coordinate order. Removing from
	 * this view mutates the collector; adding is unsupported.
	 */
	public Collection<DeclaredDependency> getDeclarations() {
		return declarations.values();
	}

	/**
	 * Return all versioned dependency usages registered with this collector.
	 * @return the live usages in artifact-coordinate order. Removing from this view
	 * mutates the collector; adding is unsupported.
	 */
	public Collection<Dependency> getUsages() {
		return usages.values();
	}

	/**
	 * Return all property names registered with this collector.
	 * @return the mutable live property names in natural order.
	 */
	public Collection<String> getProperties() {
		return properties;
	}

	/**
	 * Return the registered usage for the given artifact, or {@literal null} if no
	 * usage has been registered.
	 * @param artifactId the artifact coordinates to look up.
	 * @return the registered usage, or {@literal null} if absent.
	 */
	public @Nullable Dependency getUsage(ArtifactId artifactId) {
		return usages.get(artifactId);
	}

	/**
	 * Return the registered declaration for the given artifact, or {@literal null}
	 * if no declaration has been registered.
	 * @param artifactId the artifact coordinates to look up.
	 * @return the registered declaration, or {@literal null} if absent.
	 */
	public @Nullable DeclaredDependency getDeclaration(ArtifactId artifactId) {
		return declarations.get(artifactId);
	}

	/**
	 * Return the registered usage for the given group ID and artifact ID, or
	 * {@literal null} if no usage has been registered.
	 * @param groupId the Maven group ID.
	 * @param artifactId the Maven artifact ID.
	 * @return the registered usage, or {@literal null} if absent.
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
