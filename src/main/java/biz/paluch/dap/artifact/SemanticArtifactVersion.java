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

import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.Suffix.Release;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Value object to represent version of a particular artifact.
 *
 * @author Mark Paluch
 */
class SemanticArtifactVersion implements ArtifactVersion {

	private static final Pattern DOT_SUFFIX_PATTERN = Pattern
			.compile("((\\d+)(\\.\\d+)*)(\\.((SR\\d+)|(RC\\d+)|(M\\d+)|(BUILD-SNAPSHOT)|(RELEASE)))");

	private static final Pattern MODIFIER_PATTERN = Pattern
			.compile("((\\d+)(\\.\\d+)*)(-((RC\\d+)|(M\\d+)|(SNAPSHOT)))?");

	private static final Pattern SEMVER_PATTERN = Pattern
			.compile("((\\d+)(\\.\\d+)*)(([.-])" + Suffix.SEMVER_QUALIFIER_PATTERN + ")?");

	private static final Pattern VERSION_FALLBACK_PATTERN = Pattern.compile("((\\d+)(\\.\\d+)*)([.-])?(.*)");

	private final String version;

	private final NumericVersionComponents components;

	private final boolean modifierFormat;

	private final Suffix suffix;

	/**
	 * Creates a new {@link SemanticArtifactVersion} from the given logical {@link NumericVersionComponents}.
	 *
	 * @param components must not be {@literal null}.
	 */
	public SemanticArtifactVersion(NumericVersionComponents components) {
		this(components, false);
	}

	/**
	 * Creates a new {@link SemanticArtifactVersion} from the given logical
	 * {@link NumericVersionComponents}.
	 *
	 * @param components must not be {@literal null}.
	 * @param modifierFormat whether the suffix is rendered with modifier notation.
	 */
	public SemanticArtifactVersion(NumericVersionComponents components, boolean modifierFormat) {
		this(components.toString(), components, modifierFormat, modifierFormat ? Release.INSTANCE : Release.RELEASE);
	}

	/**
	 * Creates a new {@link SemanticArtifactVersion} from the given logical
	 * {@link NumericVersionComponents}.
	 *
	 * @param rawVersion must not be {@literal null}.
	 * @param modifierFormat whether the suffix is rendered with modifier notation.
	 */
	public SemanticArtifactVersion(String rawVersion, NumericVersionComponents components, boolean modifierFormat,
			Suffix suffix) {
		this.version = rawVersion;
		this.components = components;
		this.modifierFormat = modifierFormat;
		this.suffix = suffix;
	}

	/**
	 * Parses the given {@link String} into an {@link SemanticArtifactVersion}.
	 *
	 * @param source must not be {@literal null} or empty.
	 * @return the parsed semantic artifact version.
	 */
	public static SemanticArtifactVersion of(String source) {

		Assert.hasText(source, "Version source must not be null or empty!");

		String normalized = normalizeSource(source);
		SemanticArtifactVersion version = parseDotSuffix(source, normalized);
		if (version != null) {
			return version;
		}

		version = parseModifier(source, normalized);
		if (version != null) {
			return version;
		}

		version = parseSemVer(source, normalized);
		if (version != null) {
			return version;
		}

		version = parseFallback(source, normalized);
		if (version != null) {
			return version;
		}

		throw new IllegalArgumentException(
				"Version %s does not match <version>.<modifier> nor <version>-<modifier> pattern".formatted(source));
	}

	private static @Nullable SemanticArtifactVersion parseDotSuffix(String source, String normalized) {

		Matcher matcher = DOT_SUFFIX_PATTERN.matcher(normalized);
		if (!matcher.matches()) {
			return null;
		}

		int suffixStart = normalized.lastIndexOf('.');
		NumericVersionComponents components = NumericVersionComponents.parse(normalized.substring(0, suffixStart));
		String suffix = normalized.substring(suffixStart + 1);
		if (!suffix.matches(Suffix.VALID_SUFFIX)) {
			throw new IllegalArgumentException("Invalid version suffix: %s!".formatted(source));
		}

		return new SemanticArtifactVersion(source, components, false, Suffix.parse(suffix));
	}

	private static @Nullable SemanticArtifactVersion parseModifier(String source, String normalized) {

		Matcher matcher = MODIFIER_PATTERN.matcher(normalized);
		if (!matcher.matches()) {
			return null;
		}

		NumericVersionComponents components = NumericVersionComponents.parse(matcher.group(1));
		return new SemanticArtifactVersion(source, components, true, Suffix.parse(matcher.group(5)));
	}

	private static @Nullable SemanticArtifactVersion parseSemVer(String source, String normalized) {

		Matcher matcher = SEMVER_PATTERN.matcher(normalized);
		if (!matcher.matches()) {
			return null;
		}

		NumericVersionComponents components = NumericVersionComponents.parse(matcher.group(1));
		String modifierDelimiter = matcher.group(5);
		return new SemanticArtifactVersion(source, components, "-".equals(modifierDelimiter),
				Suffix.parse(matcher.group(6)));
	}

	private static @Nullable SemanticArtifactVersion parseFallback(String source, String normalized) {

		Matcher matcher = VERSION_FALLBACK_PATTERN.matcher(normalized);
		if (!matcher.matches()) {
			return null;
		}

		NumericVersionComponents components = NumericVersionComponents.parse(matcher.group(1));
		String modifierDelimiter = matcher.group(4);
		return new SemanticArtifactVersion(source, components, "-".equals(modifierDelimiter),
				Suffix.parse(matcher.group(5)));
	}

	private static String normalizeSource(String source) {

		String normalized = source.startsWith("v") || source.startsWith("V") ? source.substring(1) : source;
		int metadataIndex = normalized.indexOf('+');
		if (metadataIndex != -1) {
			normalized = normalized.substring(0, metadataIndex);
		}
		return normalized;
	}

	/**
	 * Return whether the given source represents a valid semantic version.
	 * @param source the source to inspect.
	 */
	public static boolean isVersion(String source) {
		try {
			of(source);
			return true;
		} catch (IllegalArgumentException ex) {
			return false;
		}
	}

	/**
	 * Return whether this version starts with the given numeric components.
	 */
	public boolean isVersionWithin(NumericVersionComponents version) {
		return this.components.toMajorMinorBugfix().startsWith(version.toString());
	}

	@Override
	public boolean isNewer(ArtifactVersion other) {
		return other.getVersion() instanceof SemanticArtifactVersion sav && this.compareTo(sav) > 0;
	}

	@Override
	public boolean isOlder(ArtifactVersion other) {
		return other.getVersion() instanceof SemanticArtifactVersion sav && this.compareTo(sav) < 0;
	}

	@Override
	public boolean isNewerMinor(ArtifactVersion other) {

		if (other.getVersion() instanceof SemanticArtifactVersion sav) {
			return components.getMajor() == sav.components.getMajor() && sav.components.getMinor() > components.getMinor()
					&& isNewer(sav);
		}

		return false;
	}

	@Override
	public boolean hasSameMajorMinor(ArtifactVersion other) {
		return other.getVersion() instanceof SemanticArtifactVersion sav
				&& components.hasSameMajorMinor(sav.components);
	}

	@Override
	public boolean hasSameMajor(ArtifactVersion other) {
		return other.getVersion() instanceof SemanticArtifactVersion sav
				&& components.getMajor() == sav.components.getMajor();
	}

	@Override
	public boolean hasSameBaseVersion(ArtifactVersion other) {
		return other.getVersion() instanceof SemanticArtifactVersion sav
				&& components.compareTo(sav.components) == 0;
	}

	/**
	 * Return whether the version is a release version.
	 *
	 * @return {@code true} if this version is a release version.
	 */
	@Override
	public boolean isReleaseVersion() {
		return suffix instanceof Release;
	}

	/**
	 * Return whether the version is a preview version.
	 *
	 * @return {@code true} if this version is a preview version.
	 */
	@Override
	public boolean isPreview() {
		return isMilestoneVersion() || isReleaseCandidateVersion();
	}

	/**
	 * Return whether the version is a milestone version.
	 *
	 * @return {@code true} if this version is a milestone version.
	 */
	@Override
	public boolean isMilestoneVersion() {

		if (suffix.isMilestone()) {
			return true;
		}

		String canonical = suffix.canonical().toLowerCase();
		return canonical.contains("alpha") || canonical.contains("beta");
	}

	/**
	 * Return whether the version is a RC version.
	 *
	 * @return {@code true} if this version is a release candidate version.
	 */
	@Override
	public boolean isReleaseCandidateVersion() {

		if (suffix.isReleaseCandidate()) {
			return true;
		}

		return suffix.canonical().toLowerCase().contains("rc");
	}

	/**
	 * Return the canonical suffix string.
	 */
	public String getSuffix() {
		return suffix.canonical();
	}

	@Override
	public boolean isSnapshotVersion() {
		return suffix instanceof Suffix.Snapshot;
	}

	/**
	 * Return whether this version is a bugfix release.
	 */
	public boolean isBugFixVersion() {
		return isReleaseVersion() && components.getBugfix() != 0;
	}

	/**
	 * Return the next development version to be used for the current release
	 * version, which means next minor for GA versions and next bug fix for service
	 * releases. Will return the current version as snapshot otherwise.
	 *
	 * @return the next development version.
	 */
	public ArtifactVersion getNextDevelopmentVersion() {

		if (isReleaseVersion() || isBugFixVersion()) {

			boolean isGaVersion = components.withBugfix(0).equals(components);
			NumericVersionComponents nextVersion = isGaVersion ? components.nextMinor() : components.nextBugfix();

			return snapshotOf(nextVersion);
		}

		return isSnapshotVersion() ? this : snapshotOf(components);
	}

	/**
	 * Return the next bug fix version for the current version if it's a release
	 * version or the snapshot version of the current one otherwise.
	 *
	 * @return the next bugfix version.
	 */
	public ArtifactVersion getNextBugfixVersion() {

		if (isReleaseVersion()) {
			return snapshotOf(components.nextBugfix());
		}

		return isSnapshotVersion() ? this : snapshotOf(components);
	}

	/**
	 * @return the next minor version retaining the modifier and snapshot suffix.
	 */
	public ArtifactVersion getNextMinorVersion() {
		return versionOf(components.nextMinor());
	}

	@Override
	public int compareTo(ArtifactVersion that) {
		if (that.isWrapped()) {
			return compareTo(that.getVersion());
		}
		return that instanceof SemanticArtifactVersion sav ? compareTo(sav) : 1;
	}

	/**
	 * Compare this semantic version with another semantic version.
	 */
	public int compareTo(SemanticArtifactVersion that) {

		int versionsEqual = this.components.compareTo(that.components);
		return versionsEqual != 0 ? versionsEqual : this.suffix.compareTo(that.suffix);
	}

	@Override
	public String toString() {
		return version;
	}

	private String getSnapshotSuffix() {
		return modifierFormat ? Suffix.SNAPSHOT_MODIFIER : Suffix.BUILD_SNAPSHOT_SUFFIX;
	}

	private ArtifactVersion snapshotOf(NumericVersionComponents version) {
		return new SemanticArtifactVersion(version.toString() + (modifierFormat ? "-" : ".") + getSnapshotSuffix(),
				version, modifierFormat, Suffix.parse(getSnapshotSuffix()));
	}

	private ArtifactVersion versionOf(NumericVersionComponents version) {
		return new SemanticArtifactVersion(version.toString(), version, modifierFormat, suffix);
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof SemanticArtifactVersion other)) {
			return false;
		}
		return components.compareTo(other.components) == 0 && suffix.compareTo(other.suffix) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(components, suffix.canonical());
	}

}
