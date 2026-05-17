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

import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import org.springframework.util.Assert;

/**
 * Callback strategy interface for locating successive ranges in decoded
 * property text.
 *
 * <p>
 * A {@code MatchFunction} is called repeatedly with the same decoded text
 * and the index at which the next search should begin. Implementations return
 * {@link #noMatch()} to signal completion. Match coordinates are offsets in the
 * decoded text so callers can map the result back to the original host text.
 *
 * @author Mark Paluch
 * @see MatchResult
 */
public interface MatchFunction {

	/**
	 * Find the next match at or after the supplied decoded-text index.
	 *
	 * <p>
	 * The returned {@link MatchResult#start()} and {@link MatchResult#end()}
	 * values must be offsets into {@code text}. A caller advances subsequent
	 * searches to {@code end()}, so implementations that expose zero-length matches
	 * must ensure repeated invocations do not return the same match indefinitely.
	 *
	 * @param text the decoded text to search; must not be {@literal null}.
	 * @param startIndex the decoded-text index at which scanning should resume.
	 * @return a matching result whose {@link MatchResult#hasMatch()} returns
	 * {@literal true}, or {@link #noMatch()} if no further match is available.
	 */
	MatchResult find(String text, int startIndex);

	/**
	 * Return a match result covering a named group from a matcher that already
	 * matched.
	 *
	 * <p>
	 * The named group must have participated in the current match. Use the
	 * original matcher when access to additional groups is needed.
	 *
	 * @param group the name of the capturing group to expose; must not be
	 * {@literal null}.
	 * @param matcher the matcher positioned on a successful match; must not be
	 * {@literal null}.
	 * @return a match result whose only exposed group is the named group.
	 * @throws IllegalStateException if no successful match is available.
	 * @throws IllegalArgumentException if {@code group} is not defined by the
	 * pattern.
	 */
	static MatchResult group(String group, Matcher matcher) {
		return new DefaultMatchResult(matcher.group(group), matcher.start(group), matcher.end(group));
	}

	/**
	 * Return a match function that treats the supplied string as a literal token.
	 *
	 * <p>
	 * The returned function delegates to {@link String#indexOf(String, int)};
	 * pattern syntax and case folding are not applied.
	 *
	 * @param str the literal text to locate; must contain at least one
	 * non-whitespace character.
	 * @return a match function that returns each occurrence of {@code str} at or
	 * after the supplied start index.
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
	 * Return the shared result used to signal that no further match is available.
	 *
	 * @return a result whose {@link MatchResult#hasMatch()} returns
	 * {@literal false}.
	 */
	static MatchResult noMatch() {
		return AbsentMatchResult.ABSENT;
	}

	/**
	 * Return a match result for an already known decoded-text range.
	 *
	 * @param text the text returned from {@link MatchResult#group()}, not the full
	 * source text; must not be {@literal null}.
	 * @param start the decoded-text start offset, inclusive.
	 * @param end the decoded-text end offset, exclusive.
	 * @return a match result exposing the supplied group text and offsets.
	 */
	static MatchResult match(String text, int start, int end) {
		return new DefaultMatchResult(text, start, end);
	}


}
