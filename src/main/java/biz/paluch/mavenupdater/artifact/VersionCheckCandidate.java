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

import java.util.LinkedHashSet;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * @author Mark Paluch
 */
public class VersionCheckCandidate {

	private final ArtifactCoordinate coordinate;
	private final ArtifactVersion currentVersion;
	private final Set<VersionSource> versionSources = new LinkedHashSet<>();
	private final Set<DeclarationSource> declarationSources = new LinkedHashSet<>();

	public VersionCheckCandidate(ArtifactCoordinate coordinate, ArtifactVersion currentVersion) {
		this.coordinate = coordinate;
		this.currentVersion = currentVersion;
	}

	public ArtifactCoordinate getCoordinate() {
		return coordinate;
	}

	public ArtifactVersion getCurrentVersion() {
		return currentVersion;
	}

	public Set<VersionSource> getVersionSources() {
		return versionSources;
	}

	public Set<DeclarationSource> getDeclarationSources() {
		return declarationSources;
	}

	public VersionCheckCandidate addVersionSource(VersionSource versionSource) {
		this.versionSources.add(versionSource);
		return this;
	}

	public VersionCheckCandidate addDeclarationSource(DeclarationSource declarationSource) {
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
