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

package biz.paluch.dap.upgrade;

import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAware;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.MessageBundle;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Upgrade target selected by an {@link UpgradeStrategy}.
 *
 * @author Mark Paluch
 */
public class UpgradeSuggestion implements VersionAware {

	private static final UpgradeSuggestion NONE = new UpgradeSuggestion(null, null);

	private final @Nullable UpgradeStrategy strategy;

	private final @Nullable Release release;

	private UpgradeSuggestion(@Nullable UpgradeStrategy strategy, @Nullable Release release) {
		this.strategy = strategy;
		this.release = release;
	}

	/**
	 * Return an empty (absent) {@code UpgradeSuggestion}.
	 */
	public static UpgradeSuggestion none() {
		return NONE;
	}

	/**
	 * Create a new {@code UpgradeSuggestion} for the given {@link UpgradeStrategy}
	 * and target {@link Release}.
	 */
	public static UpgradeSuggestion of(UpgradeStrategy strategy, Release release) {
		return new UpgradeSuggestion(strategy, release);
	}

	/**
	 * Return the localized newer-version gutter tooltip for this suggestion.
	 */
	public String getMessage() {
		return MessageBundle.message("gutter.newer.tooltip", strategy.getDisplayName(),
				getRelease().version().toString());
	}

	/**
	 * Return the localized suggestion gutter tooltip for this suggestion.
	 */
	public String getSuggestionMessage() {
		return MessageBundle.message("gutter.suggestion.tooltip", strategy.getDisplayName(),
				getRelease().version().toString());
	}

	/**
	 * Return whether a suggestion is present.
	 */
	public boolean isPresent() {
		return strategy != null;
	}

	/**
	 * Return the {@link UpgradeStrategy} for this suggestion or throw
	 * {@link IllegalStateException} if no suggestion is present.
	 */
	public UpgradeStrategy getStrategy() {
		Assert.state(isPresent() && strategy != null, "No upgrade option available");
		return strategy;
	}

	/**
	 * Return the {@link Release} for this suggestion or throw
	 * {@link IllegalStateException} if no suggestion is present.
	 */
	public Release getRelease() {
		Assert.state(isPresent() && release != null, "No upgrade option available");
		return release;
	}

	/**
	 * Return the {@link ArtifactVersion} for this suggestion or throw
	 * {@link IllegalStateException} if no suggestion is present.
	 */
	@Override
	public ArtifactVersion getVersion() {
		return getRelease().getVersion();
	}

	/**
	 * If a suggestion is present, invoke the given {@link Consumer} with the
	 * release.
	 */
	public void ifPresent(Consumer<Release> releaseConsumer) {
		if (isPresent()) {
			releaseConsumer.accept(getRelease());
		}
	}

	@Override
	public String toString() {
		return "%s -> %s".formatted(strategy, release);
	}

}
