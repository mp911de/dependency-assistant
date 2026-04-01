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

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * A project dependency with its declaration sources.
 *
 * @author Mark Paluch
 */
public class Dependency implements HasArtifactId {

	private final ArtifactId artifactId;
	private final ArtifactVersion currentVersion;
	private final Set<VersionSource> versionSources = new LinkedHashSet<>();
	private final Set<DeclarationSource> declarationSources = new LinkedHashSet<>();

	public Dependency(ArtifactId artifactId, ArtifactVersion currentVersion) {
		this.artifactId = artifactId;
		this.currentVersion = currentVersion;
	}

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	public ArtifactVersion getCurrentVersion() {
		return currentVersion;
	}

	public Set<VersionSource> getVersionSources() {
		return Collections.unmodifiableSet(versionSources);
	}

	public Set<DeclarationSource> getDeclarationSources() {
		return Collections.unmodifiableSet(declarationSources);
	}

	public Dependency addVersionSource(VersionSource versionSource) {
		this.versionSources.add(versionSource);
		return this;
	}

	public Dependency addDeclarationSource(DeclarationSource declarationSource) {
		this.declarationSources.add(declarationSource);
		return this;
	}

	public boolean hasPropertyVersion() {
		return findPropertyVersion() != null;
	}

	public VersionSource.@Nullable VersionPropertySource findPropertyVersion() {

		for (VersionSource versionSource : versionSources) {
			if (versionSource instanceof VersionSource.VersionPropertySource) {
				return (VersionSource.VersionPropertySource) versionSource;
			}
		}

		return null;
	}
}
