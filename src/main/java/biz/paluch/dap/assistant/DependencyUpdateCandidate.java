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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionSource;
import org.jspecify.annotations.Nullable;

/**
 * The immutable set of upgrade choices for a single dependency: every known
 * release, the display-filtered subset, and the per-strategy target.
 *
 * <p>This is what the dialog <em>can</em> offer for a row, not what the user
 * has picked. The pick lives in an {@link UpgradeSelection} owned by
 * {@link DependencyUpgradeReview}.
 *
 * @author Mark Paluch
 */
class DependencyUpdateCandidate implements HasArtifactId {

	private final Dependency dependency;

	private final List<Release> releases;

	private final List<Release> filtered;

	private final Map<UpgradeStrategy, Release> targets;

	/**
	 * Create a new {@code DependencyUpdateOption}.
	 * @param dependency the dependency to update.
	 * @param releases the known release options.
	 */
	DependencyUpdateCandidate(Dependency dependency, List<Release> releases) {
		this.dependency = dependency;
		this.releases = new ArrayList<>(releases);

		if (releases.stream().map(Release::getVersion)
				.noneMatch(it -> it.matches(dependency.getCurrentVersion()))) {
			this.releases.add(new Release(dependency.getCurrentVersion(), null));
			this.releases.sort(Comparator.reverseOrder());
		}

		this.filtered = filterVersionSuggestions(this.releases, dependency.getCurrentVersion());
		this.targets = new LinkedHashMap<>();

		for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
			Release option = strategy.select(currentVersion(), this.releases);
			if (option != null && !option.version().equals(currentVersion())) {
				targets.put(strategy, option);
			}
		}
	}

	private List<Release> filterVersionSuggestions(Collection<Release> versions,
			ArtifactVersion current) {

		Set<Release> result = new TreeSet<>(Comparator.reverseOrder());
		List<Release> newer = new ArrayList<>();
		List<Release> older = new ArrayList<>();

		for (Release version : versions.stream().sorted().toList()) {

			if (current.matches(version.version())) {
				result.add(version);
				continue;
			}

			if (!current.isPreview() && version.version().isPreview()) {
				continue;
			}

			doAdd(current, version, newer, older);
		}

		result.addAll(older.reversed());
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
		return !releases.isEmpty() && releases.get(0).version()
				.isNewer(dependency.getCurrentVersion()) && !getTargets().isEmpty();
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
