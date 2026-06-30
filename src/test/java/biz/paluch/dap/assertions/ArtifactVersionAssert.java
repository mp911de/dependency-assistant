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

package biz.paluch.dap.assertions;

import biz.paluch.dap.artifact.ArtifactVersion;
import org.assertj.core.api.AbstractComparableAssert;

/**
 * AssertJ assertions for {@link ArtifactVersion}.
 *
 * <p>The string overloads parse expected values at the assertion boundary so
 * ordering tests can keep the version syntax visible without repeating
 * {@code ArtifactVersion.of(...)} or local parsing helpers.
 *
 * <p>Example: <pre class="code">
 * assertThatVersion("1.0.0-M2")
 *     .isLessThan("1.0.0-RC1")
 *     .isGreaterThan("1.0.0-M1");
 * </pre>
 *
 * @author Mark Paluch
 */
public class ArtifactVersionAssert extends AbstractComparableAssert<ArtifactVersionAssert, ArtifactVersion> {

	ArtifactVersionAssert(ArtifactVersion actual) {
		super(actual, ArtifactVersionAssert.class);
	}

	/**
	 * Verifies that the actual version compares less than the given version string.
	 * @param expected the expected upper bound.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isLessThan(String expected) {
		return isLessThan(ArtifactVersion.of(expected));
	}

	/**
	 * Verifies that the actual version compares greater than the given version
	 * string.
	 * @param expected the expected lower bound.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isGreaterThan(String expected) {
		return isGreaterThan(ArtifactVersion.of(expected));
	}

	/**
	 * Verifies that the actual version compares equal to the given version string.
	 * @param expected the expected comparable version.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isEqualByComparingTo(String expected) {
		return isEqualByComparingTo(ArtifactVersion.of(expected));
	}

	/**
	 * Verifies that the actual version does not compare equal to the given version
	 * string.
	 * @param expected the expected comparable version.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isNotEqualByComparingTo(String expected) {
		return isNotEqualByComparingTo(ArtifactVersion.of(expected));
	}

	/**
	 * Verifies that the actual version is a general-availability release.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isRelease() {
		isNotNull();
		if (!this.actual.isReleaseVersion()) {
			failWithMessage("Expected version '%s' to be a release version", this.actual);
		}
		return this;
	}

	/**
	 * Verifies that the actual version is not a general-availability release.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isNotRelease() {
		isNotNull();
		if (this.actual.isReleaseVersion()) {
			failWithMessage("Expected version '%s' not to be a release version", this.actual);
		}
		return this;
	}

	/**
	 * Verifies that the actual version is a snapshot.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isSnapshot() {
		isNotNull();
		if (!this.actual.isSnapshotVersion()) {
			failWithMessage("Expected version '%s' to be a snapshot version", this.actual);
		}
		return this;
	}

	/**
	 * Verifies that the actual version is not a snapshot.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isNotSnapshot() {
		isNotNull();
		if (this.actual.isSnapshotVersion()) {
			failWithMessage("Expected version '%s' not to be a snapshot version", this.actual);
		}
		return this;
	}

	/**
	 * Verifies that the actual version is a milestone-like pre-release.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isMilestone() {
		isNotNull();
		if (!this.actual.isMilestoneVersion()) {
			failWithMessage("Expected version '%s' to be a milestone version", this.actual);
		}
		return this;
	}

	/**
	 * Verifies that the actual version is not a milestone-like pre-release.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isNotMilestone() {
		isNotNull();
		if (this.actual.isMilestoneVersion()) {
			failWithMessage("Expected version '%s' not to be a milestone version", this.actual);
		}
		return this;
	}

	/**
	 * Verifies that the actual version is a release candidate.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isReleaseCandidate() {
		isNotNull();
		if (!this.actual.isReleaseCandidateVersion()) {
			failWithMessage("Expected version '%s' to be a release candidate version", this.actual);
		}
		return this;
	}

	/**
	 * Verifies that the actual version is not a release candidate.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isNotReleaseCandidate() {
		isNotNull();
		if (this.actual.isReleaseCandidateVersion()) {
			failWithMessage("Expected version '%s' not to be a release candidate version", this.actual);
		}
		return this;
	}

	/**
	 * Verifies that the actual version is a preview.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isPreview() {
		isNotNull();
		if (!this.actual.isPreview()) {
			failWithMessage("Expected version '%s' to be a preview version", this.actual);
		}
		return this;
	}

	/**
	 * Verifies that the actual version is not a preview.
	 * @return this assertion object.
	 */
	public ArtifactVersionAssert isNotPreview() {
		isNotNull();
		if (this.actual.isPreview()) {
			failWithMessage("Expected version '%s' not to be a preview version", this.actual);
		}
		return this;
	}

}
