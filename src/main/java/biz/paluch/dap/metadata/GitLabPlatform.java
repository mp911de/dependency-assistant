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

package biz.paluch.dap.metadata;

import java.io.IOException;
import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.artifact.RemoteUrl;
import biz.paluch.dap.artifact.TagSource;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jspecify.annotations.Nullable;

/**
 * Recognizes {@code gitlab.com}, hosts following the {@code gitlab.*} naming
 * convention, and, hint-aided, self-hosted instances. Nested group paths are
 * real repository coordinates and are never truncated; path segments from the
 * {@code /-/} web-path separator onward are discarded instead, healing cached
 * URLs such as {@code group/sub/proj/-/tree/main} without re-inspection.
 *
 * <p>URL patterns: issues {@code /-/issues}, new issue {@code /-/issues/new},
 * releases {@code /-/releases}, release notes {@code /-/releases/{tag}}.
 *
 * @author Mark Paluch
 */
public class GitLabPlatform implements Platform {

	private static final String HINT_TOKEN = "gitlab";

	/**
	 * GitLab path segments per {@code Gitlab::PathRegex}: start with an
	 * alphanumeric, underscore, or dot, continue with alphanumerics, underscore,
	 * hyphen, and dot, at most 255 characters; {@code .} and {@code ..} are
	 * reserved.
	 */
	private static final Pattern PATH_SEGMENT = Pattern.compile("(?!\\.\\.?$)[A-Za-z0-9_.][A-Za-z0-9._-]{0,254}");

	@Override
	public @Nullable RepositoryConnection detect(RepositoryUrl repositoryUrl, @Nullable String hint) {

		if (repositoryUrl.getType() != RepositoryType.GIT) {
			return null;
		}
		GitRepositoryMetadata metadata = parseRepository(repositoryUrl.getRemote());
		if (metadata != null) {
			String host = metadata.host().toLowerCase(Locale.ROOT);
			if (host.startsWith("gitlab.") || matchesHint(hint)) {
				return new GitLabRepositoryConnection(this, repositoryUrl, metadata);
			}
		}

		return null;
	}

	static String createBaseUrl(GitRepositoryMetadata metadata) {
		return Urls.newUrl("https", metadata.host(), "")
				.resolve(metadata.owner()).resolve(metadata.repository()).toExternalForm();
	}

	@Override
	public @Nullable IssueTracker detectIssueTracker(RepositoryConnection repositoryConnection, @Nullable String hint) {

		if (repositoryConnection instanceof GitLabRepositoryConnection
				&& (StringUtils.isEmpty(hint) || matchesHint(hint))) {

			return new GitLabIssueTracker(repositoryConnection.getUrl());
		}

		return null;
	}

	@Override
	public @Nullable ProjectRepository createRepository(Project project, RepositoryConnection connection) {

		if (connection instanceof GitLabRepositoryConnection gitLab) {
			return new GitLabRepository(gitLab.metadata());
		}

		return null;
	}

	/**
	 * Assemble nested-group coordinates from the path segments: segments up to
	 * GitLab's {@code /-/} web-path separator are real coordinates, the last of
	 * them names the repository and everything before it forms the group path. A
	 * URL whose path is only a web path yields no repository and is not supported,
	 * nor is one carrying a coordinate outside GitLab's path format.
	 */
	private static @Nullable GitRepositoryMetadata parseRepository(RemoteUrl remoteUrl) {

		List<String> segments = remoteUrl.pathSegments();
		int separator = segments.indexOf("-");
		List<String> coordinates = separator == -1 ? segments : segments.subList(0, separator);
		if (coordinates.size() < 2) {
			return null;
		}

		for (String coordinate : coordinates) {

			if (!PATH_SEGMENT.matcher(coordinate).matches()) {
				return null;
			}
		}

		return new GitRepositoryMetadata(remoteUrl.host(),
				String.join("/", coordinates.subList(0, coordinates.size() - 1)),
				GitRepositoryMetadata.stripDotGit(coordinates.getLast()));
	}

	private static boolean matchesHint(@Nullable String hint) {
		return hint != null && hint.toLowerCase(Locale.ROOT).contains(HINT_TOKEN);
	}

	static class GitLabRepository implements ProjectRepository {

		private final GitRepositoryMetadata metadata;

		private final Url repository;

		GitLabRepository(GitRepositoryMetadata metadata) {
			this.metadata = metadata;
			this.repository = Urls.newFromEncoded(createBaseUrl(metadata));
		}

		@Override
		public URI getUrl() {
			return URI.create(repository.toExternalForm());
		}

		@Override
		public IssueTracker getIssueTracker() {
			return new GitLabIssueTracker(repository.toExternalForm());
		}

		@Override
		public URI getReleasesUrl() {
			return URI.create(repository.resolve("-/releases").toExternalForm());
		}

		@Override
		public URI getReleaseNotesUrl(String tagName) {

			return URI.create(
					repository.resolve("-/releases").toExternalForm() + "/"
							+ ProjectRepository.encodePathSegment(tagName));
		}

		@Override
		public Sequence<String> getTags(ProgressIndicator indicator) throws IOException {
			ArtifactId artifactId = ArtifactId.of(metadata.owner(), metadata.repository());
			return getTagSource().getTags(artifactId, indicator);
		}

		@Override
		public TagSource getTagSource() {
			return new GitLabReleases(metadata);
		}

		@Override
		public String toString() {
			return metadata.toString();
		}

	}

	record GitLabRepositoryConnection(
			GitLabPlatform platform, RepositoryUrl repositoryUrl, GitRepositoryMetadata metadata)
			implements RepositoryConnection {

		@Override
		public String getKey() {
			return metadata.key();
		}

		@Override
		public String getUrl() {
			return createBaseUrl(metadata);
		}

		@Override
		public @Nullable ProjectRepository createRepository(Project project) {
			return platform.createRepository(project, this);
		}
	}

	static class GitLabIssueTracker implements IssueTracker {

		private final URI issues;

		private final URI newIssue;

		GitLabIssueTracker(String baseUrl) {
			Url url = baseUrl.endsWith("/-/work_items") ? Urls.newFromEncoded(baseUrl)
					: Urls.newFromEncoded(baseUrl).resolve("-/work_items");

			this.issues = URI.create(url.toExternalForm());
			this.newIssue = URI.create(url.resolve("new").toExternalForm());
		}

		@Override
		public URI getBaseUrl() {
			return issues;
		}

		@Override
		public URI getOpenIssuesUrl() {
			return getBaseUrl();
		}

		@Override
		public URI getCreateNewIssueUrl(ArtifactId artifactId, ArtifactVersion version) {
			return newIssue;
		}

	}

}
