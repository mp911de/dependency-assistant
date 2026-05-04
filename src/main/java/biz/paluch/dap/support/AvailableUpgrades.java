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

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.SequencedMap;

import biz.paluch.dap.artifact.Release;
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
	 * MINOR &rarr; PATCH &rarr; PREVIEW order; must not be {@literal null}.
	 * @param latest the latest non-preview release across the candidate options, or
	 * {@literal null} if no such release exists.
	 */
	public static AvailableUpgrades of(ArtifactReference artifactReference, UpgradeSuggestion bestOption,
			SequencedMap<UpgradeStrategy, UpgradeSuggestion> upgrades, @Nullable Release latest) {
		return new AvailableUpgrades(artifactReference, bestOption,
				Collections.unmodifiableSequencedMap(upgrades), latest);
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


	@Override
	public String toString() {
		if (isPresent()) {
			return "%s -> %s (%s)".formatted(artifactReference.getArtifactId(), bestOption.getRelease(),
					bestOption.getStrategy());
		}
		return "None";
	}

}
