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

package biz.paluch.dap.metadata;

import java.net.URI;
import java.util.Locale;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.util.Url;
import com.intellij.util.Urls;
import org.jspecify.annotations.Nullable;

/**
 * Support class base for platforms whose browsable URLs are fixed path
 * templates below a canonical repository base URL. A subclass implements
 * {@link Platform#detect} and returns a {@link SimpleRepositoryConnection};
 * repository handle and issue tracker are rendered here from the configured
 * path templates.
 *
 * <p>Suited for platforms without tag fetching. Platforms with richer needs
 * such as tag sources or hint-aided host detection implement {@link Platform}
 * directly.
 *
 * @author Mark Paluch
 */
abstract class PlatformSupport implements Platform {

	private final @Nullable String hintToken;

	private final String releasesPath;

	private final @Nullable String releaseNotesPath;

	private final @Nullable String issuesPath;

	/**
	 * @param hintToken lower-case token that must appear in a non-empty declared
	 * hint for issue-tracker derivation; irrelevant when {@code issuesPath} is
	 * {@literal null}.
	 * @param releasesPath path below the base URL to the releases listing.
	 * @param releaseNotesPath path below the base URL to which the encoded tag name
	 * is appended; {@literal null} if the platform has no per-tag page.
	 * @param issuesPath path below the base URL to the issue tracker;
	 * {@literal null} if no tracker is derived.
	 */
	PlatformSupport(@Nullable String hintToken, String releasesPath, @Nullable String releaseNotesPath,
			@Nullable String issuesPath) {
		this.hintToken = hintToken;
		this.releasesPath = releasesPath;
		this.releaseNotesPath = releaseNotesPath;
		this.issuesPath = issuesPath;
	}

	@Override
	public @Nullable IssueTracker detectIssueTracker(RepositoryConnection repositoryConnection, @Nullable String hint) {

		if (issuesPath == null) {
			return null;
		}
		if (StringUtils.isEmpty(hint) || (hintToken != null && hint.toLowerCase(Locale.ROOT).contains(hintToken))) {
			return new SimpleIssueTracker(repositoryConnection.getUrl(), issuesPath);
		}

		return null;
	}

	@Override
	public @Nullable ProjectRepository createRepository(Project project, RepositoryConnection connection) {
		return new SimpleProjectRepository(connection.getUrl());
	}

	private static String resolve(String baseUrl, String path) {
		return baseUrl.endsWith("/") ? baseUrl + path : baseUrl + "/" + path;
	}

	record SimpleRepositoryConnection(PlatformSupport platform, String key, String url)
			implements RepositoryConnection {

		@Override
		public String getKey() {
			return key;
		}

		@Override
		public String getUrl() {
			return url;
		}

		@Override
		public @Nullable ProjectRepository createRepository(Project project) {
			return platform.createRepository(project, this);
		}

	}

	class SimpleProjectRepository implements ProjectRepository {

		private final String baseUrl;

		SimpleProjectRepository(String baseUrl) {
			this.baseUrl = baseUrl;
		}

		@Override
		public URI getUrl() {
			return URI.create(baseUrl);
		}

		@Override
		public @Nullable IssueTracker getIssueTracker() {
			return issuesPath != null ? new SimpleIssueTracker(baseUrl, issuesPath) : null;
		}

		@Override
		public URI getReleasesUrl() {
			return URI.create(resolve(baseUrl, releasesPath));
		}

		@Override
		public @Nullable URI getReleaseNotesUrl(String release) {

			if (releaseNotesPath == null) {
				return null;
			}

			return URI.create(resolve(baseUrl, releaseNotesPath) + "/"
					+ ProjectRepository.encodePathSegment(release));
		}

		@Override
		public String toString() {
			return baseUrl;
		}

	}

	static class SimpleIssueTracker implements IssueTracker {

		private final URI issues;

		private final URI newIssues;

		SimpleIssueTracker(String baseUrl, String issuesPath) {
			Url url = baseUrl.endsWith("/" + issuesPath) ? Urls.newFromEncoded(baseUrl)
					: Urls.newFromEncoded(baseUrl).resolve(issuesPath);
			this.issues = URI.create(url.toExternalForm());
			this.newIssues = URI.create(url.resolve("new").toExternalForm());
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
			return newIssues;
		}

	}

}
