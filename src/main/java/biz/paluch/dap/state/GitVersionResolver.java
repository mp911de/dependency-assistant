/*
 * Copyright 2026-present the original author or authors.
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

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

/**
 * Resolve Git refs against cached releases.
 *
 * @author Mark Paluch
 */
public class GitVersionResolver {

	private static final Pattern VERSION_LINE_REF = Pattern.compile("v\\d+");

	private static final int MIN_SHA_PREFIX_LENGTH = 7;

	private final Cache cache;

	public GitVersionResolver(Cache cache) {
		this.cache = cache;
	}

	/**
	 * Resolve using cached releases only.
	 *
	 * @return the matched version, or {@link Versioned#unversioned()} if
	 * unresolved.
	 */
	public Versioned resolveStrict(ArtifactId artifactId, String lookupString) {

		Releases releases = cache.getReleases(artifactId);
		if (releases.isEmpty()) {
			return Versioned.unversioned();
		}

		GitVersion gitVersion = resolveVersion(lookupString, releases);
		return gitVersion != null ? Versioned.of(gitVersion) : Versioned.unversioned();
	}

	/**
	 * Resolve a ref using cached releases, retaining unmatched refs.
	 *
	 * @see #resolveLenient(Releases, String)
	 */
	public Versioned resolveLenient(ArtifactId artifactId, String rawRef) {
		return resolveLenient(cache.getReleases(artifactId), rawRef);
	}

	/**
	 * Resolve Git refs where possible, retaining unmatched refs and ordinary
	 * versions. SHA-style versions remain opaque Git refs.
	 */
	public Versioned resolveLenient(ArtifactVersion version, Releases releases) {

		if (version instanceof GitRef ref) {
			GitVersion gitVersion = resolveVersion(ref.getRef(), releases);
			if (gitVersion != null) {
				return Versioned.of(gitVersion);
			}
			return Versioned.of(ref);
		}

		if (RefStyle.from(version.toString()) == RefStyle.SHA) {
			return Versioned.of(new GitRef(version.toString()));
		}

		return Versioned.of(version);
	}

	/**
	 * Resolve against cached releases. Unmatched version text is parsed, while SHA
	 * and opaque refs remain Git refs. Empty input is unversioned.
	 */
	public Versioned resolveLenient(Releases releases, String rawRef) {

		if (StringUtils.isEmpty(rawRef)) {
			return Versioned.unversioned();
		}

		if (!releases.isEmpty()) {
			GitVersion gitVersion = resolveVersion(rawRef, releases);
			if (gitVersion != null) {
				return Versioned.of(gitVersion);
			}
		}

		if (RefStyle.from(rawRef) == RefStyle.SHA) {
			return Versioned.of(new GitRef(rawRef));
		}

		return ArtifactVersion.from(rawRef).map(Versioned::of).orElseGet(() -> Versioned.of(new GitRef(rawRef)));
	}

	/**
	 * Resolve the first declared version source against cached releases. Return
	 * {@code null} if absent or unresolved.
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
	 * <p>The canonical matching order is:
	 * <ol>
	 * <li>Exact rendered version match.</li>
	 * <li>Unique SHA prefix match, accepting abbreviated commit hashes of at least
	 * 7 characters and leaving ambiguous prefixes unresolved.</li>
	 * <li>Lowercase {@code vN} version-line ref match, selecting the newest stable
	 * SHA-backed release in that major version line.</li>
	 * <li>Parsed-version comparison fallback, matching cached Git versions whose
	 * delegate compares equal to the parsed ref.</li>
	 * </ol>
	 * <p>Broad rendered-version prefix matching is deliberately not part of this
	 * contract.
	 *
	 * @param versionRef the raw Git ref (tag, SHA, or version string) to match.
	 * @return the matching version, or {@literal null} if no unique match exists.
	 */
	public static @Nullable GitVersion resolveVersion(String versionRef, Iterable<Release> releases) {

		GitVersion exact = findExactVersion(versionRef, releases);
		if (exact != null) {
			return exact;
		}

		GitVersion sha = findShaPrefix(versionRef, releases);
		if (sha != null) {
			return sha;
		}

		GitVersion line = findVersionLineRef(versionRef, releases);
		if (line != null) {
			return line;
		}

		ArtifactVersion version = ArtifactVersion.from(versionRef).orElse(null);
		return version != null ? findComparableVersion(version, releases) : null;
	}

	private static @Nullable GitVersion findExactVersion(String versionRef, Iterable<Release> releases) {

		for (Release release : releases) {

			if (!(release.getVersion() instanceof GitVersion version)) {
				continue;
			}

			if (versionRef.equals(version.toString())) {
				return version;
			}
		}

		return null;
	}

	private static @Nullable GitVersion findShaPrefix(String versionRef, Iterable<Release> releases) {

		if (versionRef.length() < MIN_SHA_PREFIX_LENGTH) {
			return null;
		}

		Set<GitVersion> candidates = new HashSet<>();
		for (Release release : releases) {

			if (!(release.getVersion() instanceof GitVersion version)) {
				continue;
			}

			if (StringUtils.hasText(version.getSha()) && version.getSha().startsWith(versionRef)) {
				candidates.add(version);
			}
		}

		return candidates.size() == 1 ? candidates.iterator().next() : null;
	}

	private static @Nullable GitVersion findVersionLineRef(String versionRef, Iterable<Release> releases) {

		if (!VERSION_LINE_REF.matcher(versionRef).matches()) {
			return null;
		}

		ArtifactVersion line = ArtifactVersion.of(versionRef.substring(1));
		GitVersion newest = null;
		for (Release release : releases) {

			if (!(release.getVersion() instanceof GitVersion version)) {
				continue;
			}

			if (!version.hasSha() || !version.isReleaseVersion() || !version.hasSameMajor(line)) {
				continue;
			}

			if (newest == null || version.compareTo(newest) > 0) {
				newest = version;
			}
		}

		return newest;
	}

	private static @Nullable GitVersion findComparableVersion(ArtifactVersion versionRef,
			Iterable<Release> releases) {

		Set<GitVersion> candidates = new LinkedHashSet<>();
		for (Release release : releases) {

			if (!(release.getVersion() instanceof GitVersion version)) {
				continue;
			}

			if (version.canCompare(versionRef) && version.compareTo(versionRef) == 0) {
				candidates.add(version);
			}
		}

		Set<String> versions = new HashSet<>();
		for (GitVersion candidate : candidates) {
			versions.add(candidate.unwrap().toString());
		}

		return versions.size() == 1 ? candidates.iterator().next() : null;
	}

}
