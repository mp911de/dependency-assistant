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

import java.util.List;

/**
 * Policy forming {@link GroupRow} groups from dependency-check candidates.
 *
 * <p>Each policy selects the candidates it applies to and returns the groups it
 * forms; candidates it does not claim remain untouched. Implementations must
 * keep each group's members in candidate order so update fan-out remains
 * deterministic.
 *
 * @author Mark Paluch
 * @see GroupByRule
 * @see InferredGrouping
 */
interface GroupingPolicy<T, G> {

	/**
	 * Form groups from the given candidates.
	 *
	 * @param candidates all candidates in display order.
	 * @return the formed groups. Each group's members appear in candidate order.
	 */
	List<G> group(List<T> candidates);

}
