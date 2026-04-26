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
package biz.paluch.dap.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.ProjectBuildContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.VersionProperty;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Base class for build-tool specific version-upgrade lookups.
 * <p>This type implements the common cache and upgrade-selection workflow used
 * by editor features that need to determine whether a PSI element refers to an
 * outdated dependency version. Subclasses are responsible for recognizing
 * build-file syntax and translating a {@link PsiElement} into an
 * {@link ArtifactReference}; this base class owns project-state access, cached
 * release lookup, and conversion of releases into upgrade suggestions.
 * <p>The implementation deliberately separates parsing from ranking. A subclass
 * should not decide whether an upgrade is major, minor, or patch, and lookup
 * methods should not trigger remote repository access. If an element cannot be
 * resolved cheaply and completely from the current PSI and project state,
 * return {@link ArtifactReference#unresolved()} or
 * {@link AvailableUpgrades#none()}.
 * <p>Subclasses typically implement only
 * {@link #resolveArtifactReference(PsiElement)}. Override
 * {@link #suggestUpgrades(PsiElement)} only when an integration needs an
 * inexpensive syntactic guard before resolving the reference, and delegate to
 * {@link #suggestUpgrades(Cache, ArtifactReference)} once the reference is
 * available.
 *
 * @author Mark Paluch
 * @see ArtifactReference
 * @see AvailableUpgrades
 * @see UpgradeSuggestion
 */
public abstract class VersionUpgradeLookupSupport {

	private final @Nullable ProjectState projectState;

	private final ProjectBuildContext buildContext;

	private final Cache cache;

	/**
	 * Create a new {@code VersionUpgradeLookupSupport} instance.
	 *
	 * @param project the IntelliJ project that owns the lookup.
	 * @param buildContext the build context.
	 */
	public VersionUpgradeLookupSupport(Project project, ProjectBuildContext buildContext) {

		DependencyAssistantService service = DependencyAssistantService.getInstance(project);
		this.cache = service.getCache();
		this.projectState = buildContext.isAvailable() ? service.getProjectState(buildContext.getProjectId()) : null;
		this.buildContext = buildContext;
	}

	/**
	 * Return the shared release cache used by this lookup.
	 *
	 * @return the release cache.
	 */
	public Cache getCache() {
		return this.cache;
	}

	/**
	 * Return the cached project state associated with this lookup.
	 * <p>Call {@link #hasCachedState()} first when the lookup may have been created
	 * for a file outside a recognized build context.
	 *
	 * @return the cached project state.
	 * @throws IllegalStateException if the build context did not provide project
	 * state.
	 */
	public ProjectState getProjectState() {

		Assert.state(this.projectState != null,
				"Project state is not available for project " + buildContext.getProjectId());
		return this.projectState;
	}

	/**
	 * Return whether this lookup has an associated {@link ProjectState}.
	 *
	 * @return {@literal true} if project state is available.
	 */
	public boolean hasCachedState() {
		return projectState != null;
	}

	/**
	 * Return the current version of the first artifact associated with the given
	 * property.
	 * <p>Properties can be associated with more than one artifact. This convenience
	 * method follows the existing project-state ordering and inspects only the
	 * first recorded association.
	 *
	 * @param property the property whose artifact association should be inspected.
	 * @return the current artifact version, or {@code null} if the property has no
	 * artifact association or project state does not contain the dependency.
	 */
	public @Nullable ArtifactVersion getCurrentVersion(VersionProperty property) {

		if (property.artifacts().isEmpty()) {
			return null;
		}
		return getCurrentVersion(property.artifacts().iterator().next().toArtifactId());
	}

	/**
	 * Return the current version of the dependency with the given artifact id.
	 *
	 * @param artifactId the artifact id to locate.
	 * @return the current artifact version, or {@code null} if project state is
	 * unavailable or the dependency is not known.
	 */
	public @Nullable ArtifactVersion getCurrentVersion(ArtifactId artifactId) {

		if (projectState == null) {
			return null;
		}

		Dependency dependency = projectState.findDependency(artifactId);
		return dependency != null ? dependency.getCurrentVersion() : null;
	}

	/**
	 * Return the default single upgrade suggestion for the given PSI element.
	 * <p>This method delegates to {@link #suggestUpgrades(PsiElement)} and returns
	 * the default suggestion exposed by {@link AvailableUpgrades}. It never returns
	 * {@code null}; callers should use {@link UpgradeSuggestion#isPresent()} to
	 * distinguish an absent suggestion.
	 *
	 * @param element the PSI element under inspection.
	 * @return the default upgrade suggestion, or {@link UpgradeSuggestion#none()}.
	 */
	public final UpgradeSuggestion suggestUpgrade(PsiElement element) {
		return suggestUpgrades(element).getUpgradeSuggestion();
	}

	/**
	 * Resolve available upgrades for the given PSI element.
	 * <p>The default implementation resolves an {@link ArtifactReference} through
	 * {@link #resolveArtifactReference(PsiElement)} and evaluates that reference
	 * against the shared cache. Subclasses may override this method to perform
	 * cheap token/file checks before resolving, but should preserve the cache-only
	 * semantics and return {@link AvailableUpgrades#none()} rather than
	 * {@code null}.
	 *
	 * @param element the PSI element under inspection.
	 * @return the available upgrades, or {@link AvailableUpgrades#none()}.
	 */
	public AvailableUpgrades suggestUpgrades(PsiElement element) {
		return suggestUpgrades(this.cache, resolveArtifactReference(element));
	}

	/**
	 * Resolve the given PSI element into artifact declaration metadata.
	 * <p>Implementations should treat every element as optional input: unsupported
	 * locations, incomplete PSI, unavailable project state, and unresolved property
	 * or catalog indirection should result in
	 * {@link ArtifactReference#unresolved()} rather than an exception.
	 * <p>A resolved reference must provide an {@link ArtifactDeclaration} with the
	 * artifact id, declaration element, version source, and current version. When
	 * the version is defined indirectly, for example through a property or version
	 * catalog entry, implementations should also provide the version literal so
	 * callers can navigate to and update the actual source.
	 * <p>This method must be free of side-effects.
	 *
	 * @param element the PSI element under inspection.
	 * @return a resolved artifact reference, or
	 * {@link ArtifactReference#unresolved()}.
	 */
	public abstract ArtifactReference resolveArtifactReference(PsiElement element);

	/**
	 * Resolve available upgrades for a pre-resolved {@link ArtifactReference}.
	 * <p>The lookup returns no result if the reference is unresolved, if its
	 * declaration has no concrete version source/current version, or if the cache
	 * contains no releases for the artifact. Releases are read from
	 * {@link Cache#getReleases(ArtifactId, boolean)} with {@code ensureRecent}
	 * disabled; this method is intentionally cache-only and never schedules a
	 * refresh.
	 *
	 * @param cache the cache to read releases from.
	 * @param artifactReference the reference to evaluate.
	 * @return the available upgrades, or {@link AvailableUpgrades#none()}.
	 */
	protected AvailableUpgrades suggestUpgrades(Cache cache,
			ArtifactReference artifactReference) {

		if (!artifactReference.isResolved()) {
			return AvailableUpgrades.none();
		}

		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		if (!declaration.hasVersionSource() || !declaration.isVersionDefined()) {
			return AvailableUpgrades.none();
		}

		List<Release> options = cache.getReleases(declaration.getArtifactId(), false);
		if (options.isEmpty()) {
			return AvailableUpgrades.none();
		}

		return determineUpgrades(artifactReference, declaration.getVersion(), options);
	}

	/**
	 * Determine the preferred broad upgrade suggestion for the given version.
	 * <p>The method evaluates major, minor, and patch strategies independently and
	 * returns the broadest available tier, preferring major over minor over patch.
	 * Each strategy returns the first matching release from {@code options}, so
	 * callers should provide releases in the desired selection order.
	 *
	 * @param artifactReference the resolved artifact reference.
	 * @param current the current artifact version.
	 * @param options the candidate release options.
	 * @return the preferred upgrade suggestion, or
	 * {@link UpgradeSuggestion#none()}.
	 */
	public static UpgradeSuggestion determineUpgrade(ArtifactReference artifactReference, ArtifactVersion current,
			List<Release> options) {

		Release major = UpgradeStrategy.MAJOR.select(current, options);
		Release minor = UpgradeStrategy.MINOR.select(current, options);
		Release patch = UpgradeStrategy.PATCH.select(current, options);

		if (major == null && minor == null && patch == null) {
			return UpgradeSuggestion.none();
		}

		UpgradeStrategy strategy;
		Release bestOption;
		if (major != null) {
			strategy = UpgradeStrategy.MAJOR;
			bestOption = major;
		} else if (minor != null) {
			strategy = UpgradeStrategy.MINOR;
			bestOption = minor;
		} else {
			strategy = UpgradeStrategy.PATCH;
			bestOption = patch;
		}

		return UpgradeSuggestion.of(strategy, bestOption, artifactReference);
	}

	/**
	 * Determine all available upgrade suggestions for the given version.
	 * <p>The returned {@link AvailableUpgrades#getUpgrades() upgrade map} contains
	 * entries for every matched major, minor, and patch tier, in that order. The
	 * default suggestion exposed by
	 * {@link AvailableUpgrades#getUpgradeSuggestion()} is the most conservative
	 * available tier, preferring patch over minor over major when multiple tiers
	 * are present.
	 * <p>Use {@link #determineUpgrade(ArtifactReference, ArtifactVersion, List)}
	 * when a caller specifically needs the broadest single suggestion.
	 *
	 * @param artifactReference the resolved artifact reference.
	 * @param current the current artifact version.
	 * @param options the candidate release options.
	 * @return the available upgrade suggestions, or
	 * {@link AvailableUpgrades#none()}.
	 */
	public static AvailableUpgrades determineUpgrades(ArtifactReference artifactReference, ArtifactVersion current,
			List<Release> options) {

		Release major = UpgradeStrategy.MAJOR.select(current, options);
		Release minor = UpgradeStrategy.MINOR.select(current, options);
		Release patch = UpgradeStrategy.PATCH.select(current, options);

		if (major == null && minor == null && patch == null) {
			return AvailableUpgrades.none();
		}

		Map<UpgradeStrategy, UpgradeSuggestion> upgrades = new LinkedHashMap<>();
		UpgradeStrategy strategy = null;

		if (major != null) {
			strategy = UpgradeStrategy.MAJOR;
			upgrades.put(strategy, UpgradeSuggestion.of(strategy, major, artifactReference));
		}

		if (minor != null) {
			strategy = UpgradeStrategy.MINOR;
			upgrades.put(strategy, UpgradeSuggestion.of(strategy, minor, artifactReference));
		}

		if (patch != null) {
			strategy = UpgradeStrategy.PATCH;
			upgrades.put(strategy, UpgradeSuggestion.of(strategy, patch, artifactReference));
		}

		return AvailableUpgrades.of(artifactReference, upgrades.get(strategy),
				upgrades);
	}


}
