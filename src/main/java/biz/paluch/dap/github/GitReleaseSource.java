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
import com.intellij.openapi.project.Project;
import org.jetbrains.plugins.github.api.GithubServerPath;

/**
 * @author Mark Paluch
 */
public class GitReleaseSource implements ReleaseSource {

	private final Project project;

	private final boolean strict;

	private final Map<String, GitHubReleaseSource> releaseSources = new ConcurrentHashMap<>();

	/**
	 * 
	 * @param project
	 * @param strict strict mode requires a {@link GitArtifactId} to be used.
	 * Non-strict mode will use {@link GithubServerPath#DEFAULT_HOST}.
	 */
	public GitReleaseSource(Project project, boolean strict) {
		this.project = project;
		this.strict = strict;
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId) {

		ReleaseSource releaseSource;
		ArtifactId id = artifactId;
		if (artifactId instanceof GitArtifactId gitArtifactId) {
			id = gitArtifactId.releaseSource();
			releaseSource = releaseSources.computeIfAbsent(gitArtifactId.host(),
					host -> GitHubReleaseSource.from(project, host));
		} else {
			if (strict) {
				return List.of();
			}

			releaseSource = releaseSources.computeIfAbsent(GithubServerPath.DEFAULT_HOST,
					host -> GitHubReleaseSource.from(project, host));
		}

		return releaseSource.getReleases(id);
	}

}
