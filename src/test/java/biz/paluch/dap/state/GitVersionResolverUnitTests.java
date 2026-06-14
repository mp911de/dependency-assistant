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

package biz.paluch.dap.state;

import java.util.List;
import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.fixtures.ReleaseBuilder;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitVersionResolver}.
 *
 * @author Mark Paluch
 */
class GitVersionResolverUnitTests {

	static final String SHA_V1 = "aabbccddeeff00112233445566778899aabbccdd";

	static final String SHA_V1_FORK = "aabbccddffffffffffffffffffffffffffffffff";

	static final String SHA_V2 = "11223344556677889900aabbccddeeff11223344";

	static final ArtifactId ARTIFACT = ArtifactId.of("github:actions", "checkout");

	static final ArtifactId OTHER_ARTIFACT = ArtifactId.of("github:actions", "setup-node");

	private static Cache cacheWithReleases() {

		CachedArtifact artifact = ReleaseBuilder.cachedArtifact(ARTIFACT, releases -> releases
				.add("1.0.0", null, SHA_V1)
				.add("1.5.0", null, SHA_V1_FORK)
				.add("2.0.0", "2026-01-01", SHA_V2)
				.add("3.0.0"));

		Cache cache = new Cache();
		cache.addArtifacts(List.of(artifact));
		return cache;
	}

	@Test
	void resolvesBySha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, SHA_V2);

		assertThat(result).hasValueSatisfying(v -> {
			assertThat(v.getSha()).isEqualTo(SHA_V2);
			assertThat(v.toString()).isEqualTo("2.0.0");
		});
	}

	@Test
	void resolvesByPlainVersion() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, "2.0.0");

		assertThat(result).hasValueSatisfying(v -> {
			assertThat(v.getSha()).isEqualTo(SHA_V2);
			assertThat(v.toString()).isEqualTo("2.0.0");
		});
	}

	@Test
	void returnsEmptyForUnknownSha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, "0000000000000000000000000000000000000000");

		assertThat(result).isEmpty();
	}

	@Test
	void returnsEmptyForUnknownVersion() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, "99.0.0");

		assertThat(result).isEmpty();
	}

	@Test
	void resolvesUnambiguousAbbreviatedSha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, "11223344");

		assertThat(result).hasValueSatisfying(v -> assertThat(v.getSha()).isEqualTo(SHA_V2));
	}

	@Test
	void returnsEmptyWhenCacheEmpty() {

		GitVersionResolver resolver = new GitVersionResolver(new Cache());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, "1.0.0");

		assertThat(result).isEmpty();
	}

	@Test
	void fallsBackToCacheForExactTagMatch() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<ArtifactVersion> result = resolver.resolveCurrent(ARTIFACT, "2.0.0");

		assertThat(result).hasValueSatisfying(v -> {
			assertThat(v).isInstanceOf(GitVersion.class);
			assertThat(((GitVersion) v).getSha()).isEqualTo(SHA_V2);
		});
	}

	@Test
	void matchesShaPrefix() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<ArtifactVersion> result = resolver.resolveCurrent(ARTIFACT, "11223344");

		assertThat(result).hasValueSatisfying(v -> assertThat(((GitVersion) v).getSha()).isEqualTo(SHA_V2));
	}

	@Test
	void fallsBackToEmptyForAmbiguousPrefixThatIsNotParseable() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());

		Optional<ArtifactVersion> result = resolver.resolveCurrent(ARTIFACT, "aabbccdd");

		assertThat(result).isEmpty();
	}

	@Test
	void fallsBackToRawParseForUnknownRef() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<ArtifactVersion> result = resolver.resolveCurrent(ARTIFACT, "4.5.6");

		assertThat(result).hasValueSatisfying(v -> {
			assertThat(v).isNotInstanceOf(GitVersion.class);
			assertThat(v.toString()).isEqualTo("4.5.6");
		});
	}

	@Test
	void returnsEmptyForUnparseableUnknownRef() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<ArtifactVersion> result = resolver.resolveCurrent(ARTIFACT, "");

		assertThat(result).isEmpty();
	}

	@Test
	void ignoresCacheForDifferentArtifactId() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<ArtifactVersion> result = resolver.resolveCurrent(OTHER_ARTIFACT, "2.0.0");

		assertThat(result).hasValueSatisfying(v -> {
			assertThat(v).isNotInstanceOf(GitVersion.class);
			assertThat(v.toString()).isEqualTo("2.0.0");
		});
	}

}
