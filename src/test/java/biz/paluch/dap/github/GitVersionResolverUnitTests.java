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

import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.state.GitVersionResolver;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitVersionResolver}.
 *
 * @author Mark Paluch
 */
class GitVersionResolverUnitTests {

	private static final String SHA_V1 = "aabbccddeeff00112233445566778899aabbccdd";

	private static final String SHA_V2 = "11223344556677889900aabbccddeeff11223344";

	private static final ArtifactId ARTIFACT = ArtifactId.of("github:actions", "checkout");

	private Cache cacheWithReleases() {

		Cache cache = new Cache();
		CachedArtifact artifact = new CachedArtifact(ARTIFACT);
		artifact.getReleases().add(new CachedRelease("1.0.0", null, SHA_V1));
		artifact.getReleases().add(new CachedRelease("2.0.0", "2026-01-01", SHA_V2));
		artifact.getReleases().add(new CachedRelease("3.0.0", null, null));
		cache.addArtifacts(java.util.List.of(artifact));
		return cache;
	}

	@Test
	void resolvesBySha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, SHA_V1);

		assertThat(result).isPresent();
		assertThat(result.get().getSha()).isEqualTo(SHA_V1);
		assertThat(result.get().toString()).isEqualTo("1.0.0");
	}

	@Test
	void resolvesByPlainVersion() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, "2.0.0");

		assertThat(result).isPresent();
		assertThat(result.get().getSha()).isEqualTo(SHA_V2);
		assertThat(result.get().toString()).isEqualTo("2.0.0");
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
	void returnsVersionForAbbreviatedSha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, "aabbccdd");

		assertThat(result).isPresent();
		assertThat(result.get().getSha()).isEqualTo(SHA_V1);
		assertThat(result.get().toString()).isEqualTo("1.0.0");
	}

	@Test
	void returnsEmptyWhenCacheEmpty() {

		GitVersionResolver resolver = new GitVersionResolver(new Cache());
		Optional<GitVersion> result = resolver.resolve(ARTIFACT, "1.0.0");

		assertThat(result).isEmpty();
	}

}
