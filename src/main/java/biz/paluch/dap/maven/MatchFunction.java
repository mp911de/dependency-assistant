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

package biz.paluch.dap.maven;

import java.util.regex.MatchResult;
import java.util.regex.Matcher;

import org.springframework.util.Assert;

/**
 * Strategy interface used to locate successive matches in decoded property
 * text.
 *
 * <p>The function receives the decoded text and the index at which the next
 * search should begin. It returns either a regular {@link MatchResult} or
 * {@link #noMatch()} to signal completion.
 *
 * @author Mark Paluch
 */
public interface MatchFunction {

	/**
	 * Find the next match in the given text.
	 * @param text the decoded text to search.
	 * @param startIndex the index at which the search should begin.
	 * @return the next match, or {@link #noMatch()} if no match is available.
	 */
	MatchResult find(String text, int startIndex);

	/**
	 * Return a match result for the named group of the given matcher.
	 * @param group the group name to expose as a match result.
	 * @param matcher the matcher that produced the group.
	 * @return a match result covering the named group.
	 */
	static MatchResult group(String group, Matcher matcher) {
		return new DefaultMatchResult(matcher.group(group), matcher.start(group), matcher.end(group));
	}

	/**
	 * Return a match function that finds the next occurrence of the given string.
	 * @param str the string to search for.
	 * @return a match function using {@link String#indexOf(String, int)}.
	 * @throws IllegalArgumentException if the search string is empty.
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
	 * Return a match result representing the absence of a match.
	 * @return a shared non-matching result.
	 */
	static MatchResult noMatch() {
		return AbsentMatchResult.ABSENT;
	}

}
