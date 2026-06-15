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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersioningScheme;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for {@link Releases}.
 *
 * <p>Methods that locate a release navigate to {@link ReleaseAssert} so tests
 * can continue with assertions on that release's version and date. Methods that
 * verify collection-level shape (ordering, scheme precedence, membership)
 * return this assertion object for chaining.
 *
 * <p>Example: <pre class="code">
 * assertThat(releases)
 *     .containsRelease("1.0.0")
 *     .hasReleaseDate("2026-06-08");
 *
 * assertThat(releases)
 *     .hasSuccessorScheme(VersioningScheme.NUMERIC)
 *     .containsExactlyVersions("3.10.0", "3.9.9", "3.9.6");
 * </pre>
 *
 * @author Mark Paluch
 * @see ReleaseAssert
 */
public class ReleasesAssert extends AbstractAssert<ReleasesAssert, Releases> {

	ReleasesAssert(Releases releases) {
		super(releases, ReleasesAssert.class);
	}

	/**
	 * Verifies that a release for the given version is contained and returns an
	 * assertion object for that release.
	 * @param version the version to look up.
	 * @return an assertion object for the matching release.
	 */
	public ReleaseAssert containsRelease(String version) {
		return containsRelease(ArtifactVersion.of(version));
	}

	/**
	 * Verifies that a release for the given version is contained and returns an
	 * assertion object for that release.
	 * @param version the version to look up.
	 * @return an assertion object for the matching release.
	 */
	public ReleaseAssert containsRelease(ArtifactVersion version) {
		isNotNull();
		Release release = this.actual.getRelease(version);
		if (release == null) {
			failWithMessage("Expected releases to contain '%s' but available versions were %s", version,
					versions());
		}
		return new ReleaseAssert(release);
	}

	/**
	 * Verifies that no release for the given version is contained.
	 * @param version the version expected to be absent.
	 * @return this assertion object.
	 */
	public ReleasesAssert doesNotContainRelease(String version) {
		return doesNotContainRelease(ArtifactVersion.of(version));
	}

	/**
	 * Verifies that no release for the given version is contained.
	 * @param version the version expected to be absent.
	 * @return this assertion object.
	 */
	public ReleasesAssert doesNotContainRelease(ArtifactVersion version) {
		isNotNull();
		Release release = this.actual.getRelease(version);
		if (release != null) {
			failWithMessage("Expected releases not to contain '%s' but found '%s'", version, release);
		}
		return this;
	}

	/**
	 * Verifies that the releases, in artifact-level order, carry exactly the given
	 * version strings.
	 * @param expected the expected version strings, in order.
	 * @return this assertion object.
	 */
	public ReleasesAssert containsExactlyVersions(String... expected) {
		isNotNull();
		Assertions.assertThat(versions()).containsExactly(expected);
		return this;
	}

	/**
	 * Verifies that the releases within the given scheme, newest first, carry
	 * exactly the given version strings. Passing no versions asserts the scheme is
	 * empty.
	 * @param scheme the versioning scheme to select.
	 * @param expected the expected version strings, in order.
	 * @return this assertion object.
	 */
	public ReleasesAssert containsExactlyVersionsInScheme(VersioningScheme scheme, String... expected) {
		isNotNull();
		List<String> actualVersions = this.actual.inScheme(scheme).stream().map(release -> release.version().toString())
				.toList();
		Assertions.assertThat(actualVersions).containsExactly(expected);
		return this;
	}

	/**
	 * Verifies that the successor scheme equals the given scheme.
	 * @param expected the expected successor scheme.
	 * @return this assertion object.
	 */
	public ReleasesAssert hasSuccessorScheme(VersioningScheme expected) {
		isNotNull();
		if (expected != this.actual.successorScheme()) {
			failWithMessage("Expected successor scheme to be '%s' but was '%s'", expected,
					this.actual.successorScheme());
		}
		return this;
	}

	/**
	 * Verifies that there is no successor scheme, i.e. the releases are empty.
	 * @return this assertion object.
	 */
	public ReleasesAssert hasNoSuccessorScheme() {
		isNotNull();
		if (this.actual.successorScheme() != null) {
			failWithMessage("Expected no successor scheme but was '%s'", this.actual.successorScheme());
		}
		return this;
	}

	/**
	 * Verifies that there are no releases.
	 * @return this assertion object.
	 */
	public ReleasesAssert isEmpty() {
		isNotNull();
		if (!this.actual.isEmpty()) {
			failWithMessage("Expected no releases but found %s", versions());
		}
		return this;
	}

	private List<String> versions() {
		return this.actual.toList().stream().map(release -> release.version().toString()).toList();
	}

}
