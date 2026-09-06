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

package biz.paluch.dap.util;

import java.util.function.Predicate;

import com.intellij.patterns.PatternCondition;
import com.intellij.util.ProcessingContext;

/**
 * Adapt Java predicates to IntelliJ {@link PatternCondition PatternConditions}.
 * <p>Implement {@code PatternCondition} directly when matching needs to read or
 * update the {@link ProcessingContext}.
 *
 * @author Mark Paluch
 */
public class PatternConditions {

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
