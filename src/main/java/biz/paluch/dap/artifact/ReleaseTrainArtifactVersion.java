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

import biz.paluch.dap.artifact.Suffix.Release;
import biz.paluch.dap.artifact.Suffix.SemVerSuffix;
import biz.paluch.dap.artifact.Suffix.Snapshot;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * {@link ArtifactVersion} for release-train style versions such as {@code Aluminium-M1}, {@code Aluminium-RELEASE}, or
 * {@code Bismuth-SR1}, where the first part is the train name and the second is a {@link Suffix}.
 *
 * @author Mark Paluch
 */
class ReleaseTrainArtifactVersion implements ArtifactVersion {

	private final String trainName;
	private final Suffix suffix;

	ReleaseTrainArtifactVersion(String trainName, Suffix suffix) {
		this.trainName = trainName;
		this.suffix = suffix;
	}

	/**
	 * Try to parse a release-train version without throwing. Returns {@code null} if the string does not match the
	 * train-name-suffix pattern.
	 *
	 * @param source the version string (e.g. {@code Aluminium-M1}, {@code Bismuth-SR1})
	 * @return a new {@link ArtifactVersion} or {@code null} if not a release-train version
	 */
	@Nullable
	static ArtifactVersion tryParse(String source) {

		if (StringUtils.isEmpty(source)) {
			return null;
		}

		String trimmed = source.trim();
		int hyphen = trimmed.indexOf('-');
		if (hyphen < 1 || hyphen == trimmed.length() - 1) {
			return null;
		}
		String train = trimmed.substring(0, hyphen);
		String suffixPart = trimmed.substring(hyphen + 1);
		if (StringUtils.isEmpty(train) || StringUtils.isEmpty(suffixPart)) {
			return null;
		}
		// Require train to start with a letter
		if (!Character.isLetter(train.charAt(0))) {
			return null;
		}
		return new ReleaseTrainArtifactVersion(train, Suffix.parse(suffixPart));
	}

	/**
	 * Parse a release-train version string. Throws if the string is not a valid release-train version.
	 *
	 * @param source the version string
	 * @return a new {@link ArtifactVersion}
	 * @throws IllegalArgumentException if the string does not match the release-train pattern
	 */
	static ArtifactVersion of(String source) {
		ArtifactVersion v = tryParse(source);
		if (v != null) {
			return v;
		}
		throw new IllegalArgumentException("Version '" + source
				+ "' does not match release-train pattern <train>-<suffix> (e.g. Aluminium-M1, Bismuth-SR1)");
	}

	/**
	 * Return whether the given string looks like a release-train version.
	 *
	 * @param source the version string
	 * @return true if parsing as release-train succeeds
	 */
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
	public boolean isNewer(ArtifactVersion other) {
		return compareTo(other) > 0;
	}

	@Override
	public boolean isOlder(ArtifactVersion other) {
		return compareTo(other) < 0;
	}

	@Override
	public boolean isNewerMinor(ArtifactVersion other) {
		if (other instanceof ReleaseTrainArtifactVersion otherTrain) {
			return trainName.equals(otherTrain.trainName) && compareTo(other) < 0;
		}
		return false;
	}

	@Override
	public boolean hasSameMajorMinor(ArtifactVersion other) {
		return other instanceof ReleaseTrainArtifactVersion otherTrain && trainName.equalsIgnoreCase(otherTrain.trainName);
	}

	@Override
	public boolean hasSameMajor(ArtifactVersion other) {
		return other instanceof ReleaseTrainArtifactVersion otherTrain && trainName.equalsIgnoreCase(otherTrain.trainName);
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
	public boolean isPreview() {
		return isMilestoneVersion() || isReleaseCandidateVersion();
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
		if (that instanceof ReleaseTrainArtifactVersion other) {
			int trainCompare = trainName.compareToIgnoreCase(other.trainName);
			return trainCompare != 0 ? trainCompare : suffix.compareTo(other.suffix);
		}
		return -1;
	}

	@Override
	public String toString() {
		return trainName + "-" + suffix.canonical();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof ReleaseTrainArtifactVersion other)) {
			return false;
		}
		return trainName.equalsIgnoreCase(other.trainName) && suffix.compareTo(other.suffix) == 0;
	}

	@Override
	public int hashCode() {
		return Objects.hash(trainName.toLowerCase(), suffix.canonical());
	}
}
