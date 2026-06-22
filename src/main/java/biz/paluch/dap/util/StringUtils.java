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

import java.util.Collection;
import java.util.Iterator;

import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Miscellaneous {@link String} utility methods.
 *
 * @author Mark Paluch
 */
public abstract class StringUtils {

	// ---------------------------------------------------------------------
	// General convenience methods for working with Strings
	// ---------------------------------------------------------------------

	/**
	 * Check whether the given {@code CharSequence} is empty.
	 * @param str the candidate string; can be {@literal null}.
	 */
	@Contract("null -> true")
	public static boolean isEmpty(@Nullable CharSequence str) {
		return !hasText(str);
	}

	/**
	 * Check whether the given {@code String} is empty.
	 * @param str the candidate string; can be {@literal null}.
	 */
	@Contract("null -> true")
	public static boolean isEmpty(@Nullable String str) {
		return !hasText(str);
	}

	/**
	 * Check whether the given {@code CharSequence} contains actual <em>text</em>.
	 * <p>
	 * Return {@literal true} if the value is not
	 * {@literal null}, its length is greater than 0, and it contains at least one
	 * non-whitespace character.
	 * <p>
	 * <pre class="code">
	 * StringUtils.hasText(null) = false
	 * StringUtils.hasText("") = false
	 * StringUtils.hasText(" ") = false
	 * StringUtils.hasText("12345") = true
	 * StringUtils.hasText(" 12345 ") = true
	 * </pre>
	 * @param str the {@code CharSequence} to check; can be {@literal null}.
	 * @return {@literal true} if the {@code CharSequence} contains text.
	 * @see Character#isWhitespace
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
	 * Check whether the given {@code String} contains actual <em>text</em>.
	 * @param str the {@code String} to check; can be {@literal null}.
	 * @return {@literal true} if the {@code String} contains text.
	 * @see #hasText(CharSequence)
	 * @see Character#isWhitespace
	 */
	@Contract("null -> false")
	public static boolean hasText(@Nullable String str) {
		return (str != null && !str.isBlank());
	}

	/**
	 * Remove quotes from a String if it is quoted with either single or double
	 * quotes.
	 * @param str the String to unquote.
	 * @return the unquoted String.
	 */
	public static String unquote(String str) {
		if ((str.startsWith("\"") && str.endsWith("\"")) || (str.startsWith("'") && str.endsWith("'"))) {
			return str.length() > 2 ? str.substring(1, str.length() - 1) : "";
		}
		return str;
	}

	public static String longestCommonPrefix(Collection<String> values) {
		Assert.notEmpty(values, "Values must not be empty");
		Iterator<String> iterator = values.iterator();
		String first = iterator.next();
		int length = first.length();
		for (String value : values) {
			length = Math.min(length, value.length());
			for (int i = 0; i < length; i++) {
				if (first.charAt(i) != value.charAt(i)) {
					length = i;
					break;
				}
			}
		}
		return first.substring(0, length);
	}
}
