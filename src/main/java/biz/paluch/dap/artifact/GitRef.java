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
 * Artifact version backed by an opaque Git reference.
 *
 * <p>A Git reference can be a branch, tag, commit, or other ref name discovered
 * from a dependency declaration. It preserves the declared ref string for
 * display and equality, but does not classify itself as a semantic release,
 * milestone, snapshot, or bugfix version.
 *
 * @author Mark Paluch
 */
public class GitRef implements ArtifactVersion {

	private final String ref;

	/**
	 * Create a Git reference version.
	 *
	 * @param ref the declared Git reference string; must not be {@literal null}.
	 */
	public GitRef(String ref) {
		this.ref = ref;
	}

	/**
	 * Return the declared Git reference.
	 *
	 * @return the Git reference string.
	 */
	public String getRef() {
		return ref;
	}

	@Override
	public boolean isNewer(ArtifactVersion other) {
		return false;
	}

	@Override
	public boolean isNewerMinor(ArtifactVersion other) {
		return false;
	}

	@Override
	public boolean isOlder(ArtifactVersion other) {
		return false;
	}

	@Override
	public boolean hasSameMajor(ArtifactVersion other) {
		return false;
	}

	@Override
	public boolean hasSameMajorMinor(ArtifactVersion other) {
		return false;
	}

	@Override
	public boolean hasSameBaseVersion(ArtifactVersion other) {
		return false;
	}

	@Override
	public boolean isSnapshotVersion() {
		return false;
	}

	@Override
	public boolean isMilestoneVersion() {
		return false;
	}

	@Override
	public boolean isReleaseCandidateVersion() {
		return false;
	}

	@Override
	public boolean isPreview() {
		return false;
	}

	@Override
	public boolean isReleaseVersion() {
		return false;
	}

	@Override
	public boolean isBugFixVersion() {
		return false;
	}

	/**
	 * Compare this ref lexically with another artifact version's display string.
	 *
	 * @param o the artifact version to compare with.
	 * @return the lexical comparison result.
	 */
	@Override
	public int compareTo(ArtifactVersion o) {
		return ref.compareTo(o.toString());
	}

	@Override
	public boolean equals(Object o) {
		if (o == null || getClass() != o.getClass()) {
			return false;
		}
		GitRef gitRef = (GitRef) o;
		return Objects.equals(ref, gitRef.ref);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(ref);
	}

	@Override
	public String toString() {
		return ref;
	}

}
