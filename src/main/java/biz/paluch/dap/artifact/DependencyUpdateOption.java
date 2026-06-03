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
 *
 * @author Mark Paluch
 */
public class DependencyUpdateOption implements HasArtifactId {

	private final Dependency dependency;
	private final List<Release> releases;
	private final List<Release> filtered;
	private @Nullable ArtifactVersion updateTo;
	private final Map<UpgradeStrategy, Release> targets;
	private boolean applyUpdate;

	/**
	 * Create a new {@code DependencyUpdateOption}.
	 * @param dependency the dependency to update.
	 * @param releases the known release options.
	 */
	public DependencyUpdateOption(Dependency dependency, List<Release> releases) {
		this.dependency = dependency;
		this.releases = new ArrayList<>(releases);

		if (releases.stream().map(Release::getVersion).noneMatch(it -> it.equals(dependency.getCurrentVersion())
				|| it.toString().equals(dependency.getCurrentVersion().toString()))) {
			this.releases.add(new Release(dependency.getCurrentVersion(), null));
			this.releases.sort(Comparator.reverseOrder());
		}

		this.filtered = filterVersionSuggestions(this.releases, dependency.getCurrentVersion());
		this.updateTo = dependency.getCurrentVersion();
		this.applyUpdate = false;
		this.targets = new LinkedHashMap<>();

		for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
			Release option = strategy.select(currentVersion(), this.releases);
			if (option != null && !option.version().equals(currentVersion())) {
				targets.put(strategy, option);
			}
		}
	}

	private List<Release> filterVersionSuggestions(Collection<Release> versions,
			@Nullable ArtifactVersion current) {

		Set<Release> result = new TreeSet<>(Comparator.reverseOrder());
		List<Release> newer = new ArrayList<>();
		List<Release> older = new ArrayList<>();

		for (Release version : versions.stream().sorted().toList()) {

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

	private static void doAdd(@Nullable ArtifactVersion current, Release version, List<Release> newer,
			List<Release> older) {
		if (current == null || version.isNewer(current)) {
			newer.add(version);
		} else if (version.isBugFixVersion() || version.isReleaseVersion()) {
			older.add(version);
		}
	}

	@Override
	public ArtifactId getArtifactId() {
		return this.dependency.getArtifactId();
	}

	/**
	 * Return whether any automatic upgrade targets are available.
	 */
	public boolean hasUpgradeTargets() {
		return !targets.isEmpty();
	}

	/**
	 * Return whether the newest known release is newer than the current version.
	 */
	public boolean hasUpdateCandidate() {
		return !releases.isEmpty() && releases.get(0).version().isNewer(dependency.getCurrentVersion());
	}

	/**
	 * Return the dependency's current version.
	 */
	public ArtifactVersion currentVersion() {
		return dependency.getCurrentVersion();
	}

	/**
	 * Return all known release options.
	 */
	public List<Release> versionOptions() {
		return releases;
	}

	/**
	 * Return release options suitable for display in the update dialog.
	 */
	public List<Release> filtered() {
		return filtered;
	}

	/**
	 * Return the first declaration source for the dependency.
	 */
	public DeclarationSource source() {
		return dependency.getDeclarationSources().iterator().next();
	}

	/**
	 * Return the selected target version, or {@literal null} if none is selected.
	 */
	public @Nullable ArtifactVersion getUpdateTo() {
		return updateTo;
	}

	/**
	 * Return the selected target version.
	 * @throws IllegalStateException if no target version is selected.
	 */
	public ArtifactVersion getRequiredUpdateTo() {

		if (updateTo == null) {
			throw new IllegalStateException(
					"Update version for " + getArtifactId().artifactId() + " is required but not set");
		}
		return updateTo;
	}

	/**
	 * Set the selected target version.
	 * @param updateTo the selected target version, or {@literal null}.
	 */
	public void setUpdateTo(@Nullable ArtifactVersion updateTo) {
		this.updateTo = updateTo;
		setApplyUpdate(!currentVersion().equals(updateTo));
	}

	/**
	 * Return whether this option should be applied.
	 */
	public boolean isApplyUpdate() {
		return applyUpdate;
	}

	/**
	 * Set whether this option should be applied.
	 */
	public void setApplyUpdate(boolean applyUpdate) {
		this.applyUpdate = applyUpdate;
	}

	/**
	 * Return the dependency represented by this option.
	 */
	public Dependency getDependency() {
		return dependency;
	}

	/**
	 * Return whether the dependency version is backed by a property.
	 */
	public boolean hasPropertyVersion() {
		return dependency.hasPropertyVersion();
	}

	/**
	 * Return the dependency's property-based version source.
	 */
	public VersionSource.VersionProperty getPropertyVersion() {
		return dependency.findPropertyVersion();
	}

	/**
	 * Return automatically selected targets by upgrade strategy.
	 */
	public Map<UpgradeStrategy, Release> getTargets() {
		return targets;
	}

	@Override
	public String toString() {
		return dependency.getArtifactId() + ": " + currentVersion() + " -> ["
				+ filtered.stream().map(Release::version).map(Object::toString).collect(Collectors.joining(", ")) + "]";
	}
}
