/*
 * Copyright 2026-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap.support;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jspecify.annotations.Nullable;

/**
 * Utility to resolve property placeholders.
 * @author Mark Paluch
 */
class PropertyResolverUtil {

	private static final Pattern PLACEHOLDER_PATTERN = Pattern
			.compile("\\$\\{([^}]+)\\}|\\$([a-zA-Z_][a-zA-Z0-9_]*)");

	static final int MAX_RESOLUTION_DEPTH = 10;

	/**
	 * Resolves all {@code ${key}} and {@code $key} placeholders within
	 * {@code value} using {@code resolver}. Unresolved placeholders are left
	 * in-place so that callers can detect them. Returns {@code null} if
	 * {@code value} is {@code null}.
	 * <p>After this call, callers must check that the result contains no residual
	 * placeholder tokens (via {@link #hasUnresolvedPlaceholder(String)}) before
	 * accepting the value as fully resolved.
	 */
	static @Nullable String resolveInterpolated(@Nullable String value, PropertyResolver resolver) {

		if (value == null) {
			return null;
		}

		Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);
		StringBuilder result = new StringBuilder();
		int end = 0;
		while (matcher.find()) {
			result.append(value, end, matcher.start());
			end = matcher.end();
			String key = matcher.group(1) != null ? matcher.group(1).trim() : matcher.group(2).trim();
			String replacement = resolver.getProperty(key);
			if (replacement == null) {
				result.append(matcher.group(0));
			} else {
				result.append(replacement);
			}
		}
		result.append(value.substring(end));
		return result.toString();
	}

	/**
	 * Resolves {@code value} by repeatedly applying {@link #resolveInterpolated}
	 * until no placeholders remain, no progress is made, or the iteration cap is
	 * reached.
	 */
	static @Nullable String resolvePlaceholders(@Nullable String value, PropertyResolver resolver) {

		if (value == null) {
			return null;
		}

		String current = value;
		for (int i = 0; i < MAX_RESOLUTION_DEPTH; i++) {
			String next = resolveInterpolated(current, resolver);
			if (next == null) {
				return null;
			}
			if (!hasUnresolvedPlaceholder(next)) {
				return next;
			}
			if (next.equals(current)) {
				return next;
			}
			current = next;
		}
		return current;
	}

	static boolean hasUnresolvedPlaceholder(@Nullable String value) {

		if (value == null) {
			return false;
		}
		return PLACEHOLDER_PATTERN.matcher(value).find();
	}

}
