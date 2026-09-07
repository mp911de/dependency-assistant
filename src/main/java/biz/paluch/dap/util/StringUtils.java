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

import java.util.Collection;
import java.util.Iterator;
import java.util.regex.Pattern;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Miscellaneous {@link String} utility methods.
 *
 * @author Mark Paluch
 */
public abstract class StringUtils {

	private static final Pattern BLANK_LINES = Pattern.compile("(?:\\r?\\n[ \\t]*){3,}");

	/**
	 * Return whether the value is absent, empty, or whitespace-only.
	 */
	@Contract("null -> true")
	public static boolean isEmpty(@Nullable CharSequence str) {
		return !hasText(str);
	}

	/**
	 * Return whether the value is absent, empty, or whitespace-only.
	 */
	@Contract("null -> true")
	public static boolean isEmpty(@Nullable String str) {
		return !hasText(str);
	}

	/**
	 * Return whether the value contains a non-whitespace character.
	 */
	@Contract("null -> false")
	public static boolean hasText(@Nullable CharSequence str) {
		if (str == null) {
			return false;
		}

		int strLen = str.length();
		if (strLen == 0) {
			return false;
		}

		for (int i = 0; i < strLen; i++) {
			if (!Character.isWhitespace(str.charAt(i))) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return whether the value contains a non-whitespace character.
	 */
	@Contract("null -> false")
	public static boolean hasText(@Nullable String str) {
		return (str != null && !str.isBlank());
	}

	/**
	 * Remove surrounding single or double quotes if present.
	 */
	public static String unquote(String str) {
		return StringUtil.unquoteString(str);
	}

	/**
	 * Collapse runs of blank lines into a single blank line and strip surrounding
	 * whitespace.
	 */
	public static String collapseBlankLines(String text) {
		return BLANK_LINES.matcher(text).replaceAll("\n\n").strip().trim();
	}

	/**
	 * Return the longest shared prefix, or empty if none.
	 *
	 * @throws IllegalArgumentException if the collection is empty.
	 */
	public static String longestCommonPrefix(Collection<String> values) {
		Assert.notEmpty(values, "Values must not be empty");
		Iterator<String> iterator = values.iterator();
		String prefix = iterator.next();
		while (iterator.hasNext()) {
			prefix = prefix.substring(0, StringUtil.commonPrefixLength(prefix, iterator.next()));
			if (prefix.isEmpty()) {
				return "";
			}
		}
		return prefix;
	}

	/**
	 * Return the longest shared suffix, or empty if none.
	 *
	 * @throws IllegalArgumentException if the collection is empty.
	 */
	public static String longestCommonSuffix(Collection<String> values) {
		Assert.notEmpty(values, "Values must not be empty");
		Iterator<String> iterator = values.iterator();
		String suffix = iterator.next();
		while (iterator.hasNext()) {
			suffix = suffix.substring(suffix.length() - StringUtil.commonSuffixLength(suffix, iterator.next()));
			if (suffix.isEmpty()) {
				return "";
			}
		}
		return suffix;
	}
}
