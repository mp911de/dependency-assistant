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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Resolver for a Git ref (tag, SHA, branch) into an {@link ArtifactVersion}
 * using the canonical resolution order.
 *
 * @author Mark Paluch
 */
public class GitVersionResolver {

	private final Cache cache;

	/**
	 * Create a resolver using the {@link Cache}.
	 * @param cache the shared release cache.
	 */
	public GitVersionResolver(Cache cache) {
		this.cache = cache;
	}

	/**
	 * Resolve the given ref against cached releases for the given artifact.
	 * <p>This is the cache-only path used by dependency collectors that need to
	 * pair a declared SHA or tag with a known release. The Project-State branch is
	 * intentionally not consulted here.
	 * @param artifactId the artifact whose cached releases to inspect.
	 * @param lookupString the raw commit SHA (full or abbreviated) or version
	 * string.
	 * @return the resolved version, or {@link Versioned#unversioned()} if
	 * unresolvable.
	 */
	public Versioned resolve(ArtifactId artifactId, String lookupString) {

		Releases releases = cache.getReleases(artifactId);
		if (releases.isEmpty()) {
			return Versioned.unversioned();
		}

		GitVersion gitVersion = resolveVersion(lookupString, releases);
		return gitVersion != null ? Versioned.of(gitVersion) : Versioned.unversioned();
	}

	/**
	 * Resolve the current effective {@link ArtifactVersion} for the given artifact
	 * and raw ref using the canonical chain.
	 * <p>Resolution order:
	 * <ol>
	 * <li>Cached releases.
	 * <li>Otherwise return {@link ArtifactVersion#from(String)} of the raw
	 * ref.</li>
	 * </ol>
	 * @param artifactId the artifact to resolve.
	 * @param rawRef the raw Git ref (tag, SHA, branch); must not be
	 * {@literal null}.
	 * @return the resolved current version, or {@link Optional#empty()} when none
	 * of the branches yields a value.
	 */
	public Optional<ArtifactVersion> resolveCurrent(ArtifactId artifactId, String rawRef) {

		Releases releases = cache.getReleases(artifactId);
		GitVersion gitVersion = releases.isEmpty() ? null : resolveVersion(rawRef, releases);
		if (gitVersion != null) {
			return Optional.of(gitVersion);
		}

		return ArtifactVersion.from(rawRef);
	}

	/**
	 * Resolve a {@link DeclaredDependency} to a {@link Dependency} using the
	 * cache-only matching on the supplied release list.
	 * @param declaredDependency the declared dependency to resolve; must not be
	 * {@literal null}.
	 * @param releases the releases to inspect.
	 * @return the resolved dependency, or {@literal null} when the first version
	 * source is empty or the cache yields no unique match.
	 */
	public static @Nullable Dependency resolveDependency(DeclaredDependency declaredDependency,
			Iterable<Release> releases) {

		if (declaredDependency.getVersionSources().isEmpty()) {
			return null;
		}

		String source = declaredDependency.getVersionSources().iterator().next().toString();
		if (!StringUtils.hasText(source)) {
			return null;
		}

		GitVersion gitVersion = resolveVersion(source, releases);
		return gitVersion != null ? Dependency.from(declaredDependency, gitVersion) : null;
	}

	/**
	 * Resolve a ref against the given release list.
	 * <p>Exact version matches win. Otherwise, a unique version or SHA prefix match
	 * is accepted; ambiguous prefixes are unresolved.
	 * @param versionRef the raw workflow ref
	 * @param releases the releases to inspect
	 * @return the matching version, or {@literal null} if no unique match exists
	 */
	public static @Nullable GitVersion resolveVersion(String versionRef, Iterable<Release> releases) {

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
			versions.add(candidate.unwrap().toString());
		}

		return versions.size() == 1 ? candidates.getFirst() : null;
	}

}
