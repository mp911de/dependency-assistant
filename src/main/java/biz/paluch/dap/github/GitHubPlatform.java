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

import java.io.IOException;
import java.net.URI;
import java.util.Locale;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.artifact.TagSource;
import biz.paluch.dap.metadata.IssueTracker;
import biz.paluch.dap.metadata.Platform;
import biz.paluch.dap.metadata.ProjectRepository;
import biz.paluch.dap.metadata.RepositoryConnection;
import biz.paluch.dap.metadata.RepositoryUrl;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jspecify.annotations.Nullable;

/**
 * {@link Platform} for GitHub ({@code github.com}) and, hint-aided, GitHub
 * Enterprise hosts.
 *
 * @author Mark Paluch
 */
public class GitHubPlatform implements Platform {

	/**
	 * GitHub owner names: alphanumerics with single inner hyphens, at most 39
	 * characters.
	 */
	private static final Pattern OWNER = Pattern.compile("[A-Za-z0-9](?:[A-Za-z0-9]|-(?=[A-Za-z0-9])){0,38}");

	/**
	 * GitHub repository names: alphanumerics, dot, hyphen, and underscore, at most
	 * 100 characters; {@code .} and {@code ..} are reserved.
	 */
	private static final Pattern REPOSITORY = Pattern.compile("(?!\\.\\.?$)[A-Za-z0-9._-]{1,100}");

	@Override
	public @Nullable RepositoryConnection detect(RepositoryUrl repositoryUrl, @Nullable String hint) {

		GitRepositoryMetadata metadata = GitRepositoryMetadata.flat(repositoryUrl.getRemote());
		if (metadata != null && OWNER.matcher(metadata.owner()).matches()
				&& REPOSITORY.matcher(metadata.repository()).matches()) {
			if (isGitHubHost(metadata.host().toLowerCase(Locale.ROOT))
					|| (hint != null && hint.toLowerCase(Locale.ROOT).contains("github"))) {
				return new GitHubRepositoryConnection(this, repositoryUrl, metadata);
			}
		}

		return null;
	}

	private boolean isGitHubHost(String host) {
		return host.equals("github.com") || host.equals("www.github.com");
	}

	static Url createBaseUrl(GitRepositoryMetadata metadata) {

		// an empty root path renders with the leading slash; a bare owner path would
		// concatenate host and owner without a separator
		return Urls.newUrl("https", metadata.host(), "")
				.resolve(metadata.owner()).resolve(metadata.repository());
	}

	@Override
	public @Nullable IssueTracker detectIssueTracker(RepositoryConnection repositoryConnection, @Nullable String hint) {

		if (repositoryConnection instanceof GitHubRepositoryConnection && (StringUtils.isEmpty(hint)
				|| hint.toLowerCase(Locale.ROOT).contains("github"))) {
			return new GitHubIssueTracker(repositoryConnection);
		}

		return null;
	}

	@Override
	public @Nullable ProjectRepository createRepository(Project project, RepositoryConnection connection) {
		if (connection instanceof GitHubRepositoryConnection ghrc) {
			return new GitHubRepository(project, ghrc.metadata());
		}
		return null;
	}

	static class GitHubRepository implements ProjectRepository {

		private final Project project;

		private final GitRepositoryMetadata metadata;

		private final Url repository;

		GitHubRepository(Project project, GitRepositoryMetadata metadata) {
			this.project = project;
			this.metadata = metadata;
			this.repository = createBaseUrl(metadata);
		}

		@Override
		public URI getUrl() {
			return URI.create(repository.toExternalForm());
		}

		@Override
		public IssueTracker getIssueTracker() {
			return new GitHubIssueTracker(repository.resolve("issues").toExternalForm());
		}

		@Override
		public URI getReleasesUrl() {
			return URI.create(repository.resolve("releases").toExternalForm());
		}

		@Override
		public URI getReleaseNotesUrl(String tagName) {
			Url resolved = repository.resolve("releases/tag");
			return URI.create(resolved.toExternalForm() + "/"
					+ ProjectRepository.encodePathSegment(tagName));
		}

		@Override
		public Sequence<String> getTags(ProgressIndicator indicator) throws IOException {
			ArtifactId artifactId = ArtifactId.of(metadata.owner(), metadata.repository());
			TagSource tagSource = getTagSource();
			return tagSource != null ? tagSource.getTags(artifactId, indicator) : Sequence.empty();
		}

		@Override
		public @Nullable TagSource getTagSource() {

			GithubApiRequestExecutorFactory factory = GithubApiRequestExecutorFactory.getInstance(project);
			GithubServerPath path = GithubApiRequestExecutorFactory.serverPath(metadata.host());
			GithubApiRequestExecutorFactory.ExecutorResult executor = factory.getExecutor(path);
			if (executor.hasExecutor()) {
				return new GitHubReleases(path, executor.getRequiredExecutor());
			}

			GithubApiRequestExecutorFactory.ExecutorResult maybeAnonymous = factory.getExecutor();
			if (maybeAnonymous.hasExecutor()) {
				return new GitHubReleases(path, maybeAnonymous.getRequiredExecutor());
			}

			return null;
		}

		@Override
		public String toString() {
			return metadata.toString();
		}

	}

	record GitHubRepositoryConnection(
			GitHubPlatform platform, RepositoryUrl repositoryUrl, GitRepositoryMetadata metadata)
			implements RepositoryConnection {

		@Override
		public String getKey() {
			return metadata.key();
		}

		@Override
		public String getUrl() {
			return createBaseUrl(metadata).toExternalForm();
		}

		@Override
		public @Nullable ProjectRepository createRepository(Project project) {
			return platform.createRepository(project, this);
		}
	}

	static class GitHubIssueTracker implements IssueTracker {

		private final URI issues;

		private final URI newIssue;

		GitHubIssueTracker(String issues) {
			Url url = issues.endsWith("/issues") ? Urls.newFromEncoded(issues)
					: Urls.newFromEncoded(issues).resolve("issues");

			this.issues = URI.create(url.toExternalForm());
			this.newIssue = URI.create(url.resolve("new").toExternalForm());
		}

		public GitHubIssueTracker(RepositoryConnection repositoryConnection) {
			this(repositoryConnection.getUrl());
		}

		@Override
		public URI getBaseUrl() {
			return issues;
		}

		@Override
		public URI getOpenIssuesUrl() {
			return issues;
		}

		@Override
		public URI getCreateNewIssueUrl(ArtifactId artifactId, ArtifactVersion version) {
			return newIssue;
		}

	}

}
