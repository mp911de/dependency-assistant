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

package biz.paluch.dap.github;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.ReleaseSourceRegistry;
import biz.paluch.dap.util.Sequence;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jspecify.annotations.Nullable;

/**
 * {@link ReleaseSource} facade for dependencies whose versions are discovered
 * from Git repositories.
 *
 * <p>This type sits one level above {@link GitHubReleases}. It accepts the
 * common {@link ArtifactId} contract used by dependency collectors and routes
 * Git-backed artifacts to a host-specific GitHub release source. The actual API
 * access and release/tag aggregation remain the responsibility of
 * {@link GitHubReleases}. Account and executor resolution is performed through
 * {@link GithubApiRequestExecutorFactory}.
 *
 * <p>{@link GitArtifactId} carries the split between the dependency identity
 * declared in a build file and the repository coordinates used for release
 * lookup. This release source honors that split by querying
 * {@link GitArtifactId#releaseSource()} on {@link GitArtifactId#host()} while
 * leaving the declared artifact identity available to the rest of the
 * application for caching, display, and update grouping.
 *
 * <p>The {@link #GitHubReleaseSourceRouter(Project, boolean) strict mode}
 * controls how broad this source participates in release resolution. Strict
 * mode is intended for ecosystems that mix registry and Git-backed
 * dependencies, where only explicit {@link GitArtifactId} instances should be
 * resolved through GitHub. Non-strict mode treats plain {@link ArtifactId}
 * values as repository coordinates on the default GitHub host, which is
 * appropriate when the whole dependency domain is repository-based.
 *
 * @author Mark Paluch
 * @see GitArtifactId
 * @see GitHubReleases
 */
public class GitHubReleaseSourceRouter implements ReleaseSource, ReleaseSourceRegistry {

	private final Project project;

	private final GithubApiRequestExecutorFactory factory;

	private final boolean strict;

	private final Map<GithubServerPath, GitHubReleases> releaseSources = new ConcurrentHashMap<>();

	/**
	 * Create a routing release source for Git-backed dependency lookups.
	 * <p>The supplied project is used by host-specific delegates to resolve GitHub
	 * accounts and authentication. Delegates are created lazily and cached per
	 * host.
	 *
	 * @param project IntelliJ project used for GitHub account resolution.
	 * @param strict whether plain {@link ArtifactId} values should be ignored
	 * instead of being interpreted as repositories on
	 * {@link GithubServerPath#DEFAULT_HOST}.
	 */
	public GitHubReleaseSourceRouter(Project project, boolean strict) {
		this.project = project;
		this.strict = strict;
		this.factory = GithubApiRequestExecutorFactory.getInstance(project);
	}

	@Override
	public String getId() {
		return "GitHubReleaseSourceRouter";
	}

	/**
	 * Resolve releases for the repository represented by the given artifact.
	 * <p>When the artifact is a {@link GitArtifactId}, repository lookup uses its
	 * Git host and release-source coordinates. In non-strict mode, plain
	 * {@link ArtifactId} values are treated as owner/repository coordinates on the
	 * default GitHub host. In strict mode they are outside this source's domain and
	 * therefore yield no releases.
	 *
	 * @param artifactId dependency identity or Git-backed repository identity.
	 * @param indicator progress indicator used for cancellation.
	 * @return releases obtained from the selected GitHub release source, or an
	 * empty sequence when the artifact is outside strict mode or no executor is
	 * available.
	 * @throws IOException if the selected release source cannot complete its
	 * request.
	 */
	@Override
	public Sequence<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {
		return getReleaseSource(artifactId).getReleases(artifactId, indicator);
	}

	@Override
	public ReleaseSource getReleaseSource(ArtifactId artifactId) {

		ReleaseSource releaseSource;
		if (artifactId instanceof GitArtifactId gitArtifactId) {
			releaseSource = doGetReleaseSource(GithubApiRequestExecutorFactory.getServerPath(gitArtifactId.host()));
		} else if (strict) {
			releaseSource = null;
		} else {
			releaseSource = doGetReleaseSource(GithubServerPath.DEFAULT_SERVER);
		}

		return releaseSource == null ? EmptyReleaseSource.INSTANCE : releaseSource;
	}

	private @Nullable ReleaseSource doGetReleaseSource(GithubServerPath server) {

		GitHubReleases existing = releaseSources.get(server);
		if (existing != null) {
			return existing;
		}

		GithubApiRequestExecutorFactory.ExecutorResult executor = getExecutor(server);

		if (!executor.hasExecutor()) {
			return null;
		}

		return releaseSources.computeIfAbsent(server, it -> new GitHubReleases(it, executor.getRequiredExecutor()));
	}

	private GithubApiRequestExecutorFactory.ExecutorResult getExecutor(GithubServerPath server) {
		return server == GithubServerPath.DEFAULT_SERVER
				? factory.getExecutor()
				: factory.getExecutor(server);
	}

	@Override
	public String toString() {
		return getId();
	}

	enum EmptyReleaseSource implements ReleaseSource {

		INSTANCE;


		@Override
		public Sequence<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {
			return Sequence.empty();
		}

	}

}
