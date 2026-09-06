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

package biz.paluch.dap.assertions;

import biz.paluch.dap.artifact.ArtifactVersion;
import org.assertj.core.api.AbstractComparableAssert;

/**
 * AssertJ assertions for {@link ArtifactVersion}.
 * <p>String overloads keep version syntax visible in ordering tests.
 *
 * @author Mark Paluch
 */
public class ArtifactVersionAssert extends AbstractComparableAssert<ArtifactVersionAssert, ArtifactVersion> {

	ArtifactVersionAssert(ArtifactVersion actual) {
		super(actual, ArtifactVersionAssert.class);
	}

	public ArtifactVersionAssert isLessThan(String expected) {
		return isLessThan(ArtifactVersion.of(expected));
	}

	public ArtifactVersionAssert isGreaterThan(String expected) {
		return isGreaterThan(ArtifactVersion.of(expected));
	}

	public ArtifactVersionAssert isEqualTo(String expected) {
		return isEqualTo(ArtifactVersion.of(expected));
	}

	public ArtifactVersionAssert isEqualByComparingTo(String expected) {
		return isEqualByComparingTo(ArtifactVersion.of(expected));
	}

	public ArtifactVersionAssert isNotEqualByComparingTo(String expected) {
		return isNotEqualByComparingTo(ArtifactVersion.of(expected));
	}

	public ArtifactVersionAssert isRelease() {
		isNotNull();
		if (!this.actual.isReleaseVersion()) {
			failWithMessage("Expected version '%s' to be a release version", this.actual);
		}
		return this;
	}

	public ArtifactVersionAssert isNotRelease() {
		isNotNull();
		if (this.actual.isReleaseVersion()) {
			failWithMessage("Expected version '%s' not to be a release version", this.actual);
		}
		return this;
	}

	public ArtifactVersionAssert isSnapshot() {
		isNotNull();
		if (!this.actual.isSnapshotVersion()) {
			failWithMessage("Expected version '%s' to be a snapshot version", this.actual);
		}
		return this;
	}

	public ArtifactVersionAssert isNotSnapshot() {
		isNotNull();
		if (this.actual.isSnapshotVersion()) {
			failWithMessage("Expected version '%s' not to be a snapshot version", this.actual);
		}
		return this;
	}

	public ArtifactVersionAssert isMilestone() {
		isNotNull();
		if (!this.actual.isMilestoneVersion()) {
			failWithMessage("Expected version '%s' to be a milestone version", this.actual);
		}
		return this;
	}

	public ArtifactVersionAssert isNotMilestone() {
		isNotNull();
		if (this.actual.isMilestoneVersion()) {
			failWithMessage("Expected version '%s' not to be a milestone version", this.actual);
		}
		return this;
	}

	public ArtifactVersionAssert isReleaseCandidate() {
		isNotNull();
		if (!this.actual.isReleaseCandidateVersion()) {
			failWithMessage("Expected version '%s' to be a release candidate version", this.actual);
		}
		return this;
	}

	public ArtifactVersionAssert isNotReleaseCandidate() {
		isNotNull();
		if (this.actual.isReleaseCandidateVersion()) {
			failWithMessage("Expected version '%s' not to be a release candidate version", this.actual);
		}
		return this;
	}

	public ArtifactVersionAssert isPreview() {
		isNotNull();
		if (!this.actual.isPreview()) {
			failWithMessage("Expected version '%s' to be a preview version", this.actual);
		}
		return this;
	}

	public ArtifactVersionAssert isNotPreview() {
		isNotNull();
		if (this.actual.isPreview()) {
			failWithMessage("Expected version '%s' not to be a preview version", this.actual);
		}
		return this;
	}

}
