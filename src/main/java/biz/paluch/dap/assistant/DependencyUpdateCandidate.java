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
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionSource;
import org.jspecify.annotations.Nullable;

/**
 * The immutable set of upgrade choices for a single dependency: every known
 * release, the display-filtered subset, and the per-strategy target.
 *
 * <p>This is what the dialog <em>can</em> offer for a row, not what the user
 * has picked. The pick lives in an {@link UpgradeSelection} owned by
 * {@link UpgradeReview}.
 *
 * @author Mark Paluch
 */
class DependencyUpdateCandidate implements HasArtifactId {

	private final Dependency dependency;

	private final Releases releases;

	private final Releases filtered;

	private final Map<UpgradeStrategy, Release> targets;

	private final Map<UpgradeStrategy, Release> filteredTargets;

	/**
	 * Create a new {@code DependencyUpdateOption}.
	 * @param dependency the dependency to update.
	 * @param releases the known release options.
	 */
	DependencyUpdateCandidate(Dependency dependency, Releases releases) {
		this.dependency = dependency;

		if (releases.stream().map(Release::getVersion)
				.noneMatch(it -> it.matches(dependency.getCurrentVersion()))) {
			releases = releases.withRelease(new Release(dependency.getCurrentVersion(), null));
		}

		this.releases = releases;
		this.filtered = filterVersionSuggestions(this.releases, dependency.getCurrentVersion());
		this.targets = new LinkedHashMap<>();
		this.filteredTargets = new LinkedHashMap<>();

		for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
			Release option = strategy.select(getCurrentVersion(), this.releases);
			if (option == null) {
				continue;
			}

			if (option.getVersion().canCompare(getCurrentVersion())
					&& option.getVersion().compareTo(getCurrentVersion()) == 0) {
				continue;
			}

			if (!option.version().equals(getCurrentVersion())) {
				targets.put(strategy, option);
			}
		}

		filteredTargets.putAll(targets);
	}

	private Releases filterVersionSuggestions(Releases releases,
			ArtifactVersion current) {

		Set<Release> result = new TreeSet<>(Comparator.reverseOrder());
		List<Release> newer = new ArrayList<>();
		List<Release> older = new ArrayList<>();

		for (Release release : releases) {

			if (current.matches(release.version())) {
				result.add(release);
				continue;
			}

			if (!current.isPreview() && release.version().isPreview()) {
				continue;
			}

			doAdd(current, release, newer, older);
		}

		result.addAll(older.reversed());
		result.addAll(newer);

		return Releases.of(result);
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
		return !filteredTargets.isEmpty();
	}

	/**
	 * Return the dependency's current version.
	 */
	public ArtifactVersion getCurrentVersion() {
		return dependency.getCurrentVersion();
	}

	/**
	 * Return all known release options.
	 */
	public Releases getReleases() {
		return releases;
	}

	/**
	 * Return release options suitable for display in the update dialog.
	 */
	public Releases getFilteredReleases() {
		return filtered;
	}

	/**
	 * Return automatically selected targets by upgrade strategy.
	 */
	public Map<UpgradeStrategy, Release> getTargets() {
		return targets;
	}

	public Map<UpgradeStrategy, Release> getFilteredTargets() {
		return filteredTargets;
	}

	/**
	 * Return the first declaration source for the dependency.
	 */
	public DeclarationSource getDeclarationSource() {
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

	@Override
	public String toString() {
		return dependency.getArtifactId() + "@" + getCurrentVersion() + " -> ["
				+ filtered + "]";
	}

}
