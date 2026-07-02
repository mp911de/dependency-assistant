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

package biz.paluch.dap.assistant.check;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.jspecify.annotations.Nullable;

/**
 * The label of an {@code Inferred Upgrade Group}, derived from the shared
 * coordinate shape of its members.
 *
 * <p>The label is the common word-boundary prefix of the member artifact ids
 * after {@code Abbreviation  Promotion}: a bare acronym prefix (at most three
 * letters) is expanded to the longer {@code groupId} segment it abbreviates, so
 * {@code org.bouncycastle:bc-*} reads as {@code bouncycastle} rather than
 * {@code bc}. A group without a usable shared prefix has no derived name.
 *
 * <p>Equality is by the resulting label; two groups that yield the same label
 * are the same name regardless of how each reached it.
 *
 * @author Mark Paluch
 * @see UpgradeGroups
 */
// TODO: refactor
class DerivedGroupName {

	private final String displayName;

	private DerivedGroupName(String displayName) {
		this.displayName = displayName;
	}

	/**
	 * Derive the group name for the given members under one {@code groupId}.
	 *
	 * @param groupId the shared group id of the members.
	 * @param artifactIds the member artifact ids; must share a {@code groupId}.
	 * @return the derived name, or {@literal null} if the members share no usable
	 * word-boundary prefix of at least two characters.
	 */
	static @Nullable DerivedGroupName of(String groupId, List<String> artifactIds) {

		String prefix = commonBoundaryPrefix(artifactIds);
		if (prefix == null || prefix.length() < 2) {
			return null;
		}

		return new DerivedGroupName(promoteAbbreviation(prefix, groupId));
	}

	/**
	 * Return the label shown as the {@code Inferred Upgrade Group} row name.
	 *
	 * @return the derived label.
	 */
	String getDisplayName() {
		return displayName;
	}

	/**
	 * Promote a bare acronym prefix (at most three alphabetic characters, no
	 * internal separator) to the longest {@code groupId} segment it abbreviates, or
	 * keep the prefix when nothing qualifies.
	 */
	private static String promoteAbbreviation(String prefix, String groupId) {

		if (prefix.length() > 3 || !isAcronym(prefix)) {
			return prefix;
		}

		String promoted = prefix;
		for (String segment : groupId.split("\\.")) {
			if (segment.length() > promoted.length() && segment.charAt(0) == prefix.charAt(0)
					&& isSubsequence(prefix, segment) && hasNoDigit(segment)) {
				promoted = segment;
			}
		}

		return promoted;
	}

	private static boolean isAcronym(String prefix) {

		for (int i = 0; i < prefix.length(); i++) {
			if (!Character.isLetter(prefix.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static boolean isSubsequence(String shorter, String longer) {

		int index = 0;
		for (int i = 0; i < longer.length() && index < shorter.length(); i++) {
			if (longer.charAt(i) == shorter.charAt(index)) {
				index++;
			}
		}
		return index == shorter.length();
	}

	private static boolean hasNoDigit(String value) {

		for (int i = 0; i < value.length(); i++) {
			if (Character.isDigit(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static @Nullable String commonBoundaryPrefix(List<String> artifactIds) {

		String base = basePrefix(artifactIds);
		if (base != null) {
			return base;
		}

		String lcp = longestCommonPrefix(artifactIds);
		int separator = Math.max(lcp.lastIndexOf('-'), lcp.lastIndexOf('.'));
		if (separator <= 0) {
			return null;
		}

		return lcp.substring(0, separator);
	}

	private static @Nullable String basePrefix(List<String> artifactIds) {

		List<String> bases = new ArrayList<>(artifactIds);
		bases.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));

		for (String base : bases) {
			if (isBaseOfAll(base, artifactIds)) {
				return base;
			}
		}

		return null;
	}

	private static boolean isBaseOfAll(String base, List<String> artifactIds) {

		boolean hasBase = false;
		for (String artifactId : artifactIds) {
			if (artifactId.equals(base)) {
				hasBase = true;
				continue;
			}

			if (!artifactId.startsWith(base) || artifactId.length() <= base.length()) {
				return false;
			}

			char separator = artifactId.charAt(base.length());
			if (separator != '-' && separator != '.') {
				return false;
			}
		}

		return hasBase;
	}

	private static String longestCommonPrefix(List<String> strings) {

		String first = strings.getFirst();
		int len = first.length();
		for (String s : strings) {
			len = Math.min(len, s.length());
			for (int i = 0; i < len; i++) {
				if (first.charAt(i) != s.charAt(i)) {
					len = i;
					break;
				}
			}
		}
		return first.substring(0, len);
	}

	@Override
	public boolean equals(Object o) {

		if (!(o instanceof DerivedGroupName that)) {
			return false;
		}
		return displayName.equals(that.displayName);
	}

	@Override
	public int hashCode() {
		return displayName.hashCode();
	}

	@Override
	public String toString() {
		return displayName;
	}

}
