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
 * A dependency with its declaration sources.
 *
 * @author Mark Paluch
 */
public class DeclaredDependency implements HasArtifactId {

	private final ArtifactId artifactId;

	private final Set<VersionSource> versionSources = new LinkedHashSet<>();

	private final Set<DeclarationSource> declarationSources = new LinkedHashSet<>();

	public DeclaredDependency(ArtifactId artifactId) {
		this.artifactId = artifactId;
	}

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	public Set<VersionSource> getVersionSources() {
		return Collections.unmodifiableSet(versionSources);
	}

	public Set<DeclarationSource> getDeclarationSources() {
		return Collections.unmodifiableSet(declarationSources);
	}

	public DeclaredDependency addVersionSource(VersionSource versionSource) {
		this.versionSources.add(versionSource);
		return this;
	}

	public DeclaredDependency addDeclarationSource(DeclarationSource declarationSource) {
		this.declarationSources.add(declarationSource);
		return this;
	}

	public boolean hasPropertyVersion() {
		return findPropertyVersion() != null;
	}

	public VersionSource.@Nullable VersionProperty findPropertyVersion() {

		for (VersionSource versionSource : versionSources) {
			if (versionSource instanceof VersionSource.VersionProperty) {
				return (VersionSource.VersionProperty) versionSource;
			}
		}

		return null;
	}

}
