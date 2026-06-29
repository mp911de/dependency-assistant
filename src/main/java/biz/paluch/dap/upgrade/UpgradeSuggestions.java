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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.support.UpgradeStrategy;

/**
 * The per-strategy upgrade targets for one dependency: a priority-ordered map
 * of {@link UpgradeStrategy} to the target {@link Release} it selected.
 *
 * @author Mark Paluch
 * @see UpgradeStrategy
 */
public class UpgradeSuggestions implements Iterable<UpgradeSuggestion> {

	private static final UpgradeSuggestions EMPTY = new UpgradeSuggestions(new LinkedHashMap<>());

	private final Map<UpgradeStrategy, UpgradeSuggestion> suggestions;

	/**
	 * Create suggestions over the given per-strategy targets, copied defensively
	 * into a {@link LinkedHashMap} that preserves the iteration order of the given
	 * map.
	 *
	 * @param suggestions the target release per strategy, in the caller's iteration
	 * order.
	 */
	UpgradeSuggestions(Map<UpgradeStrategy, UpgradeSuggestion> suggestions) {
		this.suggestions = new LinkedHashMap<>(suggestions);
	}

	/**
	 * Return the empty set, carrying no upgrade for any strategy.
	 *
	 * @return the empty set; never {@literal null}.
	 */
	public static UpgradeSuggestions empty() {
		return EMPTY;
	}

	/**
	 * Determine the target for every selectable strategy for {@code current}, the
	 * set the upgrade dialog offers.
	 *
	 * @param current the version currently in use.
	 * @param releases the analyzed release history.
	 * @return the per-strategy targets, {@link #empty()} when none apply.
	 */
	public static UpgradeSuggestions from(ArtifactVersion current, Releases releases) {

		SuggestionBuilder builder = new SuggestionBuilder(current, releases);
		for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
			if (!strategy.isRemediation()) {
				builder.select(strategy);
			}
		}
		return builder.build();
	}

	/**
	 * Create suggestions over the given per-strategy targets, copying the map
	 * defensively and preserving its iteration order.
	 *
	 * @param suggestions the target suggestion per strategy, in the caller's
	 * iteration order.
	 * @return the suggestions backed by a defensive copy; never {@literal null}.
	 */
	public static UpgradeSuggestions of(Map<UpgradeStrategy, UpgradeSuggestion> suggestions) {
		return new UpgradeSuggestions(suggestions);
	}

	/**
	 * Return the upgrade suggestions in priority order.
	 */
	public List<UpgradeSuggestion> getSuggestions() {

		List<UpgradeSuggestion> suggestions = new ArrayList<>(this.suggestions.size());
		for (UpgradeSuggestion value : this.suggestions.values()) {
			if (value.getStrategy().isRemediation()) {
				suggestions.add(value);
			}
		}
		for (UpgradeSuggestion value : this.suggestions.values()) {
			if (!value.getStrategy().isRemediation()) {
				suggestions.add(value);
			}
		}

		return suggestions;
	}

	@Override
	public Iterator<UpgradeSuggestion> iterator() {
		return suggestions.values().iterator();
	}

	/**
	 * Return whether no strategy carries an upgrade target.
	 *
	 * @return {@literal true} if no upgrade is offered; {@literal false} otherwise.
	 */
	public boolean isEmpty() {
		return suggestions.isEmpty();
	}

	/**
	 * Return the number of strategies carrying an upgrade target.
	 *
	 * @return the target count.
	 */
	public int size() {
		return suggestions.size();
	}

	/**
	 * Return whether the given strategy carries an upgrade target.
	 *
	 * @param strategy the strategy to test.
	 * @return {@literal true} if a target exists for the strategy; {@literal false}
	 * otherwise.
	 */
	public boolean contains(UpgradeStrategy strategy) {
		return suggestions.containsKey(strategy);
	}

	/**
	 * Return the upgrade suggestion for the given strategy.
	 *
	 * @param strategy the strategy to look up.
	 * @return the suggestion for the strategy, or {@link UpgradeSuggestion#none()}
	 * when the strategy carries no target.
	 */
	public UpgradeSuggestion get(UpgradeStrategy strategy) {
		return suggestions.getOrDefault(strategy, UpgradeSuggestion.none());
	}

	/**
	 * Return the suggestion of the highest-priority strategy carrying a target.
	 *
	 * @return the first suggestion in priority order, or
	 * {@link UpgradeSuggestion#none()} when no strategy carries a target.
	 */
	public UpgradeSuggestion getSuggestion() {
		if (suggestions.isEmpty()) {
			return UpgradeSuggestion.none();
		}
		return suggestions.values().iterator().next();
	}

	/**
	 * Return a copy retaining only the strategies accepted by the predicate.
	 *
	 * @param predicate tested against each strategy carrying a target.
	 * @return the retained suggestions, or {@link #empty()} when none match.
	 */
	public UpgradeSuggestions filter(Predicate<UpgradeStrategy> predicate) {

		Map<UpgradeStrategy, UpgradeSuggestion> filtered = new LinkedHashMap<>();
		this.suggestions.forEach((strategy, suggestion) -> {
			if (predicate.test(suggestion.getStrategy())) {
				filtered.put(strategy, suggestion);
			}
		});

		if (filtered.isEmpty()) {
			return empty();
		}
		return new UpgradeSuggestions(filtered);
	}

	/**
	 * Return a copy with the given suggestion added or replaced, re-ordered so the
	 * remediation strategies ({@link UpgradeStrategy#SAFE},
	 * {@link UpgradeStrategy#RULE}) lead, followed by the remaining strategies in
	 * priority order.
	 *
	 * @param suggestion the suggestion to add; replaces any existing target for the
	 * same strategy.
	 * @return the combined suggestions.
	 */
	public UpgradeSuggestions with(UpgradeSuggestion suggestion) {

		Map<UpgradeStrategy, UpgradeSuggestion> suggestions = new LinkedHashMap<>();
		Map<UpgradeStrategy, UpgradeSuggestion> source = new LinkedHashMap<>(this.suggestions);
		source.put(suggestion.getStrategy(), suggestion);

		putIfPresent(source, suggestions, UpgradeStrategy.SAFE);
		putIfPresent(source, suggestions, UpgradeStrategy.RULE);
		for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
			if (!strategy.isRemediation()) {
				putIfPresent(source, suggestions, strategy);
			}
		}

		return new UpgradeSuggestions(suggestions);
	}

	private static void putIfPresent(Map<UpgradeStrategy, UpgradeSuggestion> source,
			Map<UpgradeStrategy, UpgradeSuggestion> target, UpgradeStrategy strategy) {

		UpgradeSuggestion suggestion = source.get(strategy);
		if (suggestion != null) {
			target.put(strategy, suggestion);
		}
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof UpgradeSuggestions that)) {
			return false;
		}
		return suggestions.equals(that.suggestions);
	}

	@Override
	public int hashCode() {
		return suggestions.hashCode();
	}

	@Override
	public String toString() {
		return suggestions.toString();
	}

	public Map<UpgradeStrategy, UpgradeSuggestion> toMap() {
		return this.suggestions;
	}

	/**
	 * Accumulates the selected target per {@link UpgradeStrategy} for one current
	 * version and release history, retaining only strategies whose target is newer
	 * than the current version.
	 */
	static class SuggestionBuilder {

		private final Map<UpgradeStrategy, UpgradeSuggestion> upgrades = new LinkedHashMap<>();

		private final ArtifactVersion current;

		private final Releases releases;

		public SuggestionBuilder(ArtifactVersion artifactVersion, Releases releases) {
			this.current = artifactVersion;
			this.releases = releases;
		}

		/**
		 * Select and record the upgrade target for the given strategy, skipping it when
		 * the target is the current version or older.
		 *
		 * @param upgradeStrategy the strategy whose target should be selected.
		 */
		public void select(UpgradeStrategy upgradeStrategy) {

			Release release = upgradeStrategy.select(current, releases);
			if (release == null) {
				return;
			}

			ArtifactVersion candidate = release.getVersion();
			if (candidate.canCompare(current) && candidate.compareTo(current) <= 0) {
				return;
			}

			if (!candidate.equals(current)) {
				upgrades.put(upgradeStrategy, UpgradeSuggestion.of(upgradeStrategy, release));
			}
		}

		/**
		 * Return the accumulated suggestions in selection order.
		 *
		 * @return the suggestions for the strategies that selected a newer target;
		 * never {@literal null}.
		 */
		public UpgradeSuggestions build() {
			return new UpgradeSuggestions(upgrades);
		}

	}

}
