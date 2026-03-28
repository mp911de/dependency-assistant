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
package biz.paluch.mavenupdater.artifact;

import biz.paluch.mavenupdater.artifact.Suffix.Generic;
import biz.paluch.mavenupdater.artifact.Suffix.Release;
import biz.paluch.mavenupdater.artifact.Suffix.SemVerSuffix;

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.util.Assert;

/**
 * Value object to represent version of a particular artifact.
 *
 * @author Mark Paluch
 */
class SemanticArtifactVersion implements ArtifactVersion {

	private static final Pattern PATTERN = Pattern
			.compile("((\\d+)(\\.\\d+)+)(\\.((SR\\d+)|(RC\\d+)|(M\\d+)|(BUILD-SNAPSHOT)|(RELEASE)))");

	private static final Pattern VERSION_FALLBACK = Pattern.compile("((\\d+)(\\.\\d+)+)([.-])?(.*)");

	private static final Pattern MODIFIER_PATTERN = Pattern
			.compile("((\\d+)(\\.\\d+)+)(-((RC\\d+)|(M\\d+)|(SNAPSHOT)))?");

	private static final Pattern SEMVER_PATTERN = Pattern
			.compile("((\\d+)(\\.\\d+)+)(([.-])" + Suffix.SEMVER_QUALIFIER_PATTERN + ")?");

	private final NumericVersionComponents version;
	private final boolean modifierFormat;
	private final boolean skipSeparator;
	private final Suffix suffix;

	/**
	 * Creates a new {@link SemanticArtifactVersion} from the given logical {@link NumericVersionComponents}.
	 *
	 * @param version must not be {@literal null}.
	 * @param modifierFormat
	 */
	SemanticArtifactVersion(NumericVersionComponents version, boolean modifierFormat) {
		this(version, modifierFormat, modifierFormat ? Release.INSTANCE : Release.RELEASE);
	}

	/**
	 * Creates a new {@link SemanticArtifactVersion} from the given logical {@link NumericVersionComponents}.
	 *
	 * @param version must not be {@literal null}.
	 * @param modifierFormat
	 */
	SemanticArtifactVersion(NumericVersionComponents version, boolean modifierFormat, Suffix suffix) {

		Assert.notNull(version, "Version must not be null!");
		Assert.notNull(suffix, "Suffix must not be null!");

		this.version = version;
		this.modifierFormat = modifierFormat;
		this.skipSeparator = false;
		this.suffix = suffix;
	}

	/**
	 * Creates a new {@link SemanticArtifactVersion} from the given logical {@link NumericVersionComponents}.
	 *
	 * @param version must not be {@literal null}.
	 * @param modifierFormat
	 */
	private SemanticArtifactVersion(NumericVersionComponents version, boolean modifierFormat, boolean skipSeparator,
			Suffix suffix) {

		Assert.notNull(version, "Version must not be null!");
		Assert.notNull(suffix, "Suffix must not be null!");

		this.version = version;
		this.modifierFormat = modifierFormat;
		this.skipSeparator = skipSeparator;
		this.suffix = suffix;
	}

	/**
	 * Parses the given {@link String} into an {@link SemanticArtifactVersion}.
	 *
	 * @param source must not be {@literal null} or empty.
	 * @return
	 */
	public static SemanticArtifactVersion of(String source) {

		Assert.hasText(source, "Version source must not be null or empty!");

		Matcher matcher = PATTERN.matcher(source);
		if (matcher.matches()) {

			int suffixStart = source.lastIndexOf('.');

			NumericVersionComponents version = NumericVersionComponents.parse(source.substring(0, suffixStart));
			String suffix = source.substring(suffixStart + 1);

			Assert.isTrue(suffix.matches(Suffix.VALID_SUFFIX), String.format("Invalid version suffix: %s!", source));

			return new SemanticArtifactVersion(version, false, Suffix.parse(suffix));
		}

		matcher = MODIFIER_PATTERN.matcher(source);

		if (matcher.matches()) {

			NumericVersionComponents version = NumericVersionComponents.parse(matcher.group(1));
			String suffix = matcher.group(5);

			return new SemanticArtifactVersion(version, true, Suffix.parse(suffix));
		}

		matcher = SEMVER_PATTERN.matcher(source);

		if (matcher.matches()) {

			NumericVersionComponents version = NumericVersionComponents.parse(matcher.group(1));
			String modifierdelimiter = matcher.group(5);
			String suffix = matcher.group(6);

			return new SemanticArtifactVersion(version, modifierdelimiter.equals("-"), Suffix.parse(suffix));
		}

		matcher = VERSION_FALLBACK.matcher(source);

		if (matcher.matches()) {

			NumericVersionComponents version = NumericVersionComponents.parse(matcher.group(1));
			String modifierdelimiter = matcher.group(4);
			String suffix = matcher.group(5);

			return new SemanticArtifactVersion(version, "-".equals(modifierdelimiter),
					!("-".equals(modifierdelimiter) || ".".equals(modifierdelimiter)), Suffix.parse(suffix));
		}

		throw new IllegalArgumentException(
				String.format("Version %s does not match <version>.<modifier> nor <version>-<modifier> pattern", source));
	}

	/**
	 * Returns whether the given source represents a valid version.
	 *
	 * @param source
	 * @return
	 */
	public static boolean isVersion(String source) {
		try {
			of(source);
			return true;
		} catch (IllegalArgumentException o_O) {
			return false;
		}
	}

	public boolean isVersionWithin(NumericVersionComponents version) {
		return this.version.toMajorMinorBugfix().startsWith(version.toString());
	}

	@Override
	public boolean isNewer(ArtifactVersion other) {
		return other instanceof SemanticArtifactVersion sav && this.compareTo(sav) > 0;
	}

	@Override
	public boolean isNewerMinor(ArtifactVersion other) {

		if (other instanceof SemanticArtifactVersion sav) {
			return version.getMajor() == sav.version.getMajor() && sav.version.getMinor() > version.getMinor()
					&& isNewer(sav);
		}

		return false;
	}

	@Override
	public boolean hasSameMajorMinor(ArtifactVersion other) {
		return other instanceof SemanticArtifactVersion sav && version.hasSameMajorMinor(sav.version);
	}

	@Override
	public boolean hasSameMajor(ArtifactVersion other) {
		return other instanceof SemanticArtifactVersion sav && version.getMajor() == sav.version.getMajor();
	}

	public NumericVersionComponents getVersion() {
		return version;
	}

	/**
	 * Returns the release version for the current one.
	 *
	 * @return
	 */
	public ArtifactVersion getReleaseVersion() {
		return new SemanticArtifactVersion(version, modifierFormat);
	}

	/**
	 * Returns the snapshot version of the current one.
	 *
	 * @return
	 */
	public ArtifactVersion getSnapshotVersion() {
		return snapshotOf(version);
	}

	/**
	 * Returns whether the version is a release version.
	 *
	 * @return
	 */
	@Override
	public boolean isReleaseVersion() {
		return suffix instanceof Release;
	}

	/**
	 * Returns whether the version is a milestone version.
	 *
	 * @return
	 */
	@Override
	public boolean isPreview() {
		return isMilestoneVersion() || isReleaseCandidateVersion();
	}

	/**
	 * Returns whether the version is a milestone version.
	 *
	 * @return
	 */
	@Override
	public boolean isMilestoneVersion() {

		if (suffix instanceof SemVerSuffix sv && sv.isMilestone()) {
			return true;
		}

		String canonical = suffix.canonical().toLowerCase();
		return canonical.contains("alpha") || canonical.contains("beta");
	}

	/**
	 * Returns whether the version is a RC version.
	 *
	 * @return
	 */
	@Override
	public boolean isReleaseCandidateVersion() {

		if (suffix instanceof SemVerSuffix sv && sv.isReleaseCandidate()) {
			return true;
		}

		return suffix.canonical().toLowerCase().contains("rc");
	}

	public String getSuffix() {
		return suffix.canonical();
	}

	public int getLevel() {

		if (suffix instanceof SemVerSuffix sv) {
			return sv.counter();
		}

		if (isBugFixVersion()) {
			return version.getBugfix();
		}

		throw new IllegalStateException("Not a M/RC/SR release");

	}

	@Override
	public boolean isSnapshotVersion() {
		return suffix instanceof Suffix.Snapshot;
	}

	public boolean isBugFixVersion() {
		return isReleaseVersion() && version.getBugfix() != 0;
	}

	/**
	 * Returns the next development version to be used for the current release version, which means next minor for GA
	 * versions and next bug fix for service releases. Will return the current version as snapshot otherwise.
	 *
	 * @return
	 */
	public ArtifactVersion getNextDevelopmentVersion() {

		if (isReleaseVersion() || isBugFixVersion()) {

			boolean isGaVersion = version.withBugfix(0).equals(version);
			NumericVersionComponents nextVersion = isGaVersion ? version.nextMinor() : version.nextBugfix();

			return snapshotOf(nextVersion);
		}

		return isSnapshotVersion() ? this : snapshotOf(version);
	}

	/**
	 * Returns the next bug fix version for the current version if it's a release version or the snapshot version of the
	 * current one otherwise.
	 *
	 * @return
	 */
	public ArtifactVersion getNextBugfixVersion() {

		if (isReleaseVersion()) {
			return snapshotOf(version.nextBugfix());
		}

		return isSnapshotVersion() ? this : snapshotOf(version);
	}

	/**
	 * @return the next minor version retaining the modifier and snapshot suffix.
	 */
	public ArtifactVersion getNextMinorVersion() {
		return versionOf(version.nextMinor());
	}

	public String getReleaseTrainSuffix() {

		if (isSnapshotVersion() || isMilestoneVersion() || isReleaseCandidateVersion()) {
			return suffix.toString();
		}

		if (isBugFixVersion()) {
			return "SR" + version.getBugfix();
		}

		return "GA";
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Comparable#compareTo(java.lang.Object)
	 */
	@Override
	public int compareTo(ArtifactVersion that) {
		return that instanceof SemanticArtifactVersion sav ? compareTo(sav) : 1;
	}

	public int compareTo(SemanticArtifactVersion that) {

		int versionsEqual = this.version.compareTo(that.version);
		return versionsEqual != 0 ? versionsEqual : this.suffix.compareTo(that.suffix);
	}

	/*
	 * (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {

		if (skipSeparator) {
			return String.format("%s%s", version.toString(), suffix);
		}

		if (modifierFormat) {

			if (isSnapshotVersion() || isMilestoneVersion() || isReleaseCandidateVersion() || suffix instanceof Generic) {
				return String.format("%s-%s", version.toString(), suffix);
			}

			return version.toString();
		}

		return String.format("%s.%s", version.toString(), suffix);
	}

	private String getSnapshotSuffix() {
		return modifierFormat ? Suffix.SNAPSHOT_MODIFIER : Suffix.BUILD_SNAPSHOT_SUFFIX;
	}

	private ArtifactVersion snapshotOf(NumericVersionComponents version) {
		return new SemanticArtifactVersion(version, modifierFormat, Suffix.parse(getSnapshotSuffix()));
	}

	private ArtifactVersion versionOf(NumericVersionComponents version) {
		return new SemanticArtifactVersion(version, modifierFormat, suffix);
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof SemanticArtifactVersion other)) {
			return false;
		}
		return version.compareTo(other.version) == 0 && suffix.compareTo(other.suffix) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(version, suffix.canonical());
	}

}
