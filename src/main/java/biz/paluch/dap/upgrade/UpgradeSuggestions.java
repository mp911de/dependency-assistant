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
import java.util.stream.Stream;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.Sequence;

/**
 * Per-strategy targets in captured map order.
 * <p>Iteration and first-suggestion lookup use that order.
 * {@link #getSuggestions()} provides a separate remediation-first display
 * order.
 *
 * @author Mark Paluch
 */
public class UpgradeSuggestions implements Sequence<UpgradeSuggestion> {

	private static final UpgradeSuggestions EMPTY = new UpgradeSuggestions(new LinkedHashMap<>());

	private final Map<UpgradeStrategy, UpgradeSuggestion> suggestions;

	UpgradeSuggestions(Map<UpgradeStrategy, UpgradeSuggestion> suggestions) {
		this.suggestions = new LinkedHashMap<>(suggestions);
	}

	public static UpgradeSuggestions empty() {
		return EMPTY;
	}

	/**
	 * Select non-remediation targets within the current versioning scheme. Equal or
	 * older targets are omitted. Use {@link UpgradeSuggestionsFactory} for security
	 * and rule remediation.
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
	 * Copy the strategy map, preserving its iteration order.
	 */
	public static UpgradeSuggestions of(Map<UpgradeStrategy, UpgradeSuggestion> suggestions) {
		return new UpgradeSuggestions(suggestions);
	}

	/**
	 * Return a detached list with remediation targets first. Relative order within
	 * each group is preserved.
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

	@Override
	public Stream<UpgradeSuggestion> stream() {
		return suggestions.values().stream();
	}

	@Override
	public boolean isEmpty() {
		return suggestions.isEmpty();
	}

	public int size() {
		return suggestions.size();
	}

	public boolean contains(UpgradeStrategy strategy) {
		return suggestions.containsKey(strategy);
	}

	/**
	 * Return the strategy target, or {@link UpgradeSuggestion#none()} if absent.
	 */
	public UpgradeSuggestion get(UpgradeStrategy strategy) {
		return suggestions.getOrDefault(strategy, UpgradeSuggestion.none());
	}

	/**
	 * Return the first target in map order, or {@link UpgradeSuggestion#none()} if
	 * empty.
	 */
	public UpgradeSuggestion getSuggestion() {
		if (suggestions.isEmpty()) {
			return UpgradeSuggestion.none();
		}
		return suggestions.values().iterator().next();
	}

	/**
	 * Return a copy retaining accepted strategies in their existing order.
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
	 * Add or replace a strategy target in a copy. Order Safe and Rule targets
	 * first, then ordinary strategies in priority order.
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

	/**
	 * Return the live strategy map. Mutations affect equality, hash code and
	 * iteration order.
	 */
	public Map<UpgradeStrategy, UpgradeSuggestion> toMap() {
		return this.suggestions;
	}

	static class SuggestionBuilder {

		private final Map<UpgradeStrategy, UpgradeSuggestion> upgrades = new LinkedHashMap<>();

		private final ArtifactVersion current;

		private final Releases releases;

		public SuggestionBuilder(ArtifactVersion artifactVersion, Releases releases) {
			this.current = artifactVersion;
			this.releases = releases;
		}

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

		public UpgradeSuggestions build() {
			return new UpgradeSuggestions(upgrades);
		}

	}

}
