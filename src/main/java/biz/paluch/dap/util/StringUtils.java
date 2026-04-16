package biz.paluch.dap.util;

import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;

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
	 * Check whether the given {@code CharSequence}is empty. This is effectively a
	 * shortcut for {@code !hasText(CharSequence)}.
	 *
	 * @param str the candidate string.
	 */
	@Contract("null -> true")
	public static boolean isEmpty(@Nullable CharSequence str) {
		return !hasText(str);
	}

	/**
	 * Check whether the given object (possibly a {@code String}) is empty. This is
	 * effectively a shortcut for {@code !hasText(String)}.
	 *
	 * @param str the candidate object (possibly a {@code String}).
	 */
	@Contract("null -> true")
	public static boolean isEmpty(@Nullable String str) {
		return !hasText(str);
	}

	/**
	 * Check whether the given {@code CharSequence} contains actual <em>text</em>.
	 * <p>More specifically, this method returns {@code true} if the
	 * {@code CharSequence} is not {@code null}, its length is greater than 0, and
	 * it contains at least one non-whitespace character.
	 * <p><pre class="code">
	 * StringUtils.hasText(null) = false
	 * StringUtils.hasText("") = false
	 * StringUtils.hasText(" ") = false
	 * StringUtils.hasText("12345") = true
	 * StringUtils.hasText(" 12345 ") = true
	 * </pre>
	 * @param str the {@code CharSequence} to check (may be {@code null}).
	 * @return {@code true} if the {@code CharSequence} is not {@code null}, its
	 * length is greater than 0, and it does not contain whitespace only.
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
	 * <p>More specifically, this method returns {@code true} if the {@code String}
	 * is not {@code null}, its length is greater than 0, and it contains at least
	 * one non-whitespace character.
	 * @param str the {@code String} to check (may be {@code null}).
	 * @return {@code true} if the {@code String} is not {@code null}, its length is
	 * greater than 0, and it does not contain whitespace only.
	 * @see #hasText(CharSequence)
	 * @see Character#isWhitespace
	 */
	@Contract("null -> false")
	public static boolean hasText(@Nullable String str) {
		return (str != null && !str.isBlank());
	}

}
