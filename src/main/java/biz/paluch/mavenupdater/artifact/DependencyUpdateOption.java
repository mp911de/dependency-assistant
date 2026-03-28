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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;

/**
 * Version and update info for a single dependency.
 */
public final class DependencyUpdateOption {

	private final VersionCheckCandidate candidate;
	private final List<VersionOption> versionOptions;
	private final List<VersionOption> filtered;
	private @Nullable ArtifactVersion updateTo;
	private final Map<UpgradeStrategy, VersionOption> targets;
	private boolean applyUpdate;

	public DependencyUpdateOption(VersionCheckCandidate candidate, List<VersionOption> versionOptions) {
		this.candidate = candidate;
		this.versionOptions = versionOptions;
		this.filtered = filterVersionSuggestions(versionOptions, candidate.getCurrentVersion());
		this.updateTo = candidate.getCurrentVersion();
		this.applyUpdate = false;
		this.targets = new LinkedHashMap<>();

		for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
			VersionOption option = strategy.select(currentVersion(), versionOptions);
			if (option != null && !option.version().equals(currentVersion())) {
				targets.put(strategy, option);
			}
		}
	}

	private List<VersionOption> filterVersionSuggestions(Collection<VersionOption> versions,
			@Nullable ArtifactVersion current) {

		Set<VersionOption> result = new TreeSet<>(Comparator.reverseOrder());
		List<VersionOption> newer = new ArrayList<>();
		List<VersionOption> older = new ArrayList<>();

		for (VersionOption version : versions.stream().sorted().toList()) {

			if (current != null && current.equals(version.version())) {
				result.add(version);
			}

			if (version.version().isPreview()) {
				continue;
			}

			doAdd(current, version, newer, older);
		}

		result.addAll(older.reversed().stream().limit(10).toList());
		result.addAll(newer);

		return List.copyOf(result);
	}

	private static void doAdd(@Nullable ArtifactVersion current, VersionOption version, List<VersionOption> newer,
			List<VersionOption> older) {
		if (current == null || version.isNewer(current)) {
			newer.add(version);
		} else if (version.isBugFixVersion() || version.isReleaseVersion()) {
			older.add(version);
		}
	}

	public boolean hasUpgradeTargets() {
		return !targets.isEmpty();
	}

	public boolean hasUpdateCandidate() {
		return !versionOptions.isEmpty() && versionOptions.get(0).version().isNewer(candidate.getCurrentVersion());
	}

	public ArtifactCoordinate coordinates() {
		return candidate.getCoordinate();
	}

	public ArtifactVersion currentVersion() {
		return candidate.getCurrentVersion();
	}

	public List<VersionOption> versionOptions() {
		return versionOptions;
	}

	public List<VersionOption> filtered() {
		return filtered;
	}

	public DeclarationSource source() {
		return candidate.getDeclarationSources().iterator().next();
	}

	public @Nullable ArtifactVersion getUpdateTo() {
		return updateTo;
	}

	public ArtifactVersion getRequiredUpdateTo() {

		if (updateTo == null) {
			throw new IllegalStateException("Update version for " + coordinates() + " is required but not set");
		}
		return updateTo;
	}

	public void setUpdateTo(@Nullable ArtifactVersion updateTo) {
		this.updateTo = updateTo;
	}

	public boolean isApplyUpdate() {
		return applyUpdate;
	}

	public void setApplyUpdate(boolean applyUpdate) {
		this.applyUpdate = applyUpdate;
	}

	public VersionCheckCandidate getCandidate() {
		return candidate;
	}

	public boolean hasPropertyVersion() {
		return candidate.hasPropertyVersion();
	}

	public VersionSource.VersionPropertySource getPropertyVersion() {
		return candidate.findPropertyVersion();
	}

	public Map<UpgradeStrategy, VersionOption> getTargets() {
		return targets;
	}

	@Override
	public String toString() {
		return candidate.getCoordinate() + ": " + currentVersion() + " -> ["
				+ filtered.stream().map(VersionOption::version).map(Object::toString).collect(Collectors.joining(", ")) + "]";
	}
}
