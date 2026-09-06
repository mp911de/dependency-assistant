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

package biz.paluch.dap.artifact;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Shared artifact-name structure for group names and compact member labels.
 * <p>Common parts end at {@code -} or {@code .} boundaries. For example,
 * {@code httpcore5} is not a base of {@code httpcore5reactive}.
 *
 * @author Mark Paluch
 */
// TODO: refactor
public class CoordinateShape {

	private final List<String> artifactIds;

	private final @Nullable String base;

	private final @Nullable String separatorPrefix;

	private CoordinateShape(List<String> artifactIds) {

		this.artifactIds = artifactIds;
		this.base = commonBase(this.artifactIds);
		this.separatorPrefix = commonSeparatorPrefix(this.artifactIds);
	}

	/**
	 * Analyze member names. The supplied list must not change while the shape is
	 * used.
	 */
	public static CoordinateShape of(List<String> artifactIds) {
		return new CoordinateShape(artifactIds);
	}

	/**
	 * Derive a group name from the shared prefix of its members.
	 * <p>Short acronyms may expand to a group-id segment, so
	 * {@code org.bouncycastle:bc-*} reads as {@code bouncycastle}.
	 * @return {@literal null} if no usable shared prefix exists.
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
	 * Return compact member labels, or an empty list if no shared shape is usable.
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

	private @Nullable String boundaryPrefix() {

		if (base != null) {
			return base;
		}

		return separatorPrefix != null ? separatorPrefix.substring(0, separatorPrefix.length() - 1) : null;
	}

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
