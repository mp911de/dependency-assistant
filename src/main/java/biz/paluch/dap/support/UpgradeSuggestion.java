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

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Upgrade suggestion for an identified {@link ArtifactReference}.
 *
 * @author Mark Paluch
 */
public class UpgradeSuggestion {

	private final static UpgradeSuggestion NONE = new UpgradeSuggestion(null, null,
			ArtifactReference.unresolved());

	private final @Nullable UpgradeStrategy strategy;
	private final @Nullable Release bestOption;
	private final ArtifactReference artifactReference;

	private UpgradeSuggestion(@Nullable UpgradeStrategy strategy, @Nullable Release bestOption,
			ArtifactReference artifactReference) {
		this.strategy = strategy;
		this.bestOption = bestOption;
		this.artifactReference = artifactReference;
	}

	/**
	 * Return an empty (absent) {@code QualifiedUpgradeSuggestion}.
	 */
	public static UpgradeSuggestion none() {
		return NONE;
	}

	/**
	 * Create a new {@code QualifiedUpgradeSuggestion} for the given {@link UpgradeStrategy} and {@link Release}.
	 */
	public static UpgradeSuggestion of(UpgradeStrategy strategy, Release bestOption,
			ArtifactReference artifactReference) {
		return new UpgradeSuggestion(strategy, bestOption, artifactReference);
	}

	public String getMessage() {
		String upgradeTarget = MessageBundle.message("dialog.upgradeTarget." + strategy);
		return MessageBundle.message("gutter.newer.tooltip", upgradeTarget, getRelease().version().toString());
	}

	/**
	 * Return whether a suggestion is present.
	 */
	public boolean isPresent() {
		return artifactReference.isResolved();
	}

	/**
	 * Return the {@link UpgradeStrategy} for this suggestion or throw {@link IllegalStateException} if no suggestion is
	 * present.
	 */
	public UpgradeStrategy getStrategy() {
		Assert.state(isPresent() && strategy != null, "No upgrade option available");
		return strategy;
	}

	/**
	 * Return the {@link Release} for this suggestion or throw {@link IllegalStateException} if no suggestion is present.
	 */
	public Release getRelease() {
		Assert.state(isPresent() && bestOption != null, "No upgrade option available");
		return bestOption;
	}

	/**
	 * Return the {@link ArtifactDeclaration} for this suggestion.
	 */
	public ArtifactDeclaration getArtifactDeclaration() {
		return artifactReference.getDeclaration();
	}

}
