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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.Versioned;
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

	static final String SHA_V4 = "d1185ce59f7757407fe6a5febb1e03e3dba2a530";

	static final String SHA_V4_LINE = "f1185ce59f7757407fe6a5febb1e03e3dba2a530";

	static final String SHA_V4_PREVIEW = "e1185ce59f7757407fe6a5febb1e03e3dba2a530";

	static final ArtifactId ARTIFACT = ArtifactId.of("github:actions", "checkout");

	static final ArtifactId OTHER_ARTIFACT = ArtifactId.of("github:actions", "setup-node");

	private static Cache cacheWithReleases() {

		CachedArtifact artifact = ReleaseBuilder.artifact(ARTIFACT, releases -> releases
				.add("1.0.0", null, SHA_V1)
				.add("1.5.0", null, SHA_V1_FORK)
				.add("2.0.0", "2026-01-01", SHA_V2)
				.add("3.0.0"));

		Cache cache = new Cache();
		cache.addArtifacts(List.of(artifact));
		return cache;
	}

	private static Cache cacheWithPrefixedReleases() {

		CachedArtifact artifact = ReleaseBuilder.artifact(ARTIFACT, releases -> releases
				.add("v4.3.0-M1", "2026-03-01", SHA_V4_PREVIEW)
				.add("v4.2.0", "2026-02-01", SHA_V4)
				.add("v4.1.0", "2026-01-01", SHA_V4_LINE)
				.add("v4.0.0", "2025-12-01")
				.add("v3.6.0", "2025-11-01", SHA_V1));

		Cache cache = new Cache();
		cache.addArtifacts(List.of(artifact));
		return cache;
	}

	@Test
	void resolvesBySha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolve(ARTIFACT, SHA_V2);

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class, v -> {
			assertThat(v.getSha()).isEqualTo(SHA_V2);
			assertThat(v.toString()).isEqualTo("2.0.0");
		});
	}

	@Test
	void resolvesByPlainVersion() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolve(ARTIFACT, "2.0.0");

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class, v -> {
			assertThat(v.getSha()).isEqualTo(SHA_V2);
			assertThat(v.toString()).isEqualTo("2.0.0");
		});
	}

	@Test
	void returnsUnversionedForUnknownSha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolve(ARTIFACT, "0000000000000000000000000000000000000000");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void returnsUnversionedForUnknownVersion() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolve(ARTIFACT, "99.0.0");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void resolvesUnambiguousAbbreviatedSha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolve(ARTIFACT, "11223344");

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class,
				v -> assertThat(v.getSha()).isEqualTo(SHA_V2));
	}

	@Test
	void returnsUnversionedWhenCacheEmpty() {

		GitVersionResolver resolver = new GitVersionResolver(new Cache());
		Versioned result = resolver.resolve(ARTIFACT, "1.0.0");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void fallsBackToCacheForExactTagMatch() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveCurrent(ARTIFACT, "2.0.0");

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class,
				v -> assertThat(v.getSha()).isEqualTo(SHA_V2));
	}

	@Test
	void matchesShaPrefix() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveCurrent(ARTIFACT, "11223344");

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class,
				v -> assertThat(v.getSha()).isEqualTo(SHA_V2));
	}

	@Test
	void shaPrefixShorterThanSevenCharactersDoesNotMatch() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());

		Versioned result = resolver.resolve(ARTIFACT, "112233");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void fallsBackToUnversionedForAmbiguousPrefixThatIsNotParseable() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());

		Versioned result = resolver.resolveCurrent(ARTIFACT, "aabbccdd");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void fallsBackToRawParseForUnknownRef() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveCurrent(ARTIFACT, "4.5.6");

		assertThat(result.getVersion()).isNotInstanceOf(GitVersion.class);
		assertThat(result.getVersion()).hasToString("4.5.6");
	}

	@Test
	void returnsUnversionedForUnparseableUnknownRef() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveCurrent(ARTIFACT, "");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void ignoresCacheForDifferentArtifactId() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveCurrent(OTHER_ARTIFACT, "2.0.0");

		assertThat(result.getVersion()).isNotInstanceOf(GitVersion.class);
		assertThat(result.getVersion()).hasToString("2.0.0");
	}

	@Test
	void exactTagMatchWinsBeforeVersionLineRef() {

		Releases releases = Releases.of(
				Release.of(GitVersion.of(SHA_V4_LINE, ArtifactVersion.of("v4"))),
				Release.of(GitVersion.of(SHA_V4, ArtifactVersion.of("v4.2.0"))));

		GitVersion result = GitVersionResolver.resolveVersion("v4", releases);

		assertThat(result).isNotNull();
		assertThat(result.getSha()).isEqualTo(SHA_V4_LINE);
		assertThat(result).hasToString("v4");
	}

	@Test
	void resolvesVersionLineRefToNewestStableShaBackedRelease() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithPrefixedReleases());

		Versioned result = resolver.resolve(ARTIFACT, "v4");

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class, v -> {
			assertThat(v.getSha()).isEqualTo(SHA_V4);
			assertThat(v).hasToString("v4.2.0");
		});
	}

	@Test
	void resolvesMultiDigitVersionLineRef() {

		Releases releases = Releases.just(Release.of(GitVersion.of(SHA_V4, ArtifactVersion.of("v444.1.0"))));

		GitVersion result = GitVersionResolver.resolveVersion("v444", releases);

		assertThat(result).isNotNull();
		assertThat(result).hasToString("v444.1.0");
	}

	@Test
	void versionLineRefIgnoresShaLessGitVersions() {

		Releases releases = Releases.of(
				Release.of(GitVersion.of(ArtifactVersion.of("v4.3.0"))),
				Release.of(GitVersion.of(SHA_V4, ArtifactVersion.of("v4.2.0"))));

		GitVersion result = GitVersionResolver.resolveVersion("v4", releases);

		assertThat(result).isNotNull();
		assertThat(result.getSha()).isEqualTo(SHA_V4);
		assertThat(result).hasToString("v4.2.0");
	}

	@Test
	void exactAndCompareFallbackCanReturnShaLessGitVersion() {

		Releases releases = Releases.just(Release.of(GitVersion.of(ArtifactVersion.of("v4.2.0"))));

		GitVersion exact = GitVersionResolver.resolveVersion("v4.2.0", releases);
		GitVersion comparable = GitVersionResolver.resolveVersion("4.2.0", releases);

		assertThat(exact).isNotNull();
		assertThat(exact.hasSha()).isFalse();
		assertThat(comparable).isNotNull();
		assertThat(comparable.hasSha()).isFalse();
	}

	@Test
	void uppercaseVersionLineRefDoesNotMatchLine() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithPrefixedReleases());

		Versioned result = resolver.resolve(ARTIFACT, "V4");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void bareMajorDoesNotMatchLine() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithPrefixedReleases());

		Versioned result = resolver.resolve(ARTIFACT, "4");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void doesNotUseRenderedVersionPrefixMatching() {

		Releases releases = Releases.just(Release.of(GitVersion.of(SHA_V4, ArtifactVersion.of("v4.20.0"))));

		GitVersion result = GitVersionResolver.resolveVersion("v4.2", releases);

		assertThat(result).isNull();
	}

	@Test
	void compareFallbackMatchesUnprefixedVersionToPrefixedGitVersion() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithPrefixedReleases());

		Versioned result = resolver.resolve(ARTIFACT, "4.2.0");

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class, v -> {
			assertThat(v.getSha()).isEqualTo(SHA_V4);
			assertThat(v).hasToString("v4.2.0");
		});
	}

	@Test
	void exactPreviewRefResolvesBeforeStableLineSelection() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithPrefixedReleases());

		Versioned result = resolver.resolve(ARTIFACT, "v4.3.0-M1");

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class, v -> {
			assertThat(v.getSha()).isEqualTo(SHA_V4_PREVIEW);
			assertThat(v).hasToString("v4.3.0-M1");
		});
	}

}
