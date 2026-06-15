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

import java.time.LocalDateTime;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for a single {@link Release}.
 *
 * <p>Instances are obtained from {@link Assertions#assertThat(Release)} or by
 * navigation from {@link ReleasesAssert#containsRelease(String)} after the
 * containing {@code Releases} assertion has established that the release
 * exists.
 *
 * <p>Example: <pre class="code">
 * assertThat(releases)
 *     .containsRelease("8.14.3")
 *     .hasReleaseDate("2025-07-04T10:12:13");
 * </pre>
 *
 * @author Mark Paluch
 * @see ReleasesAssert
 */
public class ReleaseAssert extends AbstractAssert<ReleaseAssert, Release> {

	ReleaseAssert(Release release) {
		super(release, ReleaseAssert.class);
	}

	/**
	 * Verifies that the actual release version string equals the given value.
	 * @param expected the expected version string.
	 * @return this assertion object.
	 */
	public ReleaseAssert hasVersion(String expected) {
		return hasVersion(ArtifactVersion.of(expected));
	}

	/**
	 * Verifies that the actual release version equals the given value.
	 * @param expected the expected version.
	 * @return this assertion object.
	 */
	public ReleaseAssert hasVersion(ArtifactVersion expected) {
		isNotNull();
		if (!expected.equals(this.actual.version())) {
			failWithMessage("Expected release version to be '%s' but was '%s'", expected, this.actual.version());
		}
		return this;
	}

	/**
	 * Verifies that the actual release date equals the given ISO date or date-time.
	 * @param expected the expected ISO-8601 date or date-time string.
	 * @return this assertion object.
	 */
	public ReleaseAssert hasReleaseDate(String expected) {
		return hasReleaseDate(Release.parseReleaseDate(expected));
	}

	/**
	 * Verifies that the actual release date equals the given value.
	 * @param expected the expected release date.
	 * @return this assertion object.
	 */
	public ReleaseAssert hasReleaseDate(LocalDateTime expected) {
		isNotNull();
		if (!expected.equals(this.actual.releaseDate())) {
			failWithMessage("Expected release '%s' to have release date '%s' but was '%s'", this.actual.version(),
					expected, this.actual.releaseDate());
		}
		return this;
	}

	/**
	 * Verifies that the actual release carries no release date.
	 * @return this assertion object.
	 */
	public ReleaseAssert hasNoReleaseDate() {
		isNotNull();
		if (this.actual.releaseDate() != null) {
			failWithMessage("Expected release '%s' to have no release date but was '%s'", this.actual.version(),
					this.actual.releaseDate());
		}
		return this;
	}

}
