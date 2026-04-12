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

import java.util.Comparator;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.util.StringUtils;

/**
 * Version suffix such as {@code SNAPSHOT}, {@code M1}, {@code RC1} or {@code RELEASE}.
 */
interface Suffix extends Comparable<Suffix> {

	String BUILD_SNAPSHOT_SUFFIX = "BUILD-SNAPSHOT";
	String SNAPSHOT_MODIFIER = "SNAPSHOT";
	String RELEASE_SUFFIX = "RELEASE";
	String MILESTONE_SUFFIX = "M\\d+";
	String RC_SUFFIX = "RC\\d+";

	Pattern MILESTONE_OR_RC_PATTERN = Pattern.compile("(SR|RC|M)(\\d+)");

	Pattern SEMVER_SUFFIX = Pattern.compile("([a-zA-Z]+)([.-])?(\\d*)");

	/**
	 * One capturing group for the full semver-style qualifier (e.g. {@code B02}, {@code RC1}, {@code alpha1}). Use in
	 * compound patterns so the qualifier is not split across regex groups.
	 */
	String SEMVER_QUALIFIER_PATTERN = "([a-zA-Z]+(?:[.-])?(?:\\d*))";

	String VALID_SUFFIX = String.format("%s|%s|%s|%s|-%s|-%s|-%s", RELEASE_SUFFIX, MILESTONE_SUFFIX, RC_SUFFIX,
			Suffix.BUILD_SNAPSHOT_SUFFIX, RELEASE_SUFFIX, MILESTONE_SUFFIX, SNAPSHOT_MODIFIER);

	/**
	 * Parse the suffix into a {@link Suffix} instance.
	 *
	 * @param suffix
	 * @return
	 */
	static Suffix parse(String suffix) {

		if (StringUtils.isEmpty(suffix)) {
			return Release.INSTANCE;
		}

		suffix = suffix.strip();
		if (StringUtils.isEmpty(suffix)) {
			return Release.INSTANCE;
		}

		if (suffix.equals("RELEASE")) {
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

		Matcher milestoneMatcher = MILESTONE_OR_RC_PATTERN.matcher(suffix);

		if (milestoneMatcher.find()) {
			String type = milestoneMatcher.group(1);
			String digits = milestoneMatcher.group(2);
			int counter = Integer.parseInt(digits);
			return new SemVerSuffix(type, counter, "", digits);
		}

		Matcher semVerPattern = SEMVER_SUFFIX.matcher(suffix);

		if (semVerPattern.matches()) {
			String type = semVerPattern.group(1);
			String separator = semVerPattern.group(2);
			String counterString = semVerPattern.group(3);
			if (type.equalsIgnoreCase(SNAPSHOT_MODIFIER) && StringUtils.isEmpty(counterString) && separator == null) {
				return Snapshot.INSTANCE;
			}
			if (StringUtils.isEmpty(counterString)) {
				return new SemVerSuffix(type, -1, separator, "");
			}
			return new SemVerSuffix(type, Integer.parseInt(counterString), separator, counterString);
		}

		return new Generic(suffix);
	}

	/**
	 * Canonical suffix representation.
	 */
	String canonical();

	/**
	 * Snapshot suffix such as {@code SNAPSHOT} or {@code BUILD-SNAPSHOT}.
	 *
	 * @param canonical
	 */
	record Snapshot(String canonical) implements Suffix {

		private final static Snapshot INSTANCE = new Snapshot("SNAPSHOT");

		private final static Snapshot BUILD_SNAPSHOT = new Snapshot("BUILD-SNAPSHOT");

		@Override
		public int compareTo(Suffix o) {
			return o instanceof Snapshot ? 0 : -1;
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Release suffix (or no suffix at all).
	 *
	 * @param canonical
	 */
	record Release(String canonical) implements Suffix {

		public final static Release INSTANCE = new Release("");

		public final static Release RELEASE = new Release("RELEASE");

		@Override
		public int compareTo(Suffix o) {

			if (o instanceof SemVerSuffix sv) {
				if (sv.type.equals("SR")) {
					return -1;
				}
				return 1;
			}

			return o instanceof Release ? 0 : -1;
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Generic suffix that doesn't fit into any of the other categories. Will be sorted alphabetically by canonical value.
	 *
	 * @param canonical
	 */
	record Generic(String canonical) implements Suffix {

		private static final Comparator<Suffix> COMPARATOR = Comparator.comparing(Suffix::canonical,
				String.CASE_INSENSITIVE_ORDER);

		@Override
		public int compareTo(Suffix o) {

			if (o instanceof Release) {
				return -1;
			}

			if (o instanceof Snapshot) {
				return 1;
			}

			return COMPARATOR.compare(this, o);
		}

		@Override
		public String toString() {
			return canonical();
		}

	}

	/**
	 * Semantic versioning suffix such as {@code M1}, {@code RC1} or {@code SR1}.
	 *
	 * @param type
	 * @param counter
	 * @param raw original counter value.
	 */
	record SemVerSuffix(String type, int counter, String separator, String raw) implements Suffix {

		SemVerSuffix(String type, int counter, String separator) {
			this(type, counter, separator, null);
		}

		private static final Comparator<SemVerSuffix> COMPARATOR = Comparator.comparing(SemVerSuffix::getCanonicalType)
				.thenComparingInt(SemVerSuffix::counter);

		private String getCanonicalType() {
			return type.toLowerCase(Locale.ROOT);
		}

		@Override
		public int compareTo(Suffix o) {

			if (o instanceof Snapshot) {
				return 1;
			}

			if (o instanceof Release) {
				return type.equals("SR") ? 1 : -1;
			}

			if (o instanceof SemVerSuffix other) {
				return COMPARATOR.compare(this, other);
			}

			return Generic.COMPARATOR.compare(this, o);
		}

		public boolean isMilestone() {
			String t = getCanonicalType();
			return t.equals("m") || t.equals("alpha") || t.equals("beta") || t.equals("b");
		}

		public boolean isReleaseCandidate() {
			return getCanonicalType().equals("rc") || getCanonicalType().equals("cr");
		}

		/**
		 * Normalized suffix (no leading-zero padding) for comparison and {@link SemanticArtifactVersion#hashCode()}.
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
}
