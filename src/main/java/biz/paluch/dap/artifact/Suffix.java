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

package biz.paluch.dap.artifact;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Version suffix such as {@code SNAPSHOT}, {@code M1}, {@code RC1} or
 * {@code RELEASE}.
 */
interface Suffix extends Comparable<Suffix> {

	String BUILD_SNAPSHOT_SUFFIX = "BUILD-SNAPSHOT";

	String SNAPSHOT_MODIFIER = "SNAPSHOT";

	String RELEASE_SUFFIX = "RELEASE";

	String MILESTONE_SUFFIX = "M\\d+";

	String RC_SUFFIX = "RC\\d+";

	Pattern MILESTONE_OR_RC_PATTERN = Pattern.compile("(SR|RC|M)(\\d+)");

	Pattern SEMVER_SUFFIX = Pattern.compile("([a-zA-Z]+)([.-])?(\\d*)");

	Pattern NUMERIC_PRE_RELEASE_SUFFIX = Pattern.compile("\\d+(\\.\\d+)*");

	Set<String> MILESTONE_TYPES = Set.of("", "m", "alpha", "a", "beta", "b", "dev", "nightly", "canary",
			"pre", "next", "preview", "experimental");

	Set<String> RELEASE_CANDIDATE_TYPES = Set.of("rc", "cr");

	Map<String, Integer> STABILITY_RANKS = mapOf(it -> {
		it.put("", 0);
		it.put("dev", 0);
		it.put("nightly", 0);
		it.put("canary", 0);
		it.put("experimental", 0);
		it.put("alpha", 1);
		it.put("a", 1);
		it.put("beta", 2);
		it.put("b", 2);
		it.put("pre", 3);
		it.put("preview", 3);
		it.put("m", 3);
		it.put("next", 3);
		it.put("rc", 4);
		it.put("cr", 4);
		it.put("sr", 5);
	});

	Map<String, Integer> TYPE_ORDER = mapOf(it -> {
		it.put("", 0);
		it.put("dev", 1);
		it.put("nightly", 2);
		it.put("canary", 3);
		it.put("experimental", 4);
		it.put("alpha", 5);
		it.put("a", 6);
		it.put("beta", 7);
		it.put("b", 8);
		it.put("pre", 9);
		it.put("preview", 10);
		it.put("m", 11);
		it.put("next", 12);
		it.put("rc", 13);
		it.put("cr", 14);
		it.put("sr", 15);
	});

	/**
	 * One capturing group for the full semver-style qualifier (e.g. {@code B02},
	 * {@code RC1}, {@code alpha.1}, or {@code 0.3.7}). Use in compound patterns so
	 * the qualifier is not split across regex groups.
	 */
	String SEMVER_QUALIFIER_PATTERN = "([0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)";

	String VALID_SUFFIX = String.format("%s|%s|%s|%s|-%s|-%s|-%s", RELEASE_SUFFIX, MILESTONE_SUFFIX, RC_SUFFIX,
			Suffix.BUILD_SNAPSHOT_SUFFIX, RELEASE_SUFFIX, MILESTONE_SUFFIX, SNAPSHOT_MODIFIER);


	static Map<String, Integer> mapOf(Consumer<Map<String, Integer>> consumer) {
		Map<String, Integer> map = new LinkedHashMap<>();
		consumer.accept(map);

		return Map.copyOf(map);
	}

	/**
	 * Parse the suffix into a {@link Suffix} instance.
	 *
	 * @param suffix the raw suffix text to parse, or {@code null} for a release
	 * suffix.
	 * @return the parsed suffix.
	 */
	static Suffix parse(@Nullable String suffix) {

		if (StringUtils.isEmpty(suffix)) {
			return Release.INSTANCE;
		}

		String candidate = suffix.strip();
		if (StringUtils.isEmpty(candidate)) {
			return Release.INSTANCE;
		}

		Suffix knownSuffix = parseKnownSuffix(candidate);
		if (knownSuffix != null) {
			return knownSuffix;
		}

		Suffix milestoneOrReleaseCandidate = parseMilestoneOrReleaseCandidate(candidate);
		if (milestoneOrReleaseCandidate != null) {
			return milestoneOrReleaseCandidate;
		}

		Suffix singleSegment = parseSingleSegment(candidate);
		if (singleSegment != null) {
			return singleSegment;
		}

		Suffix multiSegment = parseMultiSegment(candidate);
		return multiSegment != null ? multiSegment : new Generic(candidate);
	}

	private static @Nullable Suffix parseKnownSuffix(String suffix) {

		if (suffix.equals(RELEASE_SUFFIX)) {
			return Release.RELEASE;
		}

		if (suffix.equalsIgnoreCase("Final")) {
			return new Release(suffix);
		}

		if (suffix.equalsIgnoreCase(BUILD_SNAPSHOT_SUFFIX)) {
			return Snapshot.BUILD_SNAPSHOT;
		}
		if (suffix.equalsIgnoreCase(SNAPSHOT_MODIFIER)) {
			return Snapshot.INSTANCE;
		}

		return null;
	}

	private static @Nullable Suffix parseMilestoneOrReleaseCandidate(String suffix) {

		Matcher milestoneMatcher = MILESTONE_OR_RC_PATTERN.matcher(suffix);
		if (!milestoneMatcher.matches()) {
			return null;
		}

		String type = milestoneMatcher.group(1);
		String digits = milestoneMatcher.group(2);
		return new SemVerSuffix(type, Integer.parseInt(digits), "", digits);
	}

	private static @Nullable Suffix parseSingleSegment(String suffix) {

		Matcher matcher = SEMVER_SUFFIX.matcher(suffix);
		if (!matcher.matches()) {
			return null;
		}

		String type = matcher.group(1);
		String separator = matcher.group(2);
		String counter = matcher.group(3);
		if (type.equalsIgnoreCase(SNAPSHOT_MODIFIER) && StringUtils.isEmpty(counter) && separator == null) {
			return Snapshot.INSTANCE;
		}
		if (StringUtils.isEmpty(counter)) {
			return new SemVerSuffix(type, -1, separator, "");
		}
		return new SemVerSuffix(type, Integer.parseInt(counter), separator, counter);
	}

	private static @Nullable Suffix parseMultiSegment(String suffix) {
		return NUMERIC_PRE_RELEASE_SUFFIX.matcher(suffix).matches() ? NumericPreReleaseSuffix.from(suffix)
				: MultiSegmentSuffix.from(suffix);
	}

	/**
	 * Canonical suffix representation.
	 */
	String canonical();

	/**
	 * Return whether this suffix identifies a milestone-like pre-release.
	 */
	default boolean isMilestone() {
		return false;
	}

	/**
	 * Return whether this suffix identifies a release candidate.
	 */
	default boolean isReleaseCandidate() {
		return false;
	}

	private static boolean isPreRelease(Suffix suffix) {
		return suffix instanceof PreReleaseSuffix || suffix instanceof Generic;
	}

	private static boolean isServiceRelease(Suffix suffix) {
		return suffix instanceof PreReleaseSuffix preRelease && preRelease.isServiceRelease();
	}

	private static int comparePreRelease(PreReleaseSuffix left, PreReleaseSuffix right) {

		int rank = Integer.compare(stabilityRank(left.getCanonicalType()), stabilityRank(right.getCanonicalType()));
		if (rank != 0) {
			return rank;
		}

		int type = compareType(left.getCanonicalType(), right.getCanonicalType());
		if (type != 0) {
			return type;
		}

		return compareIdentifiers(left.identifiers(), right.identifiers());
	}

	private static int comparePreReleaseWith(PreReleaseSuffix suffix, Suffix other) {

		if (other instanceof Snapshot) {
			return 1;
		}

		if (other instanceof Release) {
			return suffix.isServiceRelease() ? 1 : -1;
		}

		if (other instanceof PreReleaseSuffix otherSuffix) {
			return comparePreRelease(suffix, otherSuffix);
		}

		return Generic.COMPARATOR.compare(suffix, other);
	}

	private static int stabilityRank(String type) {
		return STABILITY_RANKS.getOrDefault(type, Integer.MAX_VALUE);
	}

	private static int compareType(String left, String right) {

		int leftRank = typeOrder(left);
		int rightRank = typeOrder(right);
		if (leftRank != -1 || rightRank != -1) {
			return Integer.compare(leftRank, rightRank);
		}
		return left.compareTo(right);
	}

	private static int typeOrder(String type) {
		return TYPE_ORDER.getOrDefault(type, -1);
	}

	private static int compareIdentifiers(List<Identifier> left, List<Identifier> right) {

		int count = Math.min(left.size(), right.size());
		for (int i = 0; i < count; i++) {
			int comparison = left.get(i).compareTo(right.get(i));
			if (comparison != 0) {
				return comparison;
			}
		}
		return Integer.compare(left.size(), right.size());
	}

	private static boolean isNumeric(String value) {

		if (StringUtils.isEmpty(value)) {
			return false;
		}

		for (int i = 0; i < value.length(); i++) {
			if (!Character.isDigit(value.charAt(i))) {
				return false;
			}
		}
		return true;
	}

	private static String canonicalIdentifiers(List<Identifier> identifiers) {

		StringBuilder builder = new StringBuilder();
		for (Identifier identifier : identifiers) {
			if (builder.length() > 0) {
				builder.append('.');
			}
			builder.append(identifier.canonical());
		}
		return builder.toString();
	}

	interface PreReleaseSuffix extends Suffix {

		String getCanonicalType();

		List<Identifier> identifiers();

		default boolean isServiceRelease() {
			return getCanonicalType().equals("sr");
		}

		@Override
		default boolean isMilestone() {
			return MILESTONE_TYPES.contains(getCanonicalType());
		}

		@Override
		default boolean isReleaseCandidate() {
			return RELEASE_CANDIDATE_TYPES.contains(getCanonicalType());
		}

	}

	record Identifier(String raw) implements Comparable<Identifier> {

		private boolean isNumeric() {
			return Suffix.isNumeric(raw);
		}

		@Override
		public int compareTo(Identifier other) {

			boolean leftNumeric = isNumeric();
			boolean rightNumeric = other.isNumeric();
			if (leftNumeric && rightNumeric) {
				return asNumber().compareTo(other.asNumber());
			}
			if (leftNumeric != rightNumeric) {
				return leftNumeric ? -1 : 1;
			}
			return raw.compareTo(other.raw);
		}

		String canonical() {
			return isNumeric() ? asNumber().toString() : raw;
		}

		private BigInteger asNumber() {
			return new BigInteger(raw);
		}

	}

	/**
	 * Snapshot suffix such as {@code SNAPSHOT} or {@code BUILD-SNAPSHOT}.
	 *
	 * @param canonical the canonical suffix text.
	 */
	record Snapshot(String canonical) implements Suffix {

		private static final Snapshot INSTANCE = new Snapshot("SNAPSHOT");

		private static final Snapshot BUILD_SNAPSHOT = new Snapshot("BUILD-SNAPSHOT");

		@Override
		public int compareTo(Suffix other) {
			return other instanceof Snapshot ? 0 : -1;
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Release suffix (or no suffix at all).
	 *
	 * @param canonical the canonical suffix text.
	 */
	record Release(String canonical) implements Suffix {

		/**
		 * Shared empty release suffix.
		 */
		public static final Release INSTANCE = new Release("");

		/**
		 * Shared {@code RELEASE} suffix.
		 */
		public static final Release RELEASE = new Release("RELEASE");

		@Override
		public int compareTo(Suffix other) {

			if (isServiceRelease(other)) {
				return -1;
			}

			if (isPreRelease(other) || other instanceof Snapshot) {
				return 1;
			}

			return other instanceof Release ? 0 : -1;
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Generic suffix that doesn't fit into any of the other categories. Will be
	 * sorted alphabetically by canonical value.
	 *
	 * @param canonical the canonical suffix text.
	 */
	record Generic(String canonical) implements Suffix {

		private static final Comparator<Suffix> COMPARATOR = Comparator.comparing(Suffix::canonical,
				String.CASE_INSENSITIVE_ORDER);

		@Override
		public int compareTo(Suffix other) {

			if (other instanceof Release) {
				return -1;
			}

			if (other instanceof Snapshot) {
				return 1;
			}

			if (other instanceof PreReleaseSuffix preRelease) {
				return COMPARATOR.compare(this, preRelease);
			}

			return COMPARATOR.compare(this, other);
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Semantic versioning suffix such as {@code M1}, {@code RC1} or {@code SR1}.
	 *
	 * @param type the qualifier type.
	 * @param counter the qualifier counter.
	 * @param separator the qualifier separator.
	 * @param raw the original counter value.
	 */
	record SemVerSuffix(String type, int counter, @Nullable String separator, @Nullable String raw)
			implements PreReleaseSuffix {

		public String getCanonicalType() {
			return type.toLowerCase(Locale.ROOT);
		}

		@Override
		public List<Identifier> identifiers() {
			return counter == -1 ? List.of() : List.of(new Identifier(raw != null ? raw : Integer.toString(counter)));
		}

		@Override
		public int compareTo(Suffix other) {
			return comparePreReleaseWith(this, other);
		}

		/**
		 * Normalized suffix without leading-zero padding.
		 */
		@Override
		public String canonical() {

			if (counter == -1) {
				return type;
			}

			if (separator == null) {
				return "%s%d".formatted(type, counter);
			}
			return "%s%s%d".formatted(type, separator, counter);
		}

		@Override
		public String toString() {

			if (counter == -1) {
				return type;
			}

			if (separator == null) {
				return type + raw;
			}
			return type + separator + raw;
		}

	}

	/**
	 * Dot-separated semantic versioning suffix such as {@code alpha.0.3} or
	 * {@code rc.1.2}.
	 *
	 * @param type the qualifier type.
	 * @param identifiers the remaining qualifier identifiers.
	 * @param raw the original suffix text.
	 */
	record MultiSegmentSuffix(String type, List<Identifier> identifiers, String raw) implements PreReleaseSuffix {

		static @Nullable MultiSegmentSuffix from(String suffix) {

			String normalized = suffix.replace('-', '.');
			String[] segments = normalized.split("\\.");
			if (segments.length < 2 || isNumeric(segments[0])) {
				return null;
			}

			List<Identifier> identifiers = Arrays.stream(segments).skip(1).map(Identifier::new).toList();
			return new MultiSegmentSuffix(segments[0], identifiers, suffix);
		}

		@Override
		public String getCanonicalType() {
			return type.toLowerCase(Locale.ROOT);
		}

		@Override
		public int compareTo(Suffix other) {
			return comparePreReleaseWith(this, other);
		}

		@Override
		public String canonical() {
			return type + "." + canonicalIdentifiers(identifiers);
		}

		@Override
		public String toString() {
			return raw;
		}

	}

	/**
	 * Numeric-only semantic versioning pre-release suffix.
	 *
	 * @param identifiers the numeric identifiers.
	 * @param raw the original suffix text.
	 */
	record NumericPreReleaseSuffix(List<Identifier> identifiers, String raw) implements PreReleaseSuffix {

		static NumericPreReleaseSuffix from(String suffix) {
			return new NumericPreReleaseSuffix(Arrays.stream(suffix.split("\\.")).map(Identifier::new).toList(),
					suffix);
		}

		@Override
		public String getCanonicalType() {
			return "";
		}

		@Override
		public int compareTo(Suffix other) {
			return comparePreReleaseWith(this, other);
		}

		@Override
		public String canonical() {
			return canonicalIdentifiers(identifiers);
		}

		@Override
		public String toString() {
			return raw;
		}

	}

}
