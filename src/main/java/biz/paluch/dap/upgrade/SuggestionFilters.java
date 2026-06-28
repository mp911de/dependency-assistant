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

import java.util.List;

/**
 * Composite {@link UpgradeSuggestionsFilter} that applies its delegates in
 * declaration order, threading the result of each into the next.
 *
 * @author Mark Paluch
 */
class SuggestionFilters implements UpgradeSuggestionsFilter {

	private final List<UpgradeSuggestionsFilter> filters;

	private SuggestionFilters(List<UpgradeSuggestionsFilter> filters) {
		this.filters = filters;
	}

	static SuggestionFilters of(UpgradeSuggestionsFilter... filters) {
		return new SuggestionFilters(List.of(filters));
	}

	@Override
	public UpgradeSuggestions filter(DependencyUpgradeSubject subject, UpgradeSuggestions suggestions) {

		UpgradeSuggestions filtered = suggestions;
		for (UpgradeSuggestionsFilter filter : filters) {
			filtered = filter.filter(subject, filtered);
		}
		return filtered;
	}

}
