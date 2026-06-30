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
 *
 * @author Mark Paluch
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

	String VALID_SUFFIX = "%s|%s|%s|%s|-%s|-%s|-%s".formatted(RELEASE_SUFFIX, MILESTONE_SUFFIX, RC_SUFFIX,
			Suffix.BUILD_SNAPSHOT_SUFFIX, RELEASE_SUFFIX, MILESTONE_SUFFIX, SNAPSHOT_MODIFIER);


	static Map<String, Integer> mapOf(Consumer<Map<String, Integer>> consumer) {
		Map<String, Integer> map = new LinkedHashMap<>();
		consumer.accept(map);

		return Map.copyOf(map);
	}

	/**
	 * Parse the suffix into a {@link Suffix} instance.
	 *
	 * @param suffix the raw suffix text to parse, or {@literal null} for a release
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
		return new SemVerSuffix(type, new BigInteger(digits), "", digits);
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
			return new SemVerSuffix(type, null, separator, "");
		}
		return new SemVerSuffix(type, new BigInteger(counter), separator, counter);
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

	@Override
	default int compareTo(Suffix other) {
		return getOrderKey().compareTo(other.getOrderKey());
	}

	OrderKey getOrderKey();

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

	private static String canonicalIdentifiers(List<Identifier> identifiers) {

		StringBuilder builder = new StringBuilder();
		for (Identifier identifier : identifiers) {
			if (!builder.isEmpty()) {
				builder.append('.');
			}
			builder.append(identifier.canonical());
		}
		return builder.toString();
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

	class OrderKey implements Comparable<OrderKey> {

		static final int SNAPSHOT_ORDER = 0;

		static final int KNOWN_PRE_RELEASE_OFFSET = 1;

		static final int GENERIC_ORDER = KNOWN_PRE_RELEASE_OFFSET + TYPE_ORDER.get("cr") + 1;

		static final int RELEASE_ORDER = GENERIC_ORDER + 1;

		static final int SERVICE_RELEASE_ORDER = RELEASE_ORDER + 1;

		private final int order;

		private final List<Identifier> identifiers;

		private final @Nullable String genericText;

		private OrderKey(int order, List<Identifier> identifiers, @Nullable String genericText) {
			this.order = order;
			this.identifiers = identifiers;
			this.genericText = genericText;
		}

		public static OrderKey from(String type, List<Identifier> identifiers, String canonical) {

			if (type.equalsIgnoreCase("sr")) {
				return new OrderKey(OrderKey.SERVICE_RELEASE_ORDER, identifiers, null);
			}

			int typeOrder = OrderKey.typeOrder(type);
			if (typeOrder == -1) {
				return new OrderKey(OrderKey.GENERIC_ORDER, List.of(), canonical);
			}

			return new OrderKey(OrderKey.KNOWN_PRE_RELEASE_OFFSET + typeOrder, identifiers, null);
		}

		@Override
		public int compareTo(OrderKey other) {

			int comparison = Integer.compare(order, other.order);
			if (comparison != 0) {
				return comparison;
			}

			if (genericText != null && other.genericText != null) {
				comparison = String.CASE_INSENSITIVE_ORDER.compare(genericText, other.genericText);
				return comparison != 0 ? comparison : genericText.compareTo(other.genericText);
			}

			return compareIdentifiers(identifiers, other.identifiers);
		}

		private static int typeOrder(String type) {
			return TYPE_ORDER.getOrDefault(type, -1);
		}

	}

	interface PreReleaseSuffix extends Suffix {

		String getCanonicalType();

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
	 */
	class Snapshot implements Suffix {

		private static final Snapshot INSTANCE = new Snapshot("SNAPSHOT");

		private static final Snapshot BUILD_SNAPSHOT = new Snapshot("BUILD-SNAPSHOT");

		private final String canonical;

		private final OrderKey orderKey;

		private Snapshot(String canonical) {
			this.canonical = canonical;
			this.orderKey = new OrderKey(OrderKey.SNAPSHOT_ORDER, List.of(), null);
		}

		@Override
		public String canonical() {
			return canonical;
		}

		@Override
		public OrderKey getOrderKey() {
			return orderKey;
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Release suffix (or no suffix at all).
	 */
	class Release implements Suffix {

		/**
		 * Shared empty release suffix.
		 */
		public static final Release INSTANCE = new Release("");

		/**
		 * Shared {@code RELEASE} suffix.
		 */
		public static final Release RELEASE = new Release("RELEASE");

		private final String canonical;

		private final OrderKey orderKey;

		private Release(String canonical) {
			this.canonical = canonical;
			this.orderKey = new OrderKey(OrderKey.RELEASE_ORDER, List.of(), null);
		}

		@Override
		public String canonical() {
			return canonical;
		}

		@Override
		public OrderKey getOrderKey() {
			return orderKey;
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Generic suffix that doesn't fit into any of the other categories.
	 */
	class Generic implements Suffix {

		private final String canonical;

		private final OrderKey orderKey;

		private Generic(String canonical) {
			this.canonical = canonical;
			this.orderKey = new OrderKey(OrderKey.GENERIC_ORDER, List.of(), canonical);
		}

		@Override
		public String canonical() {
			return canonical;
		}

		@Override
		public OrderKey getOrderKey() {
			return orderKey;
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Semantic versioning suffix such as {@code M1}, {@code RC1} or {@code SR1}.
	 */
	class SemVerSuffix implements PreReleaseSuffix {

		private final String type;

		private final @Nullable BigInteger counter;

		private final @Nullable String separator;

		private final @Nullable String raw;

		private final OrderKey orderKey;

		private SemVerSuffix(String type, @Nullable BigInteger counter, @Nullable String separator,
				@Nullable String raw) {

			this.type = type;
			this.counter = counter;
			this.separator = separator;
			this.raw = raw;
			List<Identifier> identifiers = counter == null ? List.of()
					: List.of(new Identifier(raw != null ? raw : counter.toString()));
			this.orderKey = OrderKey.from(getCanonicalType(), identifiers, canonical());
		}

		@Override
		public String getCanonicalType() {
			return type.toLowerCase(Locale.ROOT);
		}

		String type() {
			return type;
		}

		@Override
		public OrderKey getOrderKey() {
			return orderKey;
		}

		@Override
		public String canonical() {

			if (counter == null) {
				return type;
			}

			if (separator == null) {
				return "%s%s".formatted(type, counter);
			}
			return "%s%s%s".formatted(type, separator, counter);
		}

		@Override
		public String toString() {

			if (counter == null) {
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
	 */
	class MultiSegmentSuffix implements PreReleaseSuffix {

		private final String type;

		private final String canonical;

		private final String raw;

		private final OrderKey orderKey;

		private MultiSegmentSuffix(String type, List<Identifier> identifiers, String raw) {
			this.type = type;
			this.raw = raw;
			this.canonical = canonicalIdentifiers(identifiers);
			this.orderKey = OrderKey.from(getCanonicalType(), identifiers, canonical());
		}

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
		public OrderKey getOrderKey() {
			return orderKey;
		}

		@Override
		public String canonical() {
			return type + "." + canonical;
		}

		@Override
		public String toString() {
			return raw;
		}

	}

	/**
	 * Numeric-only semantic versioning pre-release suffix.
	 */
	class NumericPreReleaseSuffix implements PreReleaseSuffix {

		private final String canonical;

		private final String raw;

		private final OrderKey orderKey;

		private NumericPreReleaseSuffix(List<Identifier> identifiers, String raw) {
			this.canonical = Suffix.canonicalIdentifiers(identifiers);
			this.raw = raw;
			this.orderKey = OrderKey.from(getCanonicalType(), identifiers, canonical());
		}

		static NumericPreReleaseSuffix from(String suffix) {
			return new NumericPreReleaseSuffix(Arrays.stream(suffix.split("\\.")).map(Identifier::new).toList(),
					suffix);
		}

		@Override
		public String getCanonicalType() {
			return "";
		}

		@Override
		public OrderKey getOrderKey() {
			return orderKey;
		}

		@Override
		public String canonical() {
			return canonical;
		}

		@Override
		public String toString() {
			return raw;
		}

	}

}
