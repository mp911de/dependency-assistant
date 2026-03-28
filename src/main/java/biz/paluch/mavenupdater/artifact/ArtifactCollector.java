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
package biz.paluch.mavenupdater.artifact;

import java.util.Collection;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import org.springframework.util.MultiValueMap;
import org.springframework.util.MultiValueMapAdapter;

/**
 * @author Mark Paluch
 */
public class ArtifactCollector {

	private final MultiValueMap<ArtifactCoordinate, ArtifactUsage> allDependencies = new MultiValueMapAdapter<>(
			new TreeMap<>());
	private final Map<ArtifactCoordinate, VersionCheckCandidate> versionCheckCandidates = new TreeMap<>();

	public void add(ArtifactCoordinate coordinate, ArtifactUsage usage) {
		allDependencies.add(coordinate, usage);
	}

	public void doWithArtifacts(BiConsumer<ArtifactCoordinate, ArtifactUsage> callback) {
		allDependencies.forEach((coordinate, usages) -> usages.forEach(usage -> callback.accept(coordinate, usage)));
	}

	public void registerUpdateCandidate(ArtifactCoordinate artifactCoordinate, ArtifactVersion currentVersion,
			DeclarationSource declarationSource, VersionSource versionSource) {

		versionCheckCandidates.computeIfAbsent(artifactCoordinate, ac -> new VersionCheckCandidate(ac, currentVersion))
				.addDeclarationSource(declarationSource).addVersionSource(versionSource);
	}

	public boolean isEmpty() {
		return versionCheckCandidates.isEmpty();
	}

	public Collection<VersionCheckCandidate> getVersionCheckCandidates() {
		return versionCheckCandidates.values();
	}

}
