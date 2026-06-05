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

package biz.paluch.dap.assistant;

import org.jspecify.annotations.Nullable;

/**
 * A change to the {@link DependencyUpgradeReview} that the table must reflect.
 *
 * <p>Three shapes, distinguished so the table model can pick the cheapest
 * reaction: reload the visible row set, refresh a single row, or refresh every
 * row in place.
 *
 * @param candidate the single row to refresh, or {@literal null} when the
 * change is a reload or spans all rows.
 * @param reload whether the visible candidate set must be reloaded.
 * @author Mark Paluch
 */
record ReviewChange(@Nullable UpdateCandidate candidate, boolean reload) {

	/**
	 * The visible candidate set changed and must be reloaded.
	 */
	static ReviewChange reloadVisible() {
		return new ReviewChange(null, true);
	}

	/**
	 * A single candidate's selection changed; refresh its row.
	 */
	static ReviewChange row(UpdateCandidate candidate) {
		return new ReviewChange(candidate, false);
	}

	/**
	 * Selections changed across many rows; refresh all rows without reloading.
	 */
	static ReviewChange allRows() {
		return new ReviewChange(null, false);
	}

}
