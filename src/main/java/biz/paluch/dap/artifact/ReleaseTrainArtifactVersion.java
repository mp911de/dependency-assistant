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

import java.util.Objects;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.Suffix.Release;
import biz.paluch.dap.artifact.Suffix.SemVerSuffix;
import biz.paluch.dap.artifact.Suffix.Snapshot;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Named release-train version, such as {@code Aluminium-M1} or
 * {@code Hoxton.SR12}.
 * <p>Rendering preserves the dot or hyphen separator.
 *
 * @author Mark Paluch
 */
class ReleaseTrainArtifactVersion implements ArtifactVersion {

	/**
	 * Restrict dotted suffixes so property and file names do not enter the version
	 * cache.
	 */
	private static final Pattern DOT_QUALIFIER = Pattern.compile("(SR|RC|M)\\d+|RELEASE|BUILD-SNAPSHOT");

	private final String trainName;

	private final String separator;
	private final Suffix suffix;

	ReleaseTrainArtifactVersion(String trainName, Suffix suffix) {
		this(trainName, "-", suffix);
	}

	private ReleaseTrainArtifactVersion(String trainName, String separator, Suffix suffix) {
		this.trainName = trainName;
		this.separator = separator;
		this.suffix = suffix;
	}

	/**
	 * Parse a release-train version, or return {@literal null} if unrecognized.
	 */
	@Nullable
	static ArtifactVersion tryParse(String source) {

		if (StringUtils.isEmpty(source)) {
			return null;
		}

		String trimmed = source.trim();
		int separatorIndex = separatorIndex(trimmed);
		if (separatorIndex < 1 || separatorIndex == trimmed.length() - 1) {
			return null;
		}

		String train = trimmed.substring(0, separatorIndex);
		String suffixPart = trimmed.substring(separatorIndex + 1);
		if (StringUtils.isEmpty(train) || StringUtils.isEmpty(suffixPart)) {
			return null;
		}
		// Require train to start with a letter
		if (!Character.isLetter(train.charAt(0))) {
			return null;
		}

		String separator = trimmed.substring(separatorIndex, separatorIndex + 1);
		if (".".equals(separator) && !DOT_QUALIFIER.matcher(suffixPart).matches()) {
			return null;
		}

		return new ReleaseTrainArtifactVersion(train, separator, Suffix.parse(suffixPart));
	}

	private static int separatorIndex(String source) {

		for (int i = 0; i < source.length(); i++) {

			char c = source.charAt(i);
			if (c == '-' || c == '.') {
				return i;
			}
		}
		return -1;
	}

	/**
	 * Parse a release-train version.
	 * @throws IllegalArgumentException if the string does not match a release-train
	 * pattern.
	 */
	static ArtifactVersion of(String source) {
		ArtifactVersion v = tryParse(source);
		if (v != null) {
			return v;
		}
		throw new IllegalArgumentException("Version '" + source
				+ "' does not match release-train pattern <train>-<suffix> or <train>.<suffix>"
				+ " (e.g. Aluminium-M1, Hoxton.SR12)");
	}

	static boolean isReleaseTrainVersion(String source) {
		return tryParse(source) != null;
	}

	public String getTrainName() {
		return trainName;
	}

	public Suffix getSuffix() {
		return suffix;
	}

	@Override
	public VersioningScheme scheme() {
		return VersioningScheme.RELEASE_TRAIN;
	}

	@Override
	public boolean isNewerMinor(ArtifactVersion other) {
		if (other instanceof ReleaseTrainArtifactVersion otherTrain) {
			return trainName.equals(otherTrain.trainName) && compareTo(other) < 0;
		}
		return false;
	}

	@Override
	public boolean hasSameMajor(ArtifactVersion other) {
		return other instanceof ReleaseTrainArtifactVersion otherTrain && trainName.equalsIgnoreCase(otherTrain.trainName);
	}

	@Override
	public boolean hasSameMajorMinor(ArtifactVersion other) {
		return other instanceof ReleaseTrainArtifactVersion otherTrain && trainName.equalsIgnoreCase(otherTrain.trainName);
	}

	@Override
	public boolean hasSameBaseVersion(ArtifactVersion other) {
		return other.getVersion() instanceof ReleaseTrainArtifactVersion otherTrain
				&& trainName.equalsIgnoreCase(otherTrain.trainName);
	}

	@Override
	public boolean isSnapshotVersion() {
		return suffix instanceof Snapshot;
	}

	@Override
	public boolean isMilestoneVersion() {
		return suffix instanceof SemVerSuffix sv && sv.isMilestone();
	}

	@Override
	public boolean isReleaseCandidateVersion() {
		return suffix instanceof SemVerSuffix sv && sv.isReleaseCandidate();
	}

	@Override
	public boolean isReleaseVersion() {
		return suffix instanceof Release;
	}

	@Override
	public boolean isBugFixVersion() {
		return suffix instanceof SemVerSuffix sv && "SR".equals(sv.type());
	}

	@Override
	public int compareTo(ArtifactVersion that) {
		if (that.isWrapped()) {
			return compareTo(that.getVersion());
		}
		if (that instanceof ReleaseTrainArtifactVersion other) {
			int trainCompare = trainName.compareToIgnoreCase(other.trainName);
			return trainCompare != 0 ? trainCompare : suffix.compareTo(other.suffix);
		}

		int era = VersioningScheme.compareEra(scheme(), that.scheme());
		return era != 0 ? era : toString().compareToIgnoreCase(that.toString());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof ArtifactVersion av)) {
			return false;
		}
		if (av.isWrapped()) {
			return equals(av.getVersion());
		}
		if (!(obj instanceof ReleaseTrainArtifactVersion other)) {
			return false;
		}
		return trainName.equalsIgnoreCase(other.trainName) && suffix.compareTo(other.suffix) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(trainName.toLowerCase(), suffix.canonical());
	}

	@Override
	public String toString() {
		return trainName + separator + suffix.canonical();
	}

}
