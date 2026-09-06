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

import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import org.springframework.util.Assert;

/**
 * Strategy for locating successive matches in decoded property text.
 *
 * @author Mark Paluch
 * @see PropertyUtils#findTextRanges(com.intellij.lang.properties.psi.Property,
 * MatchFunction)
 */
public interface MatchFunction {

	/**
	 * Find the next match at or after {@code startIndex}.
	 * <p>Offsets refer to the decoded {@code text}. Callers resume at the previous
	 * match's end. Zero-length matches must not cause repeated calls to return the
	 * same match indefinitely.
	 * @return a result whose {@link MatchResult#hasMatch()} is {@literal true}, or
	 * {@link #noMatch()} when the search is complete.
	 */
	MatchResult find(String text, int startIndex);

	/**
	 * Expose a named group as the entire match, retaining its source offsets.
	 * <p>The group must have participated in the current match. Other groups are
	 * not exposed. Use the original matcher to access them.
	 * @throws IllegalStateException if the matcher has no successful match.
	 * @throws IllegalArgumentException if the pattern does not define the group.
	 */
	static MatchResult group(String group, Matcher matcher) {
		return new DefaultMatchResult(matcher.group(group), matcher.start(group), matcher.end(group));
	}

	/**
	 * Match literal occurrences of {@code str}, case-sensitively.
	 * @throws IllegalArgumentException if {@code str} is {@literal null}, empty, or
	 * blank.
	 */
	static MatchFunction indexOf(String str) {

		Assert.hasText(str, "Search string must not be empty");

		return (text, startIndex) -> {
			int index = text.indexOf(str, startIndex);
			if (index < 0) {
				return AbsentMatchResult.ABSENT;
			}
			return new DefaultMatchResult(str, index, index + str.length());
		};
	}

	/**
	 * Return the shared result whose {@link MatchResult#hasMatch()} is
	 * {@literal false}.
	 */
	static MatchResult noMatch() {
		return AbsentMatchResult.ABSENT;
	}

	/**
	 * Create a match for a known range in decoded text.
	 * @param text the matched text, not the full source.
	 * @param start the inclusive offset in the decoded source.
	 * @param end the exclusive offset in the decoded source.
	 */
	static MatchResult match(String text, int start, int end) {
		return new DefaultMatchResult(text, start, end);
	}


}
