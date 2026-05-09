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
import java.util.SequencedMap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.state.VersionProperty;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Base class for build-tool specific version-upgrade lookups.
 *
 * <p>Subclasses translate build-file PSI into an {@link ArtifactReference}.
 * This base class handles project-state access, cached release lookup, and
 * conversion of releases into upgrade suggestions.
 *
 * <p>Lookup methods are cache-only and should not trigger remote repository
 * access. Unsupported or incomplete PSI should resolve to
 * {@link ArtifactReference#unresolved()} or {@link AvailableUpgrades#none()}.
 *
 * @author Mark Paluch
 * @see ArtifactReference
 * @see AvailableUpgrades
 * @see UpgradeSuggestion
 */
public abstract class VersionUpgradeLookupSupport {

	private final Project project;

	private final @Nullable ProjectState projectState;

	private final ProjectBuildContext buildContext;

	private final Cache cache;

	/**
	 * Create a new {@code VersionUpgradeLookupSupport} instance.
	 * @param project the IntelliJ project that owns the lookup.
	 * @param buildContext the build context.
	 */
	public VersionUpgradeLookupSupport(Project project, ProjectBuildContext buildContext) {

		this.project = project;
		StateService service = StateService.getInstance(project);
		this.cache = service.getCache();
		this.projectState = buildContext.isAvailable() ? service.getProjectState(buildContext.getProjectId()) : null;
		this.buildContext = buildContext;
	}

	public Project getProject() {
		return project;
	}

	/**
	 * Return the shared release cache used by this lookup.
	 */
	public Cache getCache() {
		return this.cache;
	}

	/**
	 * Return the cached project state associated with this lookup.
	 * <p>Call {@link #hasCachedState()} first when the lookup may have been created
	 * for a file outside a supported build context.
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
	 */
	public boolean hasCachedState() {
		return projectState != null;
	}

	/**
	 * Return the current version of the first artifact associated with the given
	 * property.
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
	 * Return the current version of the dependency with the given artifact
	 * reference.
	 * @param reference the artifact to locate.
	 * @return the current artifact version, or {@code null} if project state is
	 * unavailable or the dependency is not known.
	 */
	public @Nullable ArtifactVersion getCurrentVersion(ArtifactReference reference) {

		if (projectState == null || !reference.isResolved()) {
			return null;
		}

		return getCurrentVersion(reference.getArtifactId());
	}

	/**
	 * Return the current version of the dependency with the given artifact id.
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
	 * against the shared cache.
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
	 * @param element the PSI element under inspection.
	 * @return a resolved artifact reference, or
	 * {@link ArtifactReference#unresolved()}.
	 */
	public abstract ArtifactReference resolveArtifactReference(PsiElement element);

	/**
	 * Resolve available upgrades for a pre-resolved {@link ArtifactReference}.
	 * <p>This method is intentionally cache-only and never schedules a refresh.
	 * @param cache the cache to read releases from.
	 * @param artifactReference the reference to evaluate.
	 * @return the available upgrades, or {@link AvailableUpgrades#none()}.
	 */
	protected AvailableUpgrades suggestUpgrades(Cache cache, ArtifactReference artifactReference) {

		if (!artifactReference.isResolved()) {
			return AvailableUpgrades.none();
		}

		ArtifactDeclaration declaration = artifactReference.getDeclaration();
		if (!declaration.hasVersionSource() || !declaration.isVersionDefined()) {
			return AvailableUpgrades.none();
		}

		List<Release> options = cache.getReleases(declaration.getArtifactId());
		if (options.isEmpty()) {
			return AvailableUpgrades.none();
		}

		return determineUpgrades(artifactReference, declaration.getVersion(), options);
	}

	/**
	 * Determine the preferred broad upgrade suggestion for the given version.
	 * <p>Prefers major over minor over patch when multiple tiers are available.
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
		Release preview = current.isPreview() ? UpgradeStrategy.PREVIEW.select(current, options) : null;

		if (major == null && minor == null && patch == null && preview == null) {
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
		} else if (patch != null) {
			strategy = UpgradeStrategy.PATCH;
			bestOption = patch;
		} else {
			strategy = UpgradeStrategy.PREVIEW;
			bestOption = preview;
		}

		return UpgradeSuggestion.of(strategy, bestOption, artifactReference);
	}

	/**
	 * Determine all available upgrade suggestions for the given version.
	 * <p>The returned {@link AvailableUpgrades#getUpgrades() upgrade map} contains
	 * entries for every matched major, minor, and patch tier, in that order.
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
		Release preview = current.isPreview() ? UpgradeStrategy.PREVIEW.select(current, options) : null;
		Release latestCandidate = UpgradeStrategy.LATEST.select(current, options);
		Release latest = latestCandidate != null && latestCandidate.isNewer(current) ? latestCandidate : null;

		if (major == null && minor == null && patch == null && preview == null) {
			return AvailableUpgrades.none();
		}

		SequencedMap<UpgradeStrategy, UpgradeSuggestion> upgrades = new LinkedHashMap<>();
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

		if (preview != null) {
			strategy = UpgradeStrategy.PREVIEW;
			upgrades.put(strategy, UpgradeSuggestion.of(strategy, preview, artifactReference));
		}

		return AvailableUpgrades.of(artifactReference, upgrades.get(strategy), upgrades, latest);
	}


}
