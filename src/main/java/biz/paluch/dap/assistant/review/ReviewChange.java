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

package biz.paluch.dap.assistant.review;

import org.jspecify.annotations.Nullable;

/**
 * Required table refresh after a review change.
 *
 * @author Mark Paluch
 * @param candidate the row to refresh, or {@literal null} for all rows.
 * @param reload whether the visible row set must be reloaded.
 */
record ReviewChange(@Nullable TableRow candidate, boolean reload) {

	private static final ReviewChange RELOAD_VISIBLE = new ReviewChange(null, true);

	private static final ReviewChange ALL_ROWS = new ReviewChange(null, false);

	static ReviewChange reloadVisible() {
		return RELOAD_VISIBLE;
	}

	static ReviewChange row(TableRow candidate) {
		return new ReviewChange(candidate, false);
	}

	static ReviewChange allRows() {
		return ALL_ROWS;
	}

}
