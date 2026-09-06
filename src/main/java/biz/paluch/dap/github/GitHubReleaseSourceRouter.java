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
 * Routes Git-backed dependencies to a GitHub release source for their host.
 * <p>Lookup uses {@link GitArtifactId#releaseSource()} without changing the
 * declared dependency identity. Strict mode ignores plain {@link ArtifactId}
 * values. Otherwise they identify repositories on the default GitHub host.
 *
 * @author Mark Paluch
 * @see GitHubReleases
 */
public class GitHubReleaseSourceRouter implements ReleaseSource, ReleaseSourceRegistry {

	private final Project project;

	private final GithubApiRequestExecutorFactory factory;

	private final boolean strict;

	private final Map<GithubServerPath, GitHubReleases> releaseSources = new ConcurrentHashMap<>();

	/**
	 * Create a router with host-specific sources resolved lazily.
	 * @param strict whether to ignore artifacts without an explicit Git identity.
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
	 * Return releases from the selected host.
	 * @return an empty sequence if the artifact is outside the configured domain or
	 * no executor is available.
	 * @throws IOException if the selected source cannot complete its request.
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
