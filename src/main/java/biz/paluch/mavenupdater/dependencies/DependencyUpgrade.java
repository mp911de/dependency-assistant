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
package biz.paluch.mavenupdater.dependencies;

import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * Version and update info for a single dependency.
 */
public final class DependencyUpgrade {

	private final ArtifactCoordinates coordinates;
	private final @Nullable ArtifactVersion currentVersion;
	private final List<VersionOption> versionOptions;
	private final String scope;
	private final DependencySource source;
	private @Nullable ArtifactVersion upgradeTo;
	private boolean doUpgrade;

	public DependencyUpgrade(ArtifactCoordinates coordinates, @Nullable ArtifactVersion currentVersion,
			List<VersionOption> versionOptions, String scope, DependencySource source) {
		this.coordinates = coordinates;
		this.currentVersion = currentVersion;
		this.versionOptions = versionOptions;
		this.scope = scope;
		this.source = source;
		this.upgradeTo = currentVersion;
		this.doUpgrade = false;
	}

	public boolean hasUpgradeCandidate() {
		return currentVersion != null && !versionOptions.isEmpty()
				&& versionOptions.get(0).getVersion().isNewer(currentVersion);
	}

	public ArtifactCoordinates coordinates() {
		return coordinates;
	}

	public @Nullable ArtifactVersion currentVersion() {
		return currentVersion;
	}

	public List<VersionOption> versionOptions() {
		return versionOptions;
	}

	public String scope() {
		return scope;
	}

	public DependencySource source() {
		return source;
	}

	public @Nullable ArtifactVersion getUpgradeTo() {
		return upgradeTo;
	}

	public void setUpgradeTo(@Nullable ArtifactVersion upgradeTo) {
		this.upgradeTo = upgradeTo;
	}

	public boolean isDoUpgrade() {
		return doUpgrade;
	}

	public void setDoUpgrade(boolean doUpgrade) {
		this.doUpgrade = doUpgrade;
	}

}
