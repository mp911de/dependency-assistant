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

import java.util.Map;

import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;

public class AvailableUpgrades {

	private final static AvailableUpgrades NONE = new AvailableUpgrades(ArtifactReference.unresolved(),
			UpgradeSuggestion.none(),
			Map.of());

	private final UpgradeSuggestion bestOption;

	private final ArtifactReference artifactReference;

	private final Map<UpgradeStrategy, UpgradeSuggestion> upgrades;

	private AvailableUpgrades(ArtifactReference artifactReference, UpgradeSuggestion bestOption,
			Map<UpgradeStrategy, UpgradeSuggestion> upgrades) {
		this.bestOption = bestOption;
		this.artifactReference = artifactReference;
		this.upgrades = upgrades;
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
	 */
	public static AvailableUpgrades of(ArtifactReference artifactReference, UpgradeSuggestion bestOption,
			Map<UpgradeStrategy, UpgradeSuggestion> upgrades) {
		return new AvailableUpgrades(artifactReference, bestOption, upgrades);
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
	 * Return the {@link Release} for this suggestion or throw
	 * {@link IllegalStateException} if no suggestion is present.
	 */
	public UpgradeSuggestion getUpgradeSuggestion() {
		return bestOption;
	}

	/**
	 * Return the available upgrade suggestions.
	 */
	public Map<UpgradeStrategy, UpgradeSuggestion> getUpgrades() {
		return upgrades;
	}

}
