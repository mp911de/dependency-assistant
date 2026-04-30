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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Cache-only resolver that translates a workflow ref string into a
 * {@link GitVersion}.
 *
 * <p>The resolver never triggers remote API calls. It inspects the cached
 * {@link CachedRelease} entries for the given artifact and matches the ref
 * against:
 * <ul>
 * <li>the stored SHA-1, when the ref is a full 40-character hex string; or</li>
 * <li>the normalized version string (leading {@code v} stripped), when the ref
 * is a tag-style value.</li>
 * </ul>
 * Abbreviated SHAs (fewer than 40 characters) are intentionally ignored.
 * Unresolvable refs return {@link Optional#empty()} rather than throwing.
 *
 * @author Mark Paluch
 */
class GitVersionResolver {

	private final Cache cache;

	/**
	 * Create a resolver backed by the given cache.
	 * @param cache the shared release cache.
	 */
	GitVersionResolver(Cache cache) {
		this.cache = cache;
	}

	/**
	 * Resolve the given ref against cached releases for the given artifact.
	 * @param artifactId the artifact whose cached releases to inspect.
	 * @param rawVersion the raw ref string from the workflow file.
	 * @return the resolved version, or {@link Optional#empty()} if unresolvable.
	 */
	public Optional<GitVersion> resolve(ArtifactId artifactId, String rawVersion) {

		List<GitRelease> releases = getGitReleases(artifactId, cache);
		if (releases.isEmpty()) {
			return Optional.empty();
		}

		return Optional
				.ofNullable(resolveVersion(rawVersion, releases.stream().map(GitRelease::toGitVersion).toList()));
	}

	/**
	 * Convert a {@link CachedRelease} to a {@link GitRelease}.
	 * @param cachedRelease the cached release entry.
	 * @return the corresponding git release.
	 */
	static GitRelease toGitRelease(CachedRelease cachedRelease) {

		Release release = cachedRelease.toRelease();
		GitVersion gitVersion = GitVersion.of(cachedRelease.sha(), release.version());
		return new GitRelease(new Release(gitVersion, release.releaseDate()), cachedRelease.sha());
	}

	/**
	 * Return all cached {@link GitRelease} entries for the given artifact.
	 *
	 * @param artifactId the artifact to look up.
	 * @param cache the shared release cache.
	 * @return the list of git releases, possibly empty.
	 */
	static List<GitRelease> getGitReleases(ArtifactId artifactId, Cache cache) {
		return cache.getCachedReleases(artifactId).stream()
				.map(GitVersionResolver::toGitRelease)
				.toList();
	}

	/**
	 * Return all cached {@link ArtifactRelease} entries for the given artifact.
	 *
	 * @param artifactId the artifact to look up.
	 * @param cache the shared release cache.
	 * @return the list of git releases, possibly empty.
	 */
	static List<ArtifactRelease> getArtifactReleases(ArtifactId artifactId, Cache cache) {
		List<GitRelease> gitReleases = GitVersionResolver.getGitReleases(artifactId, cache);
		return gitReleases.stream()
				.sorted(Comparator.comparing(GitRelease::release))
				.map(it -> new ArtifactRelease(artifactId, it.release()))
				.collect(Collectors.toList());
	}

	public static @Nullable GitVersion resolve(String versionRef, List<Release> releases) {
		return resolveVersion(versionRef,
				releases.stream().map(Release::getVersion).filter(GitVersion.class::isInstance)
						.map(GitVersion.class::cast).toList());
	}

	public static @Nullable GitVersion resolveVersion(String versionRef, List<GitVersion> gitVersions) {

		List<GitVersion> candidates = new ArrayList<>();
		for (GitVersion version : gitVersions) {

			if (versionRef.equals(version.toString())) {
				return version;
			}

			if (version.toString().startsWith(versionRef)) {
				candidates.add(version);
			}

			if (StringUtils.hasText(version.getSha()) && version.getSha().startsWith(versionRef)) {
				candidates.add(version);
			}
		}

		return candidates.size() == 1 ? candidates.getFirst() : null;
	}

}
