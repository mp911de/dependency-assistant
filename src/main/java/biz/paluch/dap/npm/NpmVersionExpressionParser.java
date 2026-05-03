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

package biz.paluch.dap.npm;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.util.StringUtils;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

/**
 * Pure-string classifier that turns the raw value of an entry in
 * {@code dependencies}/{@code devDependencies} into an
 * {@link NpmVersionExpression}.
 *
 * <p>Inputs that fall outside the supported grammar return {@literal null};
 * callers must skip the entry instead of branching on a sentinel. The
 * classifier does not perform package-name allowlist validation; that is the
 * {@link NpmPackageParser parser}'s responsibility.
 *
 * @author Mark Paluch
 */
final class NpmVersionExpressionParser {

	private static final Pattern SEMVER = Pattern
			.compile("\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?");

	private static final Pattern PREFIX_RANGE = Pattern
			.compile("\\d+(?:\\.\\d+)*\\.(?:x|X|\\*)(?:\\.(?:x|X|\\*))*|\\d+\\.(?:x|X|\\*)|(?:x|X|\\*)");

	private static final Pattern HYPHEN_RANGE = Pattern
			.compile(
					"(\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?)\\s+-\\s+(\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?)");

	private static final Pattern COMPARATOR_PAIR = Pattern.compile(
			"^((?:>|>=|<|<=|=)?\\s*\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?\\s+)((?:>|>=|<|<=|=)?)(\\d+(?:\\.\\d+){0,2}(?:[-+][0-9A-Za-z.-]+)?)$");

	private static final Pattern ALIAS = Pattern
			.compile("npm:(@?[a-z0-9][a-z0-9._-]*(?:/[a-z0-9][a-z0-9._-]*)?)@(.+)$");

	private NpmVersionExpressionParser() {
	}

	/**
	 * Classify the raw value of a {@code package.json} dependency entry.
	 * @param raw the dependency value text without surrounding quotes; can be
	 * {@literal null}.
	 * @return the parsed expression, or {@literal null} if the value is out of
	 * scope or not classifiable.
	 */
	@Contract("null -> null")
	static @Nullable NpmVersionExpression parse(@Nullable String raw) {

		if (StringUtils.isEmpty(raw)) {
			return null;
		}

		String value = raw.trim();
		if (value.isEmpty() || value.contains("||")) {
			return null;
		}

		Matcher aliasMatcher = ALIAS.matcher(value);
		if (aliasMatcher.matches()) {
			NpmVersionExpression inner = parseLiteral(aliasMatcher.group(2));
			if (inner == null || inner instanceof NpmVersionExpression.Alias) {
				return null;
			}
			return new NpmVersionExpression.Alias(aliasMatcher.group(1), inner);
		}

		return parseLiteral(value);
	}

	private static @Nullable NpmVersionExpression parseLiteral(String value) {

		String trimmed = value.trim();
		if (trimmed.isEmpty() || trimmed.equals("*") || trimmed.equalsIgnoreCase("latest")) {
			return null;
		}

		if (PREFIX_RANGE.matcher(trimmed).matches()) {
			return new NpmVersionExpression.Prefix(trimmed);
		}

		Matcher hyphen = HYPHEN_RANGE.matcher(trimmed);
		if (hyphen.matches()) {
			String upper = hyphen.group(2);
			String prefix = trimmed.substring(0, hyphen.start(2));
			return new NpmVersionExpression.RangeUpper(prefix, upper);
		}

		Matcher pair = COMPARATOR_PAIR.matcher(trimmed);
		if (pair.matches()) {
			String upper = pair.group(3);
			int upperStart = pair.start(3);
			String prefix = trimmed.substring(0, upperStart);
			// Reject if the upper bound region looks like a Prefix range.
			if (PREFIX_RANGE.matcher(upper).matches()) {
				return null;
			}
			return new NpmVersionExpression.RangeUpper(prefix, upper);
		}

		return parseExact(trimmed);
	}

	private static @Nullable NpmVersionExpression parseExact(String value) {

		if (value.isEmpty()) {
			return null;
		}

		String modifier = "";
		String tail = value;
		char first = value.charAt(0);
		if (first == '^' || first == '~' || first == '=') {
			modifier = String.valueOf(first);
			tail = value.substring(1);
		} else if (first == 'v' && value.length() > 1 && Character.isDigit(value.charAt(1))) {
			tail = value;
		}

		if (tail.isEmpty()) {
			return null;
		}

		return new NpmVersionExpression.Exact(modifier, tail);
	}

}
