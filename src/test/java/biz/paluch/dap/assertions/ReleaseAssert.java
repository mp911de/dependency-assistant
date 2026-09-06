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

import java.time.LocalDateTime;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for a single {@link Release}.
 *
 * @author Mark Paluch
 */
public class ReleaseAssert extends AbstractAssert<ReleaseAssert, Release> {

	ReleaseAssert(Release release) {
		super(release, ReleaseAssert.class);
	}

	public ReleaseAssert hasVersion(String expected) {
		return hasVersion(ArtifactVersion.of(expected));
	}

	public ReleaseAssert hasVersion(ArtifactVersion expected) {
		isNotNull();
		if (!expected.equals(this.actual.version())) {
			failWithMessage("Expected release version to be '%s' but was '%s'", expected, this.actual.version());
		}
		return this;
	}

	/**
	 * Compare the release date with an ISO-8601 date or date-time.
	 */
	public ReleaseAssert hasReleaseDate(String expected) {
		return hasReleaseDate(Release.parseReleaseDate(expected));
	}

	public ReleaseAssert hasReleaseDate(LocalDateTime expected) {
		isNotNull();
		if (!expected.equals(this.actual.releaseDate())) {
			failWithMessage("Expected release '%s' to have release date '%s' but was '%s'", this.actual.version(),
					expected, this.actual.releaseDate());
		}
		return this;
	}

	public ReleaseAssert hasSha(String expected) {
		isNotNull();
		if (!(this.actual.version() instanceof GitVersion gitVersion)) {
			failWithMessage("Expected release version '%s' to be a GitVersion but was %s", this.actual.version(),
					this.actual.version().getClass().getSimpleName());
			return this;
		}
		if (!expected.equals(gitVersion.getSha())) {
			failWithMessage("Expected release '%s' to have SHA '%s' but was '%s'", this.actual.version(), expected,
					gitVersion.getSha());
		}
		return this;
	}

	public ReleaseAssert hasNoReleaseDate() {
		isNotNull();
		if (this.actual.releaseDate() != null) {
			failWithMessage("Expected release '%s' to have no release date but was '%s'", this.actual.version(),
					this.actual.releaseDate());
		}
		return this;
	}

}
