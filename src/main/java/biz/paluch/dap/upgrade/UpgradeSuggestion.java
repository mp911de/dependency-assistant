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
 * Optional target selected by an {@link UpgradeStrategy}.
 * <p>Check {@link #isPresent()} before accessing target facts or messages.
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

	public static UpgradeSuggestion none() {
		return NONE;
	}

	public static UpgradeSuggestion of(UpgradeStrategy strategy, Release release) {
		return new UpgradeSuggestion(strategy, release);
	}

	/**
	 * Return the localized upgrade-available tooltip.
	 */
	public String getMessage() {
		return MessageBundle.message("gutter.newer.tooltip", strategy.getDisplayName(),
				getRelease().version().toString());
	}

	/**
	 * Return the localized rule-suggestion tooltip.
	 */
	public String getSuggestionMessage() {
		return MessageBundle.message("gutter.suggestion.tooltip", strategy.getDisplayName(),
				getRelease().version().toString());
	}

	public boolean isPresent() {
		return strategy != null;
	}

	/**
	 * Return the selecting strategy.
	 * @throws IllegalStateException if this suggestion is absent.
	 */
	public UpgradeStrategy getStrategy() {
		Assert.state(isPresent() && strategy != null, "No upgrade option available");
		return strategy;
	}

	/**
	 * Return the target release.
	 * @throws IllegalStateException if this suggestion is absent.
	 */
	public Release getRelease() {
		Assert.state(isPresent() && release != null, "No upgrade option available");
		return release;
	}

	/**
	 * Return the target version.
	 * @throws IllegalStateException if this suggestion is absent.
	 */
	@Override
	public ArtifactVersion getVersion() {
		return getRelease().getVersion();
	}

	/**
	 * Invoke the consumer with the target release if present.
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
