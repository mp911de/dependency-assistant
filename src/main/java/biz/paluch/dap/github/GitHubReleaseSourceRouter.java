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

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GithubServerPath;

import org.springframework.util.ObjectUtils;

/**
 * {@link ReleaseSource} facade for dependencies whose versions are discovered
 * from Git repositories.
 *
 * <p>
 * This type sits one level above {@link GitHubReleases}. It accepts the
 * common {@link ArtifactId} contract used by dependency collectors and routes
 * Git-backed artifacts to a host-specific GitHub release source. The actual API
 * access, authentication, and release/tag aggregation remain the responsibility
 * of {@link GitHubReleases}.
 *
 * <p>
 * {@link GitArtifactId} carries the split between the dependency identity
 * declared in a build file and the repository coordinates used for release
 * lookup. This release source honors that split by querying
 * {@link GitArtifactId#releaseSource()} on {@link GitArtifactId#host()} while
 * leaving the declared artifact identity available to the rest of the
 * application for caching, display, and update grouping.
 *
 * <p>
 * The {@link #GitHubReleaseSourceRouter(Project, boolean) strict mode} controls how
 * broad this source participates in release resolution. Strict mode is intended
 * for ecosystems that mix registry and Git-backed dependencies, where only
 * explicit {@link GitArtifactId} instances should be resolved through GitHub.
 * Non-strict mode treats plain {@link ArtifactId} values as repository
 * coordinates on the default GitHub host, which is appropriate when the whole
 * dependency domain is repository-based.
 *
 * @author Mark Paluch
 * @see GitArtifactId
 * @see GitHubReleases
 */
public class GitHubReleaseSourceRouter implements ReleaseSource {

	private final Project project;

	private final boolean strict;

	private final Map<String, GitHubReleases> releaseSources = new ConcurrentHashMap<>();

	/**
	 * Create a routing release source for Git-backed dependency lookups.
	 * <p>
	 * The supplied project is used by host-specific delegates to resolve GitHub
	 * accounts and authentication. Delegates are created lazily and cached per
	 * host.
	 *
	 * @param project IntelliJ project used for GitHub account resolution.
	 * @param strict  whether plain {@link ArtifactId} values should be ignored
	 *                instead of being interpreted as repositories on
	 *                {@link GithubServerPath#DEFAULT_HOST}.
	 */
	public GitHubReleaseSourceRouter(Project project, boolean strict) {
		this.project = project;
		this.strict = strict;
	}

	/**
	 * Resolve releases for the repository represented by the given artifact.
	 * <p>
	 * When the artifact is a {@link GitArtifactId}, repository lookup uses its
	 * Git host and release-source coordinates. In non-strict mode, plain
	 * {@link ArtifactId} values are treated as owner/repository coordinates on the
	 * default GitHub host. In strict mode they are outside this source's domain and
	 * therefore yield no releases.
	 *
	 * @param artifactId dependency identity or Git-backed repository identity.
	 * @return releases obtained from the selected GitHub release source.
	 */
	@Override
	public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) {

		ReleaseSource releaseSource;
		ArtifactId id = artifactId;
		if (artifactId instanceof GitArtifactId gitArtifactId) {
			id = gitArtifactId.releaseSource();
			releaseSource = releaseSources.computeIfAbsent(gitArtifactId.host(),
					host -> GitHubReleases.from(project, host));
		}
		else {
			if (strict) {
				return List.of();
			}

			releaseSource = releaseSources.computeIfAbsent(GithubServerPath.DEFAULT_HOST,
					host -> GitHubReleases.from(project, host));
		}

		return releaseSource.getReleases(id, indicator);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GitHubReleaseSourceRouter that)) {
			return false;
		}
		if (strict != that.strict) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(project, that.project)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(releaseSources, that.releaseSources);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(project, strict, releaseSources);
	}
}
