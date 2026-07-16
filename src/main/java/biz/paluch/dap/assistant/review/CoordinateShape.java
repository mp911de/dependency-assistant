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

package biz.paluch.dap.assistant.review;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * The shared coordinate shape of the artifact ids in one upgrade group: the
 * word-boundary base, prefix, and suffix the members have in common, computed
 * once per member set. The shape derives both the {@code Inferred Upgrade
 * Group} name ({@link #deriveGroupName(String)}) and the group-row member label
 * ({@link #memberLabelParts()}).
 *
 * <p>A boundary is a {@code '-'} or {@code '.'} separator. A shared prefix,
 * suffix, or base is only meaningful when it ends or starts on such a boundary,
 * so {@code httpcore5} is never read as a base of {@code httpcore5reactive}.
 *
 * @author Mark Paluch
 * @see UpgradeRows
 * @see GroupRow
 */
class CoordinateShape {

	private final List<String> artifactIds;

	private final @Nullable String base;

	private final @Nullable String separatorPrefix;

	private CoordinateShape(List<String> artifactIds) {

		this.artifactIds = artifactIds;
		this.base = commonBase(this.artifactIds);
		this.separatorPrefix = commonSeparatorPrefix(this.artifactIds);
	}

	/**
	 * Create the shape of the given member artifact ids.
	 *
	 * @param artifactIds the member artifact ids; must not be empty.
	 * @return the computed shape.
	 */
	public static CoordinateShape of(List<String> artifactIds) {
		return new CoordinateShape(artifactIds);
	}

	/**
	 * Derive the {@code Inferred Upgrade Group} name for members under one
	 * {@code groupId}.
	 *
	 * <p>The name is the common word-boundary prefix of the members after
	 * {@code Abbreviation Promotion}: a bare acronym prefix (at most three letters)
	 * is expanded to the longer {@code groupId} segment it abbreviates, so
	 * {@code org.bouncycastle:bc-*} reads as {@code bouncycastle} rather than
	 * {@code bc}.
	 *
	 * @param groupId the shared group id of the members.
	 * @return the derived name, or {@literal null} if the members share no usable
	 * word-boundary prefix of at least two characters.
	 */
	@Nullable
	public String deriveGroupName(String groupId) {

		String prefix = boundaryPrefix();
		if (prefix == null || prefix.length() < 2) {
			return null;
		}

		return promoteAbbreviation(prefix, groupId);
	}

	/**
	 * Return the member-label parts for the group row: the common base followed by
	 * each member's suffix, the suffixes after the common separator prefix, or the
	 * prefixes before the common separator suffix, in that fallback order.
	 *
	 * @return the label parts, or an empty list when the members share no usable
	 * shape.
	 */
	public List<String> memberLabelParts() {

		List<String> parts = baseLabelParts();
		if (parts.isEmpty()) {
			parts = separatorPrefixLabelParts();
		}
		if (parts.isEmpty()) {
			parts = separatorSuffixLabelParts();
		}

		return parts;
	}

	/**
	 * The common word-boundary prefix backing the derived name: the common base, or
	 * the common separator prefix without its trailing separator.
	 */
	private @Nullable String boundaryPrefix() {

		if (base != null) {
			return base;
		}

		return separatorPrefix != null ? separatorPrefix.substring(0, separatorPrefix.length() - 1) : null;
	}

	/**
	 * Label the members as the common base followed by each member's suffix after
	 * the base separator, or empty when there is no common base or a member adds no
	 * suffix.
	 */
	private List<String> baseLabelParts() {

		if (base == null) {
			return List.of();
		}

		List<String> suffixes = new ArrayList<>(artifactIds.size() - 1);
		for (String artifactId : artifactIds) {
			if (artifactId.equals(base)) {
				continue;
			}
			String suffix = artifactId.substring(base.length() + 1);
			if (suffix.isEmpty()) {
				return List.of();
			}
			suffixes.add(suffix);
		}

		Collections.sort(suffixes);
		List<String> parts = new ArrayList<>(artifactIds.size());
		parts.add(base);
		parts.addAll(suffixes);
		return parts;
	}

	/**
	 * Label the members by their suffixes after the common separator prefix, or
	 * empty when there is no separator prefix or a member has no suffix.
	 */
	private List<String> separatorPrefixLabelParts() {

		if (separatorPrefix == null) {
			return List.of();
		}

		List<String> suffixes = artifactIds.stream()
				.map(id -> id.substring(separatorPrefix.length()))
				.filter(value -> !value.isEmpty())
				.sorted()
				.toList();
		return suffixes.size() == artifactIds.size() ? suffixes : List.of();
	}

	/**
	 * Label the members by their prefixes before the common separator suffix, or
	 * empty when there is no separator suffix or a member has no prefix.
	 */
	private List<String> separatorSuffixLabelParts() {

		String suffix = commonSeparatorSuffix(artifactIds);
		if (suffix == null) {
			return List.of();
		}

		List<String> prefixes = artifactIds.stream()
				.map(id -> id.substring(0, id.length() - suffix.length()))
				.filter(value -> !value.isEmpty())
				.sorted()
				.toList();
		return prefixes.size() == artifactIds.size() ? prefixes : List.of();
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

	private static @Nullable String commonBase(List<String> artifactIds) {

		List<String> bases = new ArrayList<>(artifactIds);
		bases.sort(Comparator.comparingInt(String::length).thenComparing(Comparator.naturalOrder()));

		for (String base : bases) {
			if (isBaseOfAll(base, artifactIds)) {
				return base;
			}
		}

		return null;
	}

	private static @Nullable String commonSeparatorPrefix(List<String> artifactIds) {
		String prefix = StringUtils.longestCommonPrefix(artifactIds);
		int separator = lastSeparator(prefix);
		if (separator < 0) {
			return null;
		}
		return prefix.substring(0, separator + 1);
	}

	private static @Nullable String commonSeparatorSuffix(List<String> artifactIds) {

		String suffix = StringUtils.longestCommonSuffix(artifactIds);
		if (suffix.isEmpty() || !isSeparator(suffix.charAt(0))) {
			return null;
		}

		return suffix;
	}

	private static boolean isBaseOfAll(String base, List<String> artifactIds) {

		boolean hasBase = false;
		for (String artifactId : artifactIds) {
			if (artifactId.equals(base)) {
				hasBase = true;
				continue;
			}

			if (!artifactId.startsWith(base) || artifactId.length() <= base.length()
					|| !isSeparator(artifactId.charAt(base.length()))) {
				return false;
			}
		}

		return hasBase;
	}

	private static boolean isSeparator(char c) {
		return c == '-' || c == '.';
	}

	private static int lastSeparator(String value) {
		return Math.max(value.lastIndexOf('-'), value.lastIndexOf('.'));
	}

}
