/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.assistant.check;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.presentation.IconDependencyPresentation;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeSuggestion;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.upgrade.UpgradeSuggestionsFactory;
import org.jspecify.annotations.Nullable;

/**
 * Upgrade options for a collected dependency.
 * <p>This transient snapshot supplies the facts for review and application. The
 * user's choice is kept separately in the review selection. Targets must remain
 * available in the corresponding release view.
 *
 * @author Mark Paluch
 */
public class DependencyUpgradeCandidate implements HasArtifactId, HasPackageIdentity {

	private final Dependency dependency;

	private final DependencyAssistant assistant;

	private final Releases releases;

	private final VulnerabilityRepository vulnerabilities;

	private final DependencyRule rule;

	private final IconDependencyPresentation presentation;

	private final DeclaredVersions declaredVersions;

	private final UpgradeSuggestions suggestions;

	private final Releases displayReleases;

	private final UpgradeSuggestions displaySuggestions;

	private final Set<VersionProperty> versionProperties = new LinkedHashSet<>();

	private final Map<ArtifactVersion, Vulnerabilities> vulnerabilitiesByVersion = new ConcurrentHashMap<>();

	private DependencyUpgradeCandidate(Dependency dependency, DependencyAssistant assistant,
			Releases releases, VulnerabilityRepository vulnerabilities, DependencyRule rule,
			IconDependencyPresentation presentation, DeclaredVersions declaredVersions) {

		this.dependency = dependency;
		this.assistant = assistant;
		this.releases = releases.withVersion(dependency.getCurrentVersion());
		this.vulnerabilities = vulnerabilities;
		this.rule = rule;
		this.presentation = presentation;
		this.declaredVersions = declaredVersions;
		this.suggestions = UpgradeSuggestionsFactory.createSuggestions(dependency, this.releases, vulnerabilities,
				rule);
		this.displayReleases = filterDisplayReleases();
		this.displaySuggestions = suggestions.filter(this::isDisplaySuggestion);

		for (VersionSource source : dependency.getVersionSources()) {
			if (source instanceof VersionSource.VersionProperty property) {
				versionProperties.add(new VersionProperty(assistant.getId(), property.getProperty()));
			}
		}
	}

	public static DependencyUpgradeCandidate create(Dependency dependency,
			DependencyAssistant assistant, Releases releases, VulnerabilityRepository vulnerabilities,
			DependencyRule rule, IconDependencyPresentation presentation, DeclaredVersions declaredVersions) {
		return new DependencyUpgradeCandidate(dependency, assistant, releases, vulnerabilities,
				rule, presentation, declaredVersions);
	}

	private Releases filterDisplayReleases() {

		Set<Release> remediations = new HashSet<>();
		for (UpgradeSuggestion suggestion : suggestions.getSuggestions()) {
			if (suggestion.getStrategy().isRemediation()) {
				remediations.add(suggestion.getRelease());
			}
		}

		ArtifactVersion current = getCurrentVersion();
		return releases.filter(release -> release.isUpgradeCandidate(current) || remediations.contains(release));
	}

	private boolean isDisplaySuggestion(UpgradeStrategy strategy) {

		UpgradeSuggestion suggestion = suggestions.get(strategy);
		return rule.isEnabled(strategy) && suggestion.isPresent() && displayReleases.contains(suggestion.getRelease());
	}

	@Override
	public PackageIdentity getPackageIdentity() {
		return dependency.getPackageIdentity();
	}

	@Override
	public ArtifactId getArtifactId() {
		return dependency.getArtifactId();
	}

	public Dependency getDependency() {
		return dependency;
	}

	public DependencyAssistant getAssistant() {
		return assistant;
	}

	/**
	 * Return the version in use when this candidate was created.
	 */
	public ArtifactVersion getCurrentVersion() {
		return dependency.getCurrentVersion();
	}

	/**
	 * Return the known releases, including the current version.
	 */
	public Releases getReleases() {
		return releases;
	}

	/**
	 * Return releases suitable for review.
	 * <p>Previews are omitted for stable current versions, except remediation
	 * targets. The current release is retained.
	 */
	public Releases getDisplayReleases() {
		return displayReleases;
	}

	/**
	 * Return policy suggestions in strategy priority order.
	 */
	public UpgradeSuggestions getSuggestions() {
		return suggestions;
	}

	/**
	 * Return the mutable version-property set in version-source order.
	 */
	public Set<VersionProperty> getVersionProperties() {
		return versionProperties;
	}

	public DependencyRule getRule() {
		return rule;
	}

	public IconDependencyPresentation getPresentation() {
		return presentation;
	}

	public DeclaredVersions getDeclaredVersions() {
		return declaredVersions;
	}

	public VulnerabilityRepository getVulnerabilities() {
		return vulnerabilities;
	}

	/**
	 * Return vulnerabilities sampled on first access and retained for this
	 * candidate.
	 */
	public Vulnerabilities getVulnerabilities(ArtifactVersion version) {
		return vulnerabilitiesByVersion.computeIfAbsent(version, vulnerabilities::getVulnerabilities);
	}

	public boolean isVulnerable() {
		return getVulnerabilities(getCurrentVersion()).isVulnerable();
	}

	/**
	 * Return whether an automatic target is available in the display view.
	 */
	public boolean hasUpgradeTargets() {
		return !displaySuggestions.isEmpty();
	}

	/**
	 * Return the strategy target, or {@literal null} if none is available.
	 */
	public @Nullable Release findRelease(UpgradeStrategy strategy) {
		return resolveRelease(strategy, suggestions, releases);
	}

	/**
	 * Return the strategy target offered for display, or {@literal null} if none.
	 */
	public @Nullable Release findCuratedRelease(UpgradeStrategy strategy) {
		return resolveRelease(strategy, displaySuggestions, displayReleases);
	}

	private static @Nullable Release resolveRelease(UpgradeStrategy strategy, UpgradeSuggestions suggestions,
			Releases releases) {

		UpgradeSuggestion suggestion = suggestions.get(strategy);
		if (suggestion.isPresent() && releases.contains(suggestion.getRelease())) {
			return suggestion.getRelease();
		}
		return null;
	}

	/**
	 * Create an update retaining the dependency's declaration and version sources.
	 */
	public DependencyUpdate createUpdate(ArtifactVersion target) {
		return DependencyUpdate.from(dependency, target);
	}

	@Override
	public String toString() {
		return getArtifactId() + "@" + getCurrentVersion() + " -> [" + displayReleases + "]";
	}

}
