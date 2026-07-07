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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.fixtures.ReleaseBuilder;
import biz.paluch.dap.github.TestGitHubReleases;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitVersionResolver}.
 *
 * @author Mark Paluch
 */
class GitVersionResolverUnitTests {

	static final String SHA_V2 = "11223344556677889900aabbccddeeff11223344";

	static final ArtifactId ARTIFACT = TestGitHubReleases.CHECKOUT;

	static final ArtifactId OTHER_ARTIFACT = TestGitHubReleases.SETUP_JAVA;

	private static Cache cacheWithReleases() {

		CachedArtifact artifact = ReleaseBuilder.artifact(ARTIFACT, releases -> releases
				.addWithSha("1.0.0", "aabbccddeeff00112233445566778899aabbccdd")
				.addWithSha("1.5.0", "aabbccddffffffffffffffffffffffffffffffff")
				.add("2.0.0", "2026-01-01", SHA_V2)
				.add("3.0.0"));

		Cache cache = new Cache();
		cache.addArtifacts(artifact);
		return cache;
	}

	private static Cache cacheWithCheckoutReleases() {

		Cache cache = new Cache();
		cache.addArtifacts(TestGitHubReleases.CHECKOUT);
		return cache;
	}

	@Test
	void resolvesBySha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveStrict(ARTIFACT, SHA_V2);

		assertGitVersion(result, SHA_V2, "2.0.0");
	}

	@Test
	void resolvesByPlainVersion() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveStrict(ARTIFACT, "2.0.0");

		assertGitVersion(result, SHA_V2, "2.0.0");
	}

	@Test
	void returnsUnversionedForUnknownSha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveStrict(ARTIFACT, "0000000000000000000000000000000000000000");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void returnsUnversionedForUnknownVersion() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveStrict(ARTIFACT, "99.0.0");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void resolvesUnambiguousAbbreviatedSha() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveStrict(ARTIFACT, "11223344");

		assertGitVersion(result, SHA_V2);
	}

	@Test
	void returnsUnversionedWhenCacheEmpty() {

		GitVersionResolver resolver = new GitVersionResolver(new Cache());
		Versioned result = resolver.resolveStrict(ARTIFACT, "1.0.0");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void fallsBackToCacheForExactTagMatch() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveLenient(ARTIFACT, "2.0.0");

		assertGitVersion(result, SHA_V2);
	}

	@Test
	void matchesShaPrefix() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveLenient(ARTIFACT, "11223344");

		assertGitVersion(result, SHA_V2);
	}

	@Test
	void shaPrefixShorterThanSevenCharactersDoesNotMatch() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());

		Versioned result = resolver.resolveStrict(ARTIFACT, "112233");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void keepsAmbiguousShaPrefixAsGitRef() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());

		Versioned result = resolver.resolveLenient(ARTIFACT, "aabbccdd");

		assertGitRef(result, "aabbccdd");
	}

	@Test
	void fallsBackToRawParseForUnknownRef() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveLenient(ARTIFACT, "4.5.6");

		assertThat(result.getVersion()).isNotInstanceOf(GitVersion.class);
		assertThat(result.getVersion()).hasToString("4.5.6");
	}

	@Test
	void keepsUnresolvedFullShaAsGitRef() {

		GitVersionResolver resolver = new GitVersionResolver(new Cache());

		Versioned result = resolver.resolveLenient(ARTIFACT, "1bcf9fb12cf4aa7d266a90ae39939e61372fe520");

		assertGitRef(result, "1bcf9fb12cf4aa7d266a90ae39939e61372fe520");
	}

	@Test
	void keepsUnparseableUnknownRefAsGitRef() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveLenient(ARTIFACT, "main");

		assertGitRef(result, "main");
	}

	@Test
	void returnsUnversionedForEmptyRef() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveLenient(ARTIFACT, "");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void ignoresCacheForDifferentArtifactId() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Versioned result = resolver.resolveLenient(OTHER_ARTIFACT, "2.0.0");

		assertThat(result.getVersion()).isNotInstanceOf(GitVersion.class);
		assertThat(result.getVersion()).hasToString("2.0.0");
	}

	@Test
	void exactTagMatchWinsBeforeVersionLineRef() {

		Releases releases = Releases.of(
				Release.of(GitVersion.of("f1185ce59f7757407fe6a5febb1e03e3dba2a530", ArtifactVersion.of("v4"))),
				Release.of(GitVersion.of(TestGitHubReleases.CHECKOUT_SHA_LATEST, ArtifactVersion.of("v4.2.0"))));

		GitVersion result = GitVersionResolver.resolveVersion("v4", releases);

		assertThat(result).isNotNull();
		assertThat(result.getSha()).isEqualTo("f1185ce59f7757407fe6a5febb1e03e3dba2a530");
		assertThat(result).hasToString("v4");
	}

	@Test
	void resolvesVersionLineRefToNewestStableShaBackedRelease() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithCheckoutReleases());

		Versioned result = resolver.resolveStrict(ARTIFACT, "v4");

		assertGitVersion(result, TestGitHubReleases.CHECKOUT_SHA_LATEST, "v4.2.0");
	}

	@Test
	void resolvesMultiDigitVersionLineRef() {

		Releases releases = Releases.just(Release.of(GitVersion.of(TestGitHubReleases.CHECKOUT_SHA_LATEST,
				ArtifactVersion.of("v444.1.0"))));

		GitVersion result = GitVersionResolver.resolveVersion("v444", releases);

		assertThat(result).isNotNull();
		assertThat(result).hasToString("v444.1.0");
	}

	@Test
	void versionLineRefIgnoresShaLessGitVersions() {

		Releases releases = Releases.of(
				Release.of(GitVersion.of(ArtifactVersion.of("v4.3.0"))),
				Release.of(GitVersion.of(TestGitHubReleases.CHECKOUT_SHA_LATEST, ArtifactVersion.of("v4.2.0"))));

		GitVersion result = GitVersionResolver.resolveVersion("v4", releases);

		assertThat(result).isNotNull();
		assertThat(result.getSha()).isEqualTo(TestGitHubReleases.CHECKOUT_SHA_LATEST);
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

		GitVersionResolver resolver = new GitVersionResolver(cacheWithCheckoutReleases());

		Versioned result = resolver.resolveStrict(ARTIFACT, "V4");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void bareMajorDoesNotMatchLine() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithCheckoutReleases());

		Versioned result = resolver.resolveStrict(ARTIFACT, "4");

		assertThat(result.isVersioned()).isFalse();
	}

	@Test
	void doesNotUseRenderedVersionPrefixMatching() {

		Releases releases = Releases.just(Release.of(GitVersion.of(TestGitHubReleases.CHECKOUT_SHA_LATEST,
				ArtifactVersion.of("v4.20.0"))));

		GitVersion result = GitVersionResolver.resolveVersion("v4.2", releases);

		assertThat(result).isNull();
	}

	@Test
	void compareFallbackMatchesUnprefixedVersionToPrefixedGitVersion() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithCheckoutReleases());

		Versioned result = resolver.resolveStrict(ARTIFACT, "4.2.0");

		assertGitVersion(result, TestGitHubReleases.CHECKOUT_SHA_LATEST, "v4.2.0");
	}

	@Test
	void exactPreviewRefResolvesBeforeStableLineSelection() {

		CachedArtifact artifact = ReleaseBuilder.artifact(ARTIFACT, releases -> releases
				.addWithSha("v4.3.0-M1", "e1185ce59f7757407fe6a5febb1e03e3dba2a530")
				.addWithSha("v4.2.0", TestGitHubReleases.CHECKOUT_SHA_LATEST));
		Cache cache = new Cache();
		cache.addArtifacts(artifact);
		GitVersionResolver resolver = new GitVersionResolver(cache);

		Versioned result = resolver.resolveStrict(ARTIFACT, "v4.3.0-M1");

		assertGitVersion(result, "e1185ce59f7757407fe6a5febb1e03e3dba2a530", "v4.3.0-M1");
	}

	private static void assertGitVersion(Versioned result, String sha) {

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class,
				version -> assertThat(version.getSha()).isEqualTo(sha));
	}

	private static void assertGitVersion(Versioned result, String sha, String version) {

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitVersion.class, gitVersion -> {
			assertThat(gitVersion.getSha()).isEqualTo(sha);
			assertThat(gitVersion).hasToString(version);
		});
	}

	private static void assertGitRef(Versioned result, String ref) {

		assertThat(result.getVersion()).isInstanceOfSatisfying(GitRef.class,
				gitRef -> assertThat(gitRef.getRef()).isEqualTo(ref));
	}

}
