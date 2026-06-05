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
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Resolver for a Git ref (tag, SHA, branch) into an {@link ArtifactVersion}
 * using the canonical resolution order.
 *
 * <p>The {@link #resolveCurrent(ArtifactId, String) instance method} applies
 * the canonical chain: <strong>Project State</strong> current version, then
 * cached <strong>Releases</strong> matched by SHA or tag prefix (via the
 * existing cache-only resolver), then a best-effort
 * {@link ArtifactVersion#from(String)} parse of the raw ref.
 *
 * <p>The static {@link #resolveVersion(String, List)} entry point preserves the
 * cache-only matching used by dependency collectors and assistant context
 * helpers that already own a release list.
 *
 * @author Mark Paluch
 */
public class GitVersionResolver {

	private final Cache cache;

	private final @Nullable ProjectState projectState;

	/**
	 * Create a resolver using the {@link Cache} without project state.
	 * <p>The Project-State branch of {@link #resolveCurrent(ArtifactId, String)} is
	 * skipped when no project state is available.
	 * @param cache the shared release cache.
	 */
	public GitVersionResolver(Cache cache) {
		this(cache, null);
	}

	/**
	 * Create a resolver using the {@link Cache} and project state.
	 * @param cache the shared release cache.
	 * @param projectState the project state to consult first, or {@literal null} to
	 * skip the project-state branch.
	 */
	public GitVersionResolver(Cache cache, @Nullable ProjectState projectState) {
		this.cache = cache;
		this.projectState = projectState;
	}

	/**
	 * Resolve the given ref against cached releases for the given artifact.
	 * <p>This is the cache-only path used by dependency collectors that need to
	 * pair a declared SHA or tag with a known release. The Project-State branch is
	 * intentionally not consulted here.
	 * @param artifactId the artifact whose cached releases to inspect.
	 * @param lookupString the raw commit SHA (full or abbreviated) or version
	 * string.
	 * @return the resolved version, or {@link Optional#empty()} if unresolvable.
	 */
	public Optional<GitVersion> resolve(ArtifactId artifactId, String lookupString) {

		List<Release> releases = cache.getReleases(artifactId);
		if (releases.isEmpty()) {
			return Optional.empty();
		}

		return Optional.ofNullable(resolveVersion(lookupString, releases));
	}

	/**
	 * Resolve the current effective {@link ArtifactVersion} for the given artifact
	 * and raw ref using the canonical chain.
	 * <p>Resolution order:
	 * <ol>
	 * <li>If project state holds a dependency for {@code artifactId} with a
	 * non-{@literal null} current version, return that version.</li>
	 * <li>Otherwise consult the cached releases via
	 * {@link #resolveVersion(String, List)} and return the matched
	 * {@link GitVersion}.</li>
	 * <li>Otherwise return {@link ArtifactVersion#from(String)} of the raw
	 * ref.</li>
	 * </ol>
	 * @param artifactId the artifact to resolve; must not be {@literal null}.
	 * @param rawRef the raw Git ref (tag, SHA, branch); must not be
	 * {@literal null}.
	 * @return the resolved current version, or {@link Optional#empty()} when none
	 * of the branches yields a value.
	 */
	public Optional<ArtifactVersion> resolveCurrent(ArtifactId artifactId, String rawRef) {

		List<Release> releases = cache.getReleases(artifactId);
		GitVersion gitVersion = releases.isEmpty() ? null : resolveVersion(rawRef, releases);
		if (gitVersion != null) {
			return Optional.of(gitVersion);
		}

		return ArtifactVersion.from(rawRef);
	}

	public static @Nullable ArtifactVersion resolveVersion(VersionSource versionSource,
			List<Release> releases) {

		String source = versionSource.toString();
		if (!StringUtils.hasText(source)) {
			return null;
		}

		return resolveVersion(source, releases);
	}

	/**
	 * Resolve a {@link DeclaredDependency} to a {@link Dependency} using the
	 * cache-only matching on the supplied release list.
	 * <p>Shared delegation for build-tool integrations whose
	 * {@link biz.paluch.dap.ProjectDependencyContext#resolveDependency(DeclaredDependency, List)}
	 * implementation only needs cache matching.
	 * @param declaredDependency the declared dependency to resolve; must not be
	 * {@literal null}.
	 * @param releases the releases to inspect; must not be {@literal null}.
	 * @return the resolved dependency, or {@literal null} when the first version
	 * source is empty or the cache yields no unique match.
	 */
	public static @Nullable Dependency resolveDependency(DeclaredDependency declaredDependency,
			List<Release> releases) {

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
