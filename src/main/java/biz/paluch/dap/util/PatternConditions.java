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
package biz.paluch.dap.util;

import java.util.function.Predicate;

import com.intellij.patterns.PatternCondition;
import com.intellij.util.ProcessingContext;

/**
 * Factory methods for named IntelliJ {@link PatternCondition PatternConditions}
 * backed by ordinary Java predicates.
 *
 * <p>
 * Completion contributors in this project often need to combine IntelliJ's
 * declarative PSI patterns with existing parser predicates such as
 * {@code isVersionLiteral(...)} or {@code isVersionCatalog(...)}. This utility
 * provides the narrow adapter layer for those cases: the pattern keeps a stable
 * debug name for IntelliJ pattern diagnostics, while the semantic test remains
 * in the parser or PSI utility that owns the rule.
 *
 * <p>
 * Stateless conditions that only depend on the element being matched can use
 * this class. Implement {@link PatternCondition} directly when a condition
 * needs access to {@link ProcessingContext}, needs to contribute values to the
 * pattern context, or has lifecycle/state requirements beyond a simple
 * predicate.
 *
 * @author Mark Paluch
 */
public class PatternConditions {

	/**
	 * Create a named pattern condition from a stateless predicate.
	 * <p>
	 * The {@code debugName} should describe the semantic role of the condition
	 * in the surrounding pattern, for example {@code "versionNamedArgumentLiteral"}
	 * rather than the mechanics of the PSI traversal. Keeping these names stable
	 * makes completion patterns easier to inspect and debug.
	 * @param debugName the name exposed by the IntelliJ pattern infrastructure;
	 * must not be {@literal null}.
	 * @param predicate the element predicate that owns the matching rule; must
	 * not be {@literal null}.
	 * @return a {@link PatternCondition} suitable for use with
	 * {@code ElementPattern.with(...)}.
	 */
	public static <T> PatternCondition<T> conditional(
			String debugName,
			Predicate<? super T> predicate) {
		return new PatternCondition<>(debugName) {

			@Override
			public boolean accepts(T t, ProcessingContext context) {
				return predicate.test(t);
			}

		};
	}

}
