/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a snapshot of the License at
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.GitVersion;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GitVersionResolver}.
 *
 * @author Mark Paluch
 */
class GitVersionResolverUnitTests {

	private static final String SHA_V1 = "aabbccddeeff00112233445566778899aabbccdd";

	private static final String SHA_V1_FORK = "aabbccddffffffffffffffffffffffffffffffff";

	private static final String SHA_V2 = "11223344556677889900aabbccddeeff11223344";

	private static final ArtifactId ARTIFACT = ArtifactId.of("github:actions", "checkout");

	private static final ArtifactId OTHER_ARTIFACT = ArtifactId.of("github:actions", "setup-node");

	private static Cache cacheWithReleases() {

		Cache cache = new Cache();
		CachedArtifact artifact = new CachedArtifact(ARTIFACT);
		artifact.getReleases().add(new CachedRelease("1.0.0", null, SHA_V1));
		artifact.getReleases().add(new CachedRelease("1.5.0", null, SHA_V1_FORK));
		artifact.getReleases().add(new CachedRelease("2.0.0", "2026-01-01", SHA_V2));
		artifact.getReleases().add(new CachedRelease("3.0.0", null, null));
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

	@Test
	void skipsProjectStateWhenAbsent() {

		GitVersionResolver resolver = new GitVersionResolver(cacheWithReleases());
		Optional<ArtifactVersion> result = resolver.resolveCurrent(ARTIFACT, "2.0.0");

		assertThat(result).hasValueSatisfying(v -> assertThat(((GitVersion) v).getSha()).isEqualTo(SHA_V2));
	}

	private static class InMemoryProjectState implements ProjectState {

		private final Map<ArtifactId, Dependency> dependencies = new HashMap<>();

		void put(ArtifactId artifactId, Dependency dependency) {
			dependencies.put(artifactId, dependency);
		}

		@Override
		public @Nullable Dependency findDependency(ArtifactId artifactId) {
			return dependencies.get(artifactId);
		}

		@Override
		public void setDependencies(DependencyCollector collector) {
			throw new UnsupportedOperationException();
		}

		@Override
		public boolean hasDependencies() {
			return !dependencies.isEmpty();
		}

		@Override
		public void invalidateDependencies() {
			dependencies.clear();
		}

		@Override
		public @Nullable VersionProperty findProperty(String propertyName, Predicate<VersionProperty> filter) {
			return null;
		}

		@Override
		public @Nullable ProjectProperty findProjectProperty(String propertyName, Predicate<VersionProperty> filter) {
			return null;
		}

	}

}
