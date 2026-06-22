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
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.UpgradeStrategy;
import org.jspecify.annotations.Nullable;

/**
 * Upgrade suggestions for a resolved artifact reference.
 */
public class AvailableUpgrades {

	private static final AvailableUpgrades NONE = new AvailableUpgrades(ArtifactReference.unresolved(),
			UpgradeSuggestion.none(), Collections.unmodifiableSequencedMap(new LinkedHashMap<>()), null);

	private final UpgradeSuggestion bestOption;

	private final ArtifactReference artifactReference;

	private final SequencedMap<UpgradeStrategy, UpgradeSuggestion> upgrades;

	private final @Nullable Release latest;

	private AvailableUpgrades(ArtifactReference artifactReference, UpgradeSuggestion bestOption,
			SequencedMap<UpgradeStrategy, UpgradeSuggestion> upgrades, @Nullable Release latest) {
		this.bestOption = bestOption;
		this.artifactReference = artifactReference;
		this.upgrades = upgrades;
		this.latest = latest;
	}

	/**
	 * Return an empty (absent) {@code QualifiedUpgradeSuggestion}.
	 */
	public static AvailableUpgrades none() {
		return NONE;
	}

	/**
	 * Create new {@code AvailableUpgrades} for the given {@link ArtifactReference}
	 * and {@link UpgradeSuggestion} and {@code upgrades}.
	 * @param artifactReference the resolved artifact reference; must not be
	 * {@literal null}.
	 * @param bestOption the preferred upgrade suggestion; must not be
	 * {@literal null}.
	 * @param upgrades the available tier-keyed upgrade suggestions in MAJOR &rarr;
	 * MINOR &rarr; PATCH &rarr; PREVIEW order.
	 * @param latest the latest non-preview release across the candidate options, or
	 * {@literal null} if no such release exists.
	 */
	public static AvailableUpgrades of(ArtifactReference artifactReference, UpgradeSuggestion bestOption,
			SequencedMap<UpgradeStrategy, UpgradeSuggestion> upgrades, @Nullable Release latest) {
		return new AvailableUpgrades(artifactReference, bestOption,
				Collections.unmodifiableSequencedMap(upgrades), latest);
	}

	/**
	 * Determine all available upgrade suggestions for the given version.
	 * <p>The returned {@link AvailableUpgrades#getUpgrades() upgrade map} contains
	 * entries for every matched major, minor, patch, preview, and release tier, in
	 * that order.
	 * @param artifactReference the resolved artifact reference.
	 * @param current the current artifact version.
	 * @param releases the candidate releases.
	 * @return the available upgrade suggestions, or
	 * {@link AvailableUpgrades#none()}.
	 */
	public static AvailableUpgrades determineUpgrades(ArtifactReference artifactReference, ArtifactVersion current,
			Releases releases) {

		Release major = UpgradeStrategy.MAJOR.select(current, releases);
		Release minor = UpgradeStrategy.MINOR.select(current, releases);
		Release patch = UpgradeStrategy.PATCH.select(current, releases);
		Release preview = current.isSnapshotVersion() || current.isPreview()
				? UpgradeStrategy.PREVIEW.select(current, releases)
				: null;
		Release latestCandidate = UpgradeStrategy.LATEST.select(current, releases);
		Release release = UpgradeStrategy.RELEASE.select(current, releases);
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
			return none();
		}
		Collections.reverse(suggestions);
		SequencedMap<UpgradeStrategy, UpgradeSuggestion> upgrades = new LinkedHashMap<>();
		suggestions.forEach(s -> upgrades.put(s.getStrategy(), s));
		return of(artifactReference, suggestions.getFirst(), upgrades, latest);
	}

	/**
	 * Return whether a suggestion is present.
	 */
	public boolean isPresent() {
		return artifactReference.isResolved();
	}

	/**
	 * Return the {@link ArtifactDeclaration} for this suggestion.
	 */
	public ArtifactDeclaration getArtifactDeclaration() {
		return artifactReference.getDeclaration();
	}

	/**
	 * Return the preferred upgrade suggestion for this artifact, or
	 * {@link UpgradeSuggestion#none()} if no upgrade is available.
	 */
	public UpgradeSuggestion getUpgradeSuggestion() {
		return bestOption;
	}

	/**
	 * Return the available upgrade suggestions in MAJOR &rarr; MINOR &rarr; PATCH
	 * &rarr; PREVIEW order. The returned map is unmodifiable.
	 */
	public SequencedMap<UpgradeStrategy, UpgradeSuggestion> getUpgrades() {
		return upgrades;
	}

	/**
	 * Return the latest non-preview release across the candidate options, or
	 * {@literal null} if no such release exists or no release is newer than the
	 * current version.
	 */
	public @Nullable Release getLatest() {
		return latest;
	}

	public AvailableUpgrades filterSuggestions(Predicate<UpgradeStrategy> strategyFilter) {

		if (!isPresent()) {
			return this;
		}

		SequencedMap<UpgradeStrategy, UpgradeSuggestion> upgrades = new LinkedHashMap<>();

		this.upgrades.forEach((strategy, suggestion) -> {
			if (strategyFilter.test(strategy)) {
				upgrades.put(strategy, suggestion);
			}
		});

		UpgradeSuggestion bestOption = strategyFilter.test(this.bestOption.getStrategy()) ? this.bestOption
				: UpgradeSuggestion.none();

		if (!bestOption.isPresent() && !upgrades.isEmpty()) {
			bestOption = upgrades.values().iterator().next();
		}

		if (upgrades.isEmpty()) {
			return NONE;
		}
		return new AvailableUpgrades(artifactReference, bestOption, upgrades, latest);
	}

	@Override
	public String toString() {
		if (isPresent()) {
			return "%s -> %s (%s)".formatted(artifactReference.getArtifactId(), bestOption.getRelease(),
					bestOption.getStrategy());
		}
		return "None";
	}

}
