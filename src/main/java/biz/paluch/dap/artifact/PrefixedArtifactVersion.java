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

/**
 * {@link ArtifactVersion} that wraps an inner version with a string prefix such
 * as {@code v}.
 *
 * <p>The prefix is preserved in {@link #toString()} so that the original
 * version string round-trips correctly (e.g. {@code v1.2.3}). All ordering and
 * compatibility methods delegate to the wrapped version after stripping any
 * prefix from the operand as well, so prefixed and unprefixed versions of the
 * same release compare as equal.
 *
 * <p>Instances are created by {@link ArtifactVersion#of(String)} and
 * {@link ArtifactVersion#from(String)} when the input carries a known prefix.
 * Callers that need the bare version for cache-key lookups should call
 * {@link #unwrap()}.
 *
 * <p>Note: {@link #compareTo} is not consistent with {@link #equals}. Two
 * instances representing the same numeric version but differing only by prefix
 * (e.g. {@code v1.2.3} vs {@code 1.2.3}) compare as equal (return 0) but are
 * not {@code equals}. This is the same design choice made by
 * {@link java.math.BigDecimal}.
 *
 * @author Mark Paluch
 * @see ArtifactVersion#isWrapped()
 * @see ArtifactVersion#unwrap()
 */
class PrefixedArtifactVersion implements ArtifactVersion {

	private final String prefix;

	private final ArtifactVersion delegate;

	PrefixedArtifactVersion(String prefix, ArtifactVersion delegate) {
		this.prefix = prefix;
		this.delegate = delegate;
	}

	@Override
	public ArtifactVersion getVersion() {
		return delegate;
	}

	@Override
	public boolean isWrapped() {
		return true;
	}

	@Override
	public ArtifactVersion unwrap() {
		return delegate;
	}

	@Override
	public boolean isNewer(ArtifactVersion other) {
		return delegate.isNewer(other.getVersion());
	}

	@Override
	public boolean isNewerMinor(ArtifactVersion other) {
		return delegate.isNewerMinor(other.getVersion());
	}

	@Override
	public boolean isOlder(ArtifactVersion other) {
		return delegate.isOlder(other.getVersion());
	}

	@Override
	public boolean hasSameMajorMinor(ArtifactVersion other) {
		return delegate.hasSameMajorMinor(other.getVersion());
	}

	@Override
	public boolean hasSameMajor(ArtifactVersion other) {
		return delegate.hasSameMajor(other.getVersion());
	}

	@Override
	public boolean isSnapshotVersion() {
		return delegate.isSnapshotVersion();
	}

	@Override
	public boolean isMilestoneVersion() {
		return delegate.isMilestoneVersion();
	}

	@Override
	public boolean isReleaseCandidateVersion() {
		return delegate.isReleaseCandidateVersion();
	}

	@Override
	public boolean isPreview() {
		return delegate.isPreview();
	}

	@Override
	public boolean isReleaseVersion() {
		return delegate.isReleaseVersion();
	}

	@Override
	public boolean isBugFixVersion() {
		return delegate.isBugFixVersion();
	}

	@Override
	public int compareTo(ArtifactVersion other) {
		return delegate.compareTo(other.getVersion());
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		if (!(obj instanceof PrefixedArtifactVersion other)) {
			return false;
		}
		return prefix.equals(other.prefix) && delegate.equals(other.delegate);
	}

	@Override
	public int hashCode() {
		return Objects.hash(prefix, delegate);
	}

	@Override
	public String toString() {
		return prefix + delegate;
	}

}
