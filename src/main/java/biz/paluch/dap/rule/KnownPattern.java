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

package biz.paluch.dap.rule;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Literal pattern with {@code *} wildcards.
 *
 * <p>Patterns originate from untrusted project descriptors, so matching walks
 * literal segments with plain string search and never backtracks, regardless of
 * pattern length or wildcard count.
 *
 * @author Mark Paluch
 */
class KnownPattern implements Predicate<String> {

	public static final KnownPattern ANY = new KnownPattern("*");

	private final String pattern;

	private final List<String> segments;

	private final boolean wildcard;

	private final boolean leadingWildcard;

	private final boolean trailingWildcard;

	private KnownPattern(String pattern) {

		this.pattern = pattern;
		this.segments = literalSegments(pattern);
		this.wildcard = pattern.indexOf('*') != -1;
		this.leadingWildcard = pattern.startsWith("*");
		this.trailingWildcard = pattern.endsWith("*");
	}

	public static KnownPattern of(String pattern) {

		if ("*".equals(pattern)) {
			return ANY;
		}
		return new KnownPattern(pattern);
	}

	public String getPattern() {
		return pattern;
	}

	@Override
	public boolean test(String s) {

		if (this == ANY) {
			return true;
		}
		if (!wildcard) {
			return pattern.equals(s);
		}
		if (segments.isEmpty()) {
			return true;
		}
		return matchesSegments(s);
	}

	/**
	 * Match by anchoring the first and last segments at the value boundaries and
	 * locating the remaining segments greedily left to right.
	 */
	private boolean matchesSegments(String value) {

		int position = 0;
		int first = 0;
		int last = segments.size();

		if (!leadingWildcard) {
			String head = segments.get(first);
			if (!value.startsWith(head)) {
				return false;
			}
			position = head.length();
			first++;
		}

		int limit = value.length();
		if (!trailingWildcard) {
			String tail = segments.get(last - 1);
			limit = value.length() - tail.length();
			if (limit < position || !value.startsWith(tail, limit)) {
				return false;
			}
			last--;
		}

		for (int i = first; i < last; i++) {

			String segment = segments.get(i);
			int index = value.indexOf(segment, position);
			if (index == -1 || index + segment.length() > limit) {
				return false;
			}
			position = index + segment.length();
		}

		return true;
	}

	private static List<String> literalSegments(String pattern) {

		List<String> segments = new ArrayList<>();
		int start = 0;
		while (start < pattern.length()) {

			int star = pattern.indexOf('*', start);
			if (star == -1) {
				segments.add(pattern.substring(start));
				break;
			}
			if (star > start) {
				segments.add(pattern.substring(start, star));
			}
			start = star + 1;
		}
		return List.copyOf(segments);
	}

	@Override
	public String toString() {
		return pattern;
	}

}
