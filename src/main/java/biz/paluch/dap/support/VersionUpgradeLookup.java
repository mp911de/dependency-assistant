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

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.SequencedMap;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.VersionProperty;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Per-file deep module that turns a build-file element into upgrade
 * suggestions.
 *
 * <p>The module owns cached release lookup, current-version resolution, and
 * upgrade tiering. Build-tool variation is isolated behind a single
 * {@link ArtifactReferenceResolver} that the module holds and delegates
 * {@link #resolveArtifactReference(PsiElement)} to. The same concrete type
 * serves every build ecosystem.
 *
 * <p>Lookup methods are cache-only and never trigger remote repository access.
 *
 * @author Mark Paluch
 * @see ArtifactReferenceResolver
 * @see LookupContext
 * @see AvailableUpgrades
 */
public class VersionUpgradeLookup {

	private final LookupContext context;

	private final ArtifactReferenceResolver resolver;

	/**
	 * Create a {@code VersionUpgradeLookup} bound to the given context and
	 * resolver.
	 * @param context the shared per-file resolution environment; must not be
	 * {@literal null}.
	 * @param resolver the build-tool-specific reference resolver; must not be
	 * {@literal null}.
	 */
	public VersionUpgradeLookup(LookupContext context, ArtifactReferenceResolver resolver) {
		this.context = context;
		this.resolver = resolver;
	}

	/**
	 * Return the shared release cache used by this lookup.
	 */
	public Cache getCache() {
		return this.context.cache();
	}

	/**
	 * Resolve the given PSI element into artifact declaration metadata.
	 * @param element the PSI element under inspection.
	 * @return a resolved artifact reference, or
	 * {@link ArtifactReference#unresolved()}.
	 */
	public ArtifactReference resolveArtifactReference(PsiElement element) {
		return resolver.resolveArtifactReference(element);
	}

	/**
	 * Resolve available upgrades for the given PSI element.
	 * @param element the PSI element under inspection.
	 * @return the available upgrades, or {@link AvailableUpgrades#none()}.
	 */
	public AvailableUpgrades suggestUpgrades(PsiElement element) {
		ArtifactReference artifactReference = resolveArtifactReference(element);
		return suggestUpgrades(context.cache(), artifactReference);
	}

	/**
	 * Return the current version of the dependency with the given artifact
	 * reference.
	 * <p>The {@link ProjectState} version wins when present; on a project-state
	 * miss the reference's own declared version is returned, so versions resolved
	 * by the resolver are reported even before the dependency is scanned into
	 * project state.
	 * @param reference the artifact to locate.
	 * @return the current artifact version, or {@literal null} if the reference is
	 * unresolved or carries no defined version and project state has no entry.
	 */
	public @Nullable ArtifactVersion getCurrentVersion(ArtifactReference reference) {

		if (!reference.isResolved()) {
			return null;
		}

		ArtifactVersion stateVersion = context.findCurrentVersion(reference.getArtifactId());
		if (stateVersion != null) {
			return stateVersion;
		}

		ArtifactDeclaration declaration = reference.getDeclaration();
		return declaration.isVersionDefined() ? declaration.getVersion() : null;
	}


	public @Nullable VersionProperty findProperty(String property) {
		return context.findProperty(property);
	}

	/**
	 * Return the current version of the first artifact associated with the given
	 * property.
	 * @param property the property whose artifact association should be inspected.
	 * @return the current artifact version, or {@literal null} if the property has
	 * no artifact association or project state does not contain the dependency.
	 */
	public @Nullable ArtifactVersion getCurrentVersion(VersionProperty property) {

		if (property.artifacts().isEmpty()) {
			return null;
		}
		return context.findCurrentVersion(property.artifacts().getFirst().toArtifactId());
	}

	/**
	 * Resolve available upgrades for a pre-resolved {@link ArtifactReference}.
	 * <p>Resolution is intentionally cache-only and never schedules a refresh.
	 * @param cache the cache to read releases from.
	 * @param artifactReference the reference to evaluate.
	 * @return the available upgrades, or {@link AvailableUpgrades#none()}.
	 */
	private AvailableUpgrades suggestUpgrades(Cache cache, ArtifactReference artifactReference) {

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
	 * Determine all available upgrade suggestions for the given version.
	 * <p>The returned {@link AvailableUpgrades#getUpgrades() upgrade map} contains
	 * entries for every matched major, minor, patch, preview, and release tier, in
	 * that order.
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
		Release preview = current.isSnapshotVersion() || current.isPreview()
				? UpgradeStrategy.PREVIEW.select(current, options)
				: null;
		Release latestCandidate = UpgradeStrategy.LATEST.select(current, options);
		Release release = UpgradeStrategy.RELEASE.select(current, options);
		Release latest = latestCandidate != null && latestCandidate.isNewer(current) ? latestCandidate : null;
		List<UpgradeSuggestion> suggestions = new ArrayList<>();

		if (major != null) {
			suggestions.add(UpgradeSuggestion.of(UpgradeStrategy.MAJOR, major, artifactReference));
		}

		if (minor != null) {
			suggestions.add(UpgradeSuggestion.of(UpgradeStrategy.MINOR, minor, artifactReference));
		}

		if (patch != null) {
			suggestions.add(UpgradeSuggestion.of(UpgradeStrategy.PATCH, patch, artifactReference));
		}

		if (preview != null) {
			suggestions.add(UpgradeSuggestion.of(UpgradeStrategy.PREVIEW, preview, artifactReference));
		}

		if (release != null) {
			suggestions.add(UpgradeSuggestion.of(UpgradeStrategy.RELEASE, release, artifactReference));
		}

		if (suggestions.isEmpty()) {
			return AvailableUpgrades.none();
		}
		Collections.reverse(suggestions);
		SequencedMap<UpgradeStrategy, UpgradeSuggestion> upgrades = new LinkedHashMap<>();
		suggestions.forEach(s -> upgrades.put(s.getStrategy(), s));
		return AvailableUpgrades.of(artifactReference, suggestions.getFirst(), upgrades, latest);
	}

}
