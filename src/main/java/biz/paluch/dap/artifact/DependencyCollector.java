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

import org.springframework.util.MultiValueMap;
import org.springframework.util.MultiValueMapAdapter;

/**
 * Collects all dependencies and their usages.
 *
 * @author Mark Paluch
 */
public class DependencyCollector {

	private final MultiValueMap<ArtifactId, ArtifactUsage> allDependencies = new MultiValueMapAdapter<>(
			new TreeMap<>());
	private final Map<ArtifactId, Dependency> versionCheckCandidates = new TreeMap<>();

	/**
	 * Properties defined in the scanned file.
	 */
	private final Set<String> properties = new TreeSet<>();

	/**
	 * Add a dependency usage.
	 */
	public void add(ArtifactId coordinate, ArtifactUsage usage) {
		allDependencies.add(coordinate, usage);
	}

	public void addProperties(Collection<String> propertyNames) {
		this.properties.addAll(propertyNames);
	}

	/**
	 * Execute the given callback for each dependency.
	 */
	public void doWithArtifacts(BiConsumer<ArtifactId, ArtifactUsage> callback) {
		allDependencies.forEach((coordinate, usages) -> usages.forEach(usage -> callback.accept(coordinate, usage)));
	}

	/**
	 * Register an update candidate.
	 */
	public void registerUpdateCandidate(ArtifactId artifactId, ArtifactVersion currentVersion,
			DeclarationSource declarationSource, VersionSource versionSource) {

		versionCheckCandidates.computeIfAbsent(artifactId, ac -> new Dependency(ac, currentVersion))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	/**
	 * Return whether there are no update candidates.
	 */
	public boolean isEmpty() {
		return versionCheckCandidates.isEmpty();
	}

	/**
	 * Return all update candidates.
	 */
	public Collection<Dependency> getDependencies() {
		return versionCheckCandidates.values();
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
	public @Nullable Dependency getDependency(ArtifactId artifactId) {
		return versionCheckCandidates.get(artifactId);
	}

	/**
	 * Return the dependency for the given artifact id.
	 */
	public @Nullable Dependency getDependency(String groupId, String artifactId) {
		return getDependency(ArtifactId.of(groupId, artifactId));
	}

}
