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

package biz.paluch.dap.assistant.check;

import java.util.HashSet;
import java.util.Set;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeSuggestion;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.upgrade.UpgradeSuggestionsFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A collected dependency's complete upgrade picture: the dependency, its
 * release universe, governing rule, vulnerabilities, computed suggestions,
 * declared versions, and the assistant that can apply it, gathered in one
 * consistent release universe.
 *
 * <p>The aggregate is the basis for an upgrade decision, not the decision
 * itself: it owns everything needed to offer and apply an upgrade, while the
 * user's actual pick lives in {@code UpgradeSelection}. Strategy targets are
 * resolved through the matching release view so callers cannot select a
 * suggestion whose release is no longer available.
 *
 * <p>An aggregate is created for one dependency check. Suggestions and display
 * views are computed during creation and retained with the supplied dependency,
 * rule, vulnerability repository, declared versions, and assistant.
 *
 * @author Mark Paluch
 */
public class DependencyUpgradeCandidate implements HasArtifactId {

	private final Dependency dependency;

	private final Releases releases;

	private final VulnerabilityRepository vulnerabilities;

	private final DependencyRule rule;

	private final InterfaceAssistant assistant;

	private final DeclaredVersions declaredVersions;

	private final UpgradeSuggestions suggestions;

	private final Releases displayReleases;

	private final UpgradeSuggestions displaySuggestions;

	private DependencyUpgradeCandidate(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities,
			DependencyRule rule, InterfaceAssistant assistant, DeclaredVersions declaredVersions) {

		this.dependency = dependency;
		this.releases = releases.withVersion(dependency.getCurrentVersion());
		this.vulnerabilities = vulnerabilities;
		this.rule = rule;
		this.assistant = assistant;
		this.declaredVersions = declaredVersions;
		this.suggestions = UpgradeSuggestionsFactory.createSuggestions(dependency, this.releases, vulnerabilities,
				rule);
		this.displayReleases = filterDisplayReleases();
		this.displaySuggestions = suggestions.filter(this::isDisplaySuggestion);
	}

	/**
	 * Create an ungoverned upgrade for the given dependency.
	 *
	 * @param dependency the collected dependency to upgrade.
	 * @param releases the known releases for the dependency.
	 * @param vulnerabilities the vulnerability results for known versions.
	 * @param assistant the assistant that can apply the upgrade.
	 * @param declaredVersions the versions the dependency is declared at.
	 * @return an upgrade governed by the absent dependency rule.
	 */
	public static DependencyUpgradeCandidate create(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities, InterfaceAssistant assistant, DeclaredVersions declaredVersions) {
		return create(dependency, releases, vulnerabilities, DependencyRule.absent(), assistant, declaredVersions);
	}

	/**
	 * Create an upgrade governed by the given rule.
	 *
	 * @param dependency the collected dependency to upgrade.
	 * @param releases the known releases for the dependency.
	 * @param vulnerabilities the vulnerability results for known versions.
	 * @param rule the governing dependency rule.
	 * @param assistant the assistant that can apply the upgrade.
	 * @param declaredVersions the versions the dependency is declared at.
	 * @return an upgrade with suggestions and display views computed from the
	 * supplied facts.
	 */
	public static DependencyUpgradeCandidate create(Dependency dependency, Releases releases,
			VulnerabilityRepository vulnerabilities, DependencyRule rule, InterfaceAssistant assistant,
			DeclaredVersions declaredVersions) {
		return new DependencyUpgradeCandidate(dependency, releases, vulnerabilities, rule, assistant, declaredVersions);
	}

	private Releases filterDisplayReleases() {

		Set<Release> remediations = new HashSet<>();
		for (UpgradeSuggestion suggestion : suggestions.getSuggestions()) {
			if (suggestion.getStrategy().isRemediation()) {
				remediations.add(suggestion.getRelease());
			}
		}

		ArtifactVersion current = getCurrentVersion();
		return releases.filter(release -> isDisplayable(release, current) || remediations.contains(release));
	}

	private boolean isDisplayable(Release release, ArtifactVersion current) {

		if (current.matches(release.version())) {
			return true;
		}

		if (!current.isPreview() && release.version().isPreview()) {
			return false;
		}

		return release.getVersion().isNewer(current) || release.getVersion().isBugFixVersion()
				|| release.getVersion().isReleaseVersion();
	}

	private boolean isDisplaySuggestion(UpgradeStrategy strategy) {

		UpgradeSuggestion suggestion = suggestions.get(strategy);
		return rule.isEnabled(strategy) && suggestion.isPresent() && displayReleases.contains(suggestion.getRelease());
	}

	@Override
	public ArtifactId getArtifactId() {
		return dependency.getArtifactId();
	}

	/**
	 * Return the collected dependency represented by this upgrade.
	 *
	 * @return the dependency retained by this upgrade.
	 */
	public Dependency getDependency() {
		return dependency;
	}

	/**
	 * Return the dependency version in use when this upgrade was created.
	 *
	 * @return the current dependency version.
	 */
	public ArtifactVersion getCurrentVersion() {
		return dependency.getCurrentVersion();
	}

	/**
	 * Return all known releases, including the current dependency version.
	 *
	 * @return the release universe used to compute suggestions.
	 */
	public Releases getReleases() {
		return releases;
	}

	/**
	 * Return releases suitable for the dependency check dialog.
	 *
	 * <p>The result omits preview noise for a stable current version while
	 * retaining the current release and remediation targets.
	 *
	 * @return the display release view.
	 */
	public Releases getDisplayReleases() {
		return displayReleases;
	}

	/**
	 * Return all policy suggestions in strategy priority order.
	 *
	 * @return the computed upgrade suggestions.
	 */
	public UpgradeSuggestions getSuggestions() {
		return suggestions;
	}

	/**
	 * Return suggestions enabled for the dependency check dialog.
	 *
	 * @return the display suggestion view.
	 */
	public UpgradeSuggestions getDisplaySuggestions() {
		return displaySuggestions;
	}

	/**
	 * Return the rule governing this upgrade.
	 *
	 * @return the resolved dependency rule.
	 */
	public DependencyRule getRule() {
		return rule;
	}

	/**
	 * Return the assistant that can apply this upgrade.
	 *
	 * @return the ecosystem assistant bound to the dependency.
	 */
	public InterfaceAssistant getAssistant() {
		return assistant;
	}

	/**
	 * Return the versions the dependency is declared at across its declaration
	 * sites.
	 *
	 * @return the declared-version facts, used for drift reporting and grouping.
	 */
	public DeclaredVersions getDeclaredVersions() {
		return declaredVersions;
	}

	/**
	 * Return the vulnerability repository used by this upgrade.
	 *
	 * @return the supplied vulnerability repository.
	 */
	public VulnerabilityRepository getVulnerabilities() {
		return vulnerabilities;
	}

	/**
	 * Return the known vulnerability state for the given version.
	 *
	 * @param version the version to inspect.
	 * @return the known vulnerabilities for the version.
	 */
	public Vulnerabilities getVulnerabilities(ArtifactVersion version) {
		return vulnerabilities.getVulnerabilities(version);
	}

	/**
	 * Return whether the current dependency version is known to be vulnerable.
	 *
	 * @return {@code true} if the current version is vulnerable; {@code false}
	 * otherwise.
	 */
	public boolean isVulnerable() {
		return getVulnerabilities(getCurrentVersion()).isVulnerable();
	}

	/**
	 * Return whether the display view contains an automatic upgrade target.
	 *
	 * @return {@code true} if at least one display suggestion is available;
	 * {@code false} otherwise.
	 */
	public boolean hasUpgradeTargets() {
		return !displaySuggestions.isEmpty();
	}

	/**
	 * Resolve an unfiltered strategy target through the upgrade's releases.
	 *
	 * @param strategy the strategy whose target should be resolved.
	 * @return the target release, or {@literal null} if the strategy has no valid
	 * target.
	 */
	public @Nullable Release resolveTarget(UpgradeStrategy strategy) {
		return resolveTarget(strategy, suggestions, releases);
	}

	/**
	 * Resolve a display strategy target through the display releases.
	 *
	 * @param strategy the strategy whose visible target should be resolved.
	 * @return the target release, or {@literal null} if the strategy has no visible
	 * target.
	 */
	public @Nullable Release resolveDisplayTarget(UpgradeStrategy strategy) {
		return resolveTarget(strategy, displaySuggestions, displayReleases);
	}

	private static @Nullable Release resolveTarget(UpgradeStrategy strategy, UpgradeSuggestions suggestions,
			Releases releases) {

		UpgradeSuggestion suggestion = suggestions.get(strategy);
		if (suggestion.isPresent() && releases.contains(suggestion.getRelease())) {
			return suggestion.getRelease();
		}
		return null;
	}

	/**
	 * Select a target from this upgrade's release universe.
	 *
	 * <p>The returned release is the canonical release retained by this upgrade.
	 * Callers may keep the selected version as presentation state, while target
	 * validity remains owned here.
	 *
	 * @param target the target version to select.
	 * @return the matching release retained by this upgrade.
	 * @throws IllegalArgumentException if the target is not part of this upgrade.
	 */
	public Release selectTarget(ArtifactVersion target) {

		Release release = releases.getRelease(target);
		Assert.notNull(release, "Target version is not part of the upgrade: " + target);
		return release;
	}

	/**
	 * Create the apply-ready update for the selected target.
	 *
	 * @param target the selected target version.
	 * @return an update carrying the dependency's declaration and version sources.
	 */
	public DependencyUpdate createUpdate(ArtifactVersion target) {
		return DependencyUpdate.from(dependency, selectTarget(target).version());
	}

	/**
	 * Create the apply-ready update with a replacement artifact identity used for
	 * rendering.
	 *
	 * @param artifactId the artifact identity to expose from the update.
	 * @param target the selected target version.
	 * @return an update carrying the dependency's declaration and version sources.
	 */
	public DependencyUpdate createUpdate(ArtifactId artifactId, ArtifactVersion target) {
		return DependencyUpdate.from(artifactId, dependency, selectTarget(target).version());
	}

	@Override
	public String toString() {
		return getArtifactId() + "@" + getCurrentVersion() + " -> [" + displayReleases + "]";
	}

}
