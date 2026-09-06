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
 * Mutable result of a dependency scan.
 * <p>Active usages are separate from managed declarations. A collector can have
 * managed declarations even when {@link #isEmpty()} returns {@code true}.
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

	public void addProperties(Collection<String> propertyNames) {
		this.properties.addAll(propertyNames);
	}

	/**
	 * Register property values for scan-wide property promotion.
	 * <p>Values must be effective at the anchor file, including inherited
	 * properties.
	 * @see biz.paluch.dap.IntrospectedDependencies#complete(DependencyCollector)
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

	public void addReleaseSource(ReleaseSource releaseSource) {
		this.releaseSources.add(releaseSource);
	}

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
	 * Register a usage, retaining the first effective version for its artifact.
	 * Later registrations merge declaration and version sources.
	 */
	public void registerUsage(ArtifactId artifactId, ArtifactVersion currentVersion,
			DeclarationSource declarationSource, VersionSource versionSource) {
		usages.computeIfAbsent(artifactId, ac -> new Dependency(PackageIdentity.of(ac, packageSystem), currentVersion))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Register a BOM, keeping the first entry for each package identity and
	 * version.
	 */
	public void registerBillOfMaterials(BillOfMaterials bom) {
		billOfMaterials.add(bom);
	}

	/**
	 * Return the Bills of Materials registered while scanning the build files.
	 * @return the mutable live collection in registration order.
	 */
	public Collection<BillOfMaterials> getBillOfMaterials() {
		return billOfMaterials;
	}

	/**
	 * Register a managed declaration and merge its sources with earlier
	 * registrations.
	 */
	public void registerDeclaration(ArtifactId artifactId,
			DeclarationSource declarationSource, VersionSource versionSource) {
		declarations.computeIfAbsent(artifactId, pkg -> new DeclaredDependency(PackageIdentity.of(pkg, packageSystem)))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Resolve declarations that have no registered usage.
	 * @param resolver returns a dependency with at least one declaration source and
	 * one version source, or {@literal null} if unresolved. Only the first source
	 * of each kind is registered.
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
	 */
	public boolean isEmpty() {
		return usages.isEmpty();
	}

	/**
	 * Return all version-constraint declarations registered with this collector.
	 * @return the live declarations in artifact-coordinate order. Removing from
	 * this view mutates the collector. Adding is unsupported.
	 */
	public Collection<DeclaredDependency> getDeclarations() {
		return declarations.values();
	}

	/**
	 * Return all versioned dependency usages registered with this collector.
	 * @return the live usages in artifact-coordinate order. Removing from this view
	 * mutates the collector. Adding is unsupported.
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
	 */
	public @Nullable Dependency getUsage(ArtifactId artifactId) {
		return usages.get(artifactId);
	}

	/**
	 * Return the registered declaration for the given artifact, or {@literal null}
	 * if no declaration has been registered.
	 */
	public @Nullable DeclaredDependency getDeclaration(ArtifactId artifactId) {
		return declarations.get(artifactId);
	}

	/**
	 * Return the registered usage for the given group ID and artifact ID, or
	 * {@literal null} if no usage has been registered.
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
