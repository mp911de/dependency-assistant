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

import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.DependencyUpgradeSubject;
import biz.paluch.dap.upgrade.UpgradeSuggestion;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.upgrade.UpgradeSuggestionsFactory;

/**
 * The immutable set of upgrade choices for a single dependency: its
 * {@link DependencyUpgradeSubject upgrade subject} plus the per-strategy target
 * and the display-filtered release subset derived from it.
 *
 * @author Mark Paluch
 */
class DependencyUpdateCandidate implements HasArtifactId {

	private final DependencyUpgradeSubject subject;

	private final Releases releases;

	private final Releases filtered;

	private final UpgradeSuggestions targets;

	private final UpgradeSuggestions filteredTargets;

	/**
	 * Create an ungoverned update candidate, computing its targets.
	 */
	DependencyUpdateCandidate(Dependency dependency, Releases releases, VulnerabilityRepository vulnerabilities) {
		this(DependencyUpgradeSubject.of(dependency, releases, vulnerabilities, DependencyRule.absent()));
	}

	/**
	 * Create an update candidate governed by the given rule, computing its targets.
	 */
	DependencyUpdateCandidate(Dependency dependency, Releases releases, VulnerabilityRepository vulnerabilities,
			DependencyRule rule) {
		this(DependencyUpgradeSubject.of(dependency, releases, vulnerabilities, rule));
	}

	/**
	 * Create an update candidate from the given subject, computing its targets.
	 */
	DependencyUpdateCandidate(DependencyUpgradeSubject subject) {
		this(subject, suggestions(subject));
	}

	private static UpgradeSuggestions suggestions(DependencyUpgradeSubject subject) {
		return new UpgradeSuggestionsFactory(new Cache()).createSuggestions(subject);
	}

	/**
	 * Create an update candidate from the given subject and precomputed targets.
	 */
	DependencyUpdateCandidate(DependencyUpgradeSubject subject, UpgradeSuggestions suggestions) {

		this.subject = subject;

		Dependency dependency = subject.getDependency();
		Releases releases = subject.getReleases();
		if (releases.stream().map(Release::getVersion)
				.noneMatch(it -> it.matches(dependency.getCurrentVersion()))) {
			releases = releases.withRelease(new Release(dependency.getCurrentVersion(), null));
		}

		this.releases = releases;
		this.targets = suggestions;

		DependencyRule rule = subject.getRule();
		this.filteredTargets = suggestions.filter(rule::isEnabled);
		this.filtered = filterVersionSuggestions(this.releases, dependency.getCurrentVersion());
	}

	private Releases filterVersionSuggestions(Releases releases,
			ArtifactVersion current) {

		Set<Release> result = new TreeSet<>();

		for (Release release : releases) {

			if (current.matches(release.version())) {
				result.add(release);
				continue;
			}

			if (!current.isPreview() && release.version().isPreview()) {
				continue;
			}

			if (release.getVersion().isNewer(current)) {
				result.add(release);
			} else if (release.getVersion().isBugFixVersion() || release.getVersion().isReleaseVersion()) {
				result.add(release);
			}
		}

		for (UpgradeSuggestion suggestion : this.targets.getSuggestions()) {
			if (suggestion.getStrategy().isRemediation()) {
				result.add(suggestion.getRelease());
			}
		}

		return Releases.of(result);
	}

	@Override
	public ArtifactId getArtifactId() {
		return getDependency().getArtifactId();
	}

	public DependencyRule getRule() {
		return subject.getRule();
	}

	/**
	 * Return whether any automatic upgrade targets are available.
	 */
	public boolean hasUpgradeTargets() {
		return !filteredTargets.isEmpty();
	}

	public boolean hasSafeVersion() {
		return this.filteredTargets.contains(UpgradeStrategy.SAFE);
	}

	/**
	 * Return whether the declared current version carries a known vulnerability.
	 */
	public boolean isVulnerable() {
		ArtifactVersion current = getCurrentVersion();
		return current != null && getVulnerabilities(current).isVulnerable();
	}

	/**
	 * Return the dependency's current version.
	 */
	public ArtifactVersion getCurrentVersion() {
		return getDependency().getCurrentVersion();
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
	public UpgradeSuggestions getTargets() {
		return targets;
	}

	public UpgradeSuggestions getFilteredTargets() {
		return filteredTargets;
	}

	/**
	 * Return the first declaration source for the dependency.
	 */
	public DeclarationSource getDeclarationSource() {
		return getDependency().getDeclarationSources().iterator().next();
	}

	/**
	 * Return the dependency represented by this option.
	 */
	public Dependency getDependency() {
		return subject.getDependency();
	}

	/**
	 * Return whether the dependency version is backed by a property.
	 */
	public boolean hasPropertyVersion() {
		return getDependency().hasPropertyVersion();
	}

	/**
	 * Return the dependency's property-based version source.
	 */
	public VersionSource.VersionProperty getPropertyVersion() {
		return getDependency().findPropertyVersion();
	}

	public VulnerabilityRepository getVulnerabilities() {
		return subject.getVulnerabilities();
	}

	/**
	 * Return the known vulnerabilities for the given version.
	 * @param version the version to check.
	 * @return the known vulnerabilities.
	 */
	public Vulnerabilities getVulnerabilities(ArtifactVersion version) {
		return subject.getVulnerabilities().getVulnerabilities(version);
	}

	@Override
	public String toString() {
		return getArtifactId() + "@" + getCurrentVersion() + " -> ["
				+ filtered + "]";
	}


}
