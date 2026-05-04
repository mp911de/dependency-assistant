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

package biz.paluch.dap.artifact;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
 * <li>the stored SHA, accepting an unambiguous prefix; or</li>
 * <li>the version string, also accepting an unambiguous prefix.</li>
 * </ul>
 * Ambiguous prefixes and unknown refs return {@link Optional#empty()} rather
 * than throwing.
 *
 * @author Mark Paluch
 */
public class GitVersionResolver {

	private final Cache cache;

	/**
	 * Create a resolver backed by the given cache.
	 * @param cache the shared release cache.
	 */
	public GitVersionResolver(Cache cache) {
		this.cache = cache;
	}

	/**
	 * Resolve the given ref against cached releases for the given artifact.
	 * @param artifactId the artifact whose cached releases to inspect.
	 * @param rawVersion the raw ref string from the GitHub Actions file.
	 * @return the resolved version, or {@link Optional#empty()} if unresolvable.
	 */
	public Optional<GitVersion> resolve(ArtifactId artifactId, String rawVersion) {

		List<Release> releases = cache.getReleases(artifactId);
		if (releases.isEmpty()) {
			return Optional.empty();
		}

		return Optional.ofNullable(resolveVersion(rawVersion, releases));
	}

	/**
	 * Resolve a ref against the given release list.
	 * <p>Exact version matches win. Otherwise, a unique version or SHA prefix match
	 * is accepted; ambiguous prefixes are unresolved.
	 * @param versionRef the raw workflow ref
	 * @param releases the releases to inspect
	 * @return the matching version, or {@code null} if no unique match exists
	 */
	public static @Nullable GitVersion resolveVersion(String versionRef, List<Release> releases) {

		List<GitVersion> candidates = new ArrayList<>();
		for (Release release : releases) {

			if (!(release.getVersion() instanceof GitVersion version)) {
				continue;
			}

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

		if (candidates.isEmpty()) {
			return null;
		}

		if (candidates.size() == 1) {
			return candidates.getFirst();
		}

		Set<String> versions = new HashSet<>();
		for (GitVersion candidate : candidates) {
			versions.add(candidate.getVersion().getVersion().toString());
		}

		return versions.size() == 1 ? candidates.getFirst() : null;
	}

}
