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

package biz.paluch.dap.github;

import biz.paluch.dap.artifact.ArtifactVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitVersion}.
 *
 * @author Mark Paluch
 */
class GitVersionUnitTests {

	private static final String SHA = "abc1234567890123456789012345678901234567890".substring(0, 40);

	@Test
	void toStringReturnsDelegateNotSha() {
		assertThat(gitVersion(SHA, "1.2.3").toString()).isEqualTo("1.2.3");
	}

	@Test
	void getShaReturnsSha() {
		assertThat(gitVersion(SHA, "1.2.3").getSha()).isEqualTo(SHA);
	}

	@Test
	void getShaIsNullWhenAbsent() {
		assertThat(gitVersion("1.2.3").getSha()).isNull();
	}

	@Test
	void canCompareReturnsTrueForGitVersion() {
		GitVersion a = gitVersion("1.2.3");
		GitVersion b = gitVersion("1.2.4");
		assertThat(a.canCompare(b)).isTrue();
	}

	@Test
	void canCompareReturnsFalseForNonGitVersion() {
		GitVersion a = gitVersion("1.2.3");
		ArtifactVersion semantic = ArtifactVersion.of("1.2.4");
		assertThat(a.canCompare(semantic)).isFalse();
	}

	@Test
	void hasSameMajorMinorReturnsTrueForSameMajorMinor() {
		assertThat(gitVersion("1.2.3").hasSameMajorMinor(gitVersion("1.2.4"))).isTrue();
	}

	@Test
	void hasSameMajorMinorReturnsFalseForDifferentMinor() {
		assertThat(gitVersion("1.2.3").hasSameMajorMinor(gitVersion("1.3.0"))).isFalse();
	}

	@Test
	void hasSameMajorMinorReturnsForNonGitVersion() {
		assertThat(gitVersion("1.2.3").hasSameMajorMinor(ArtifactVersion.of("1.2.3"))).isTrue();
	}

	@Test
	void isNewerReturnsTrueWhenDelegateIsNewer() {
		assertThat(gitVersion("1.2.4").isNewer(gitVersion("1.2.3"))).isTrue();
	}

	@Test
	void isNewerReturnsForNonGitVersion() {
		assertThat(gitVersion("1.2.4").isNewer(ArtifactVersion.of("1.2.3"))).isTrue();
	}

	@Test
	void isOlderReturnsTrueWhenDelegateIsOlder() {
		assertThat(gitVersion("1.2.3").isOlder(gitVersion("1.2.4"))).isTrue();
	}

	@Test
	void compareToOrdersByDelegate() {
		GitVersion older = gitVersion("1.2.3");
		GitVersion newer = gitVersion("1.2.4");
		assertThat(older.compareTo(newer)).isLessThan(0);
		assertThat(newer.compareTo(older)).isGreaterThan(0);
		assertThat(older.compareTo(older)).isEqualTo(0);
	}

	@Test
	void compareToThrowsForNonGitVersion() {
		GitVersion a = gitVersion("1.2.4");
		assertThat((ArtifactVersion) a).isGreaterThan(ArtifactVersion.of("1.0.0"));
	}

	@Test
	void isPreviewDelegates() {
		assertThat(gitVersion("1.0.0-M1").isPreview()).isTrue();
		assertThat(gitVersion("1.0.0").isPreview()).isFalse();
	}

	@Test
	void isReleaseVersionDelegates() {
		assertThat(gitVersion("1.0.0").isReleaseVersion()).isTrue();
		assertThat(gitVersion("1.0.0-M1").isReleaseVersion()).isFalse();
	}

	@Test
	void equalsByDelegateAndSha() {
		assertThat(gitVersion(SHA, "1.2.3")).isEqualTo(gitVersion(SHA, "1.2.3"));
		assertThat(gitVersion(SHA, "1.2.3")).isNotEqualTo(gitVersion(SHA, "1.2.4"));
		assertThat(gitVersion(SHA, "1.2.3")).isNotEqualTo(gitVersion("1.2.3"));
	}

	@Test
	void hashCodeConsistentWithEquals() {
		assertThat(gitVersion(SHA, "1.2.3").hashCode()).isEqualTo(gitVersion(SHA, "1.2.3").hashCode());
	}

	static GitVersion gitVersion(String version) {
		return GitVersion.of(ArtifactVersion.of(version));
	}

	static GitVersion gitVersion(String sha, String version) {
		return GitVersion.of(sha, ArtifactVersion.of(version));
	}


}
