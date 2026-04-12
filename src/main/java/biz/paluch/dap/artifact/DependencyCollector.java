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
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.function.BiConsumer;

import org.jspecify.annotations.Nullable;

/**
 * Collects all dependencies and their usages.
 *
 * @author Mark Paluch
 */
public class DependencyCollector {

	private final Map<ArtifactId, DeclaredDependency> declarations = new TreeMap<>();

	private final Map<ArtifactId, Dependency> usages = new TreeMap<>();

	/**
	 * Properties defined in the scanned file.
	 */
	private final Set<String> properties = new TreeSet<>();

	/**
	 * Add properties defined in the scanned file.
	 * @param propertyNames the property names defined in the scanned file.
	 */
	public void addProperties(Collection<String> propertyNames) {
		this.properties.addAll(propertyNames);
	}

	/**
	 * Execute the given callback for each dependency.
	 */
	public void doWithDeclarations(BiConsumer<ArtifactId, DeclaredDependency> callback) {
		declarations.forEach(callback);
	}

	/**
	 * Register a dependency usage.
	 */
	public void registerUsage(ArtifactId artifactId, ArtifactVersion currentVersion,
			DeclarationSource declarationSource, VersionSource versionSource) {
		usages.computeIfAbsent(artifactId, ac -> new Dependency(ac, currentVersion))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Register dependency declaration and usage.
	 */
	public void register(ArtifactId artifactId, ArtifactVersion currentVersion,
			DeclarationSource declarationSource, VersionSource versionSource) {
		registerDeclaration(artifactId, declarationSource, versionSource);
		usages.computeIfAbsent(artifactId, ac -> new Dependency(ac, currentVersion))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Register a dependency declaration that is declared in the scanned file.
	 * Declarations are not required to define a version but indicate the place
	 * where a dependency has been declared. This allows to find usages that are not
	 * declared in the scanned file but are transitively used by other dependencies.
	 */
	public void registerDeclaration(ArtifactId artifactId,
			DeclarationSource declarationSource, VersionSource versionSource) {
		declarations.computeIfAbsent(artifactId, DeclaredDependency::new)
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Return whether there are no update candidates.
	 */
	public boolean isEmpty() {
		return usages.isEmpty();
	}

	/**
	 * Return all dependency declarations.
	 */
	public Collection<DeclaredDependency> getDeclarations() {
		return declarations.values();
	}

	/**
	 * Return all dependency usages.
	 */
	public Collection<Dependency> getUsages() {
		return usages.values();
	}

	/**
	 * Return all properties defined in the scanned file.
	 */
	public Collection<String> getProperties() {
		return properties;
	}

	/**
	 * Return the dependency for the given artifact id.
	 */
	public @Nullable Dependency getUsage(ArtifactId artifactId) {
		return usages.get(artifactId);
	}

	/**
	 * Return the dependency for the given artifact id.
	 */
	public @Nullable Dependency getUsage(String groupId, String artifactId) {
		return getUsage(ArtifactId.of(groupId, artifactId));
	}

}
