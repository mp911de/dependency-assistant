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

/**
 * Wrapper class for {@link ArtifactVersion} delegating to the wrapped version.
 * 
 * @author Mark Paluch
 */
abstract class ArtifactVersionWrapper implements ArtifactVersion {

	private final ArtifactVersion delegate;

	protected ArtifactVersionWrapper(ArtifactVersion delegate) {
		this.delegate = delegate;
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

	/**
	 * {@inheritDoc}
	 *
	 * <p>
	 * For another {@code GitVersion}, the comparison delegates to the wrapped
	 * {@link ArtifactVersion} so that GitHub-only release lists sort consistently.
	 *
	 * <p>
	 * For non-{@code GitVersion} arguments, comparison throws
	 * {@link ClassCastException}. Returning {@code 0} for incomparable types would
	 * silently mis-merge sorted lists; throwing makes the incompatibility loud and
	 * matches the {@link Comparable} contract that orderings be total within a
	 * compared type.
	 */
	@Override
	public int compareTo(ArtifactVersion other) {
		return delegate.compareTo(other.getVersion());
	}

	@Override
	public boolean isWrapped() {
		return true;
	}

	@Override
	public ArtifactVersion getVersion() {
		return delegate;
	}

	@Override
	public String toString() {
		return delegate.toString();
	}

}
