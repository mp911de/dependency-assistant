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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersioningScheme;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for {@link Releases}.
 *
 * @author Mark Paluch
 */
public class ReleasesAssert extends AbstractAssert<ReleasesAssert, Releases> {

	ReleasesAssert(Releases releases) {
		super(releases, ReleasesAssert.class);
	}

	public ReleaseAssert containsRelease(String version) {
		return containsRelease(ArtifactVersion.of(version));
	}

	public ReleaseAssert containsRelease(ArtifactVersion version) {
		isNotNull();
		Release release = this.actual.getRelease(version);
		if (release == null) {
			failWithMessage("Expected releases to contain '%s' but available versions were %s", version,
					versions());
		}
		return new ReleaseAssert(release);
	}

	public ReleasesAssert doesNotContainRelease(String version) {
		return doesNotContainRelease(ArtifactVersion.of(version));
	}

	public ReleasesAssert doesNotContainRelease(ArtifactVersion version) {
		isNotNull();
		Release release = this.actual.getRelease(version);
		if (release != null) {
			failWithMessage("Expected releases not to contain '%s' but found '%s'", version, release);
		}
		return this;
	}

	/**
	 * Require these version strings in artifact-level order.
	 */
	public ReleasesAssert containsExactlyVersions(String... expected) {
		isNotNull();
		Assertions.assertThat(versions()).containsExactly(expected);
		return this;
	}

	/**
	 * Require these version strings within the scheme, newest first.
	 * <p>No expected versions means the scheme must be empty.
	 */
	public ReleasesAssert containsExactlyVersionsInScheme(VersioningScheme scheme, String... expected) {
		isNotNull();
		List<String> actualVersions = this.actual.inScheme(scheme).stream().map(release -> release.version().toString())
				.toList();
		Assertions.assertThat(actualVersions).containsExactly(expected);
		return this;
	}

	public ReleasesAssert hasSuccessorScheme(VersioningScheme expected) {
		isNotNull();
		if (expected != this.actual.successorScheme()) {
			failWithMessage("Expected successor scheme to be '%s' but was '%s'", expected,
					this.actual.successorScheme());
		}
		return this;
	}

	public ReleasesAssert hasNoSuccessorScheme() {
		isNotNull();
		if (this.actual.successorScheme() != null) {
			failWithMessage("Expected no successor scheme but was '%s'", this.actual.successorScheme());
		}
		return this;
	}

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
