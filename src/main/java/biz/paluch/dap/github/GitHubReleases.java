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
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactNotFoundException;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jetbrains.plugins.github.api.GithubApiRequest;
import org.jetbrains.plugins.github.api.GithubApiRequestExecutor;
import org.jetbrains.plugins.github.api.GithubServerPath;
import org.jetbrains.plugins.github.api.data.GithubResponsePage;
import org.jetbrains.plugins.github.api.util.GithubApiPagesLoader;
import org.jetbrains.plugins.github.exceptions.GithubStatusCodeException;
import org.jspecify.annotations.Nullable;

/**
 * {@link ReleaseSource} that fetches repository releases and tags from the
 * GitHub REST API via the bundled GitHub plugin's
 * {@link GithubApiRequestExecutor}.
 *
 * <p>The result is the union of two sources:
 * <ul>
 * <li>All GitHub Releases provide the publication date used for ordering and
 * display, and</li>
 * <li>the latest repository tags, up to the configured page size, provide
 * commit hashes for matching release entries and version candidates for
 * repositories that do not publish GitHub Releases.</li>
 * </ul>
 *
 * <p>Many projects do not publish GitHub Releases; the tag fallback ensures
 * those still expose update candidates. Tag entries without a release
 * contribute a version with {@literal null} date and the tag's commit SHA.
 * Release entries without a matching fetched tag contribute a version with
 * {@literal null} SHA.
 *
 * <p>Results are cached into the shared {@link biz.paluch.dap.state.Cache} as
 * {@link CachedRelease} entries with the SHA stored in the optional {@code sha}
 * field.
 *
 * @author Mark Paluch
 */
public class GitHubReleases implements ReleaseSource {

	private static final Logger LOG = Logger.getInstance(GitHubReleases.class);

	private static final int DEFAULT_TAGS_PAGE_SIZE = 100;

	private final GithubServerPath server;

	private final GithubApiRequestExecutor executor;

	private final int pageSize;

	/**
	 * Create a release source backed by the given executor.
	 *
	 * @param server the GitHub server (e.g.
	 * {@link GithubServerPath#DEFAULT_SERVER}).
	 * @param executor the API request executor.
	 */
	GitHubReleases(GithubServerPath server, GithubApiRequestExecutor executor) {
		this(server, executor, DEFAULT_TAGS_PAGE_SIZE);
	}

	GitHubReleases(GithubServerPath server, GithubApiRequestExecutor executor, int pageSize) {
		this.server = server;
		this.executor = executor;
		this.pageSize = pageSize;
	}

	@Override
	public String getId() {
		return "GitHub[%s]".formatted(server.getHost());
	}

	@Override
	public List<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {
		return fetchAllReleases(artifactId, indicator);
	}

	/**
	 * Fetch the union of GitHub Releases and the latest repository tags, then
	 * combine them into a deduplicated, version-keyed list of {@link Release}
	 * entries.
	 *
	 * @return the fetched releases, or an empty list if the fetch could not
	 * complete for a recoverable error.
	 * @throws ArtifactNotFoundException if the repository does not exist.
	 */
	public List<Release> fetchAllReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {
		List<IOException> exceptions = new ArrayList<>();
		Map<String, String> shaByTag = fetchTagShas(artifactId, indicator, exceptions::add);
		List<GitHubReleaseDto> releases = fetchReleases(artifactId, indicator, exceptions::add);

		if (shaByTag.isEmpty() && releases.isEmpty() && !exceptions.isEmpty()) {
			throw exceptions.getFirst();
		}

		return createReleases(releases, shaByTag);
	}

	private List<Release> createReleases(List<GitHubReleaseDto> releases, Map<String, String> shaByTag) {

		List<Release> result = new ArrayList<>(releases.size());
		Set<String> seenTags = new LinkedHashSet<>(releases.size());

		for (GitHubReleaseDto gitHubRelease : releases) {

			String tagName = gitHubRelease.tagName();
			seenTags.add(tagName);
			if (gitHubRelease.draft()) {
				continue;
			}

			String publishedAt = gitHubRelease.publishedAt();
			LocalDateTime releaseDate = StringUtils.hasText(publishedAt)
					? OffsetDateTime.parse(publishedAt).toLocalDateTime()
					: null;

			Release.tryFrom(tagName, releaseDate, shaByTag.get(tagName)).ifPresent(result::add);
		}

		shaByTag.forEach((tag, sha) -> {

			if (seenTags.contains(tag)) {
				return;
			}

			Release.tryFrom(tag, null, sha).ifPresent(result::add);
		});

		return result;
	}

	private Map<String, String> fetchTagShas(ArtifactId artifactId, ProgressIndicator indicator,
			Consumer<IOException> exceptionConsumer) {

		String url = "/repos/%s/%s/tags?per_page=%d".formatted(artifactId.groupId(), artifactId.artifactId(), pageSize);
		GithubApiRequest<GithubResponsePage<GitHubTagDto>> request = new GithubApiRequest.Get.JsonPage<>(
				server.toApiUrl() + url, GitHubTagDto.class, "application/vnd.github+json");

		try {
			List<GitHubTagDto> tags = executor.execute(indicator, request).getItems();
			Map<String, String> shaByTag = new LinkedHashMap<>();
			for (GitHubTagDto tag : tags) {

				String name = tag.name();
				if (StringUtils.isEmpty(name)) {
					continue;
				}

				GitHubCommitRefDto commit = tag.commit();
				if (commit != null) {
					shaByTag.putIfAbsent(name, commit.sha());
				}
			}
			return shaByTag;
		} catch (GithubStatusCodeException ex) {
			handleException(ex, artifactId, request.getUrl());
			return Map.of();
		} catch (IOException ex) {
			LOG.warn("[%s][%s] HTTP fetching of %s failed: %s".formatted(toString(artifactId),
					getId(), url, ex.getMessage()), ex);
			exceptionConsumer.accept(ex);
			return Map.of();
		}
	}

	private List<GitHubReleaseDto> fetchReleases(ArtifactId artifactId, ProgressIndicator indicator,
			Consumer<IOException> exceptionConsumer)
			throws IOException {

		String apiBase = server.toApiUrl();
		String url = "/repos/%s/%s/releases?per_page=%s".formatted(artifactId.groupId(), artifactId.artifactId(),
				pageSize);
		GithubApiRequest<GithubResponsePage<GitHubReleaseDto>> initial = new GithubApiRequest.Get.JsonPage<>(
				apiBase + url, GitHubReleaseDto.class, "application/vnd.github+json");
		GithubApiPagesLoader.Request<GitHubReleaseDto> pages = new GithubApiPagesLoader.Request<>(initial,
				nextUrl -> {
					if (!nextUrl.startsWith(apiBase)) {
						throw new IllegalStateException(
								"Pagination URL does not match expected server: %s".formatted(nextUrl));
					}
					return new GithubApiRequest.Get.JsonPage<>(nextUrl, GitHubReleaseDto.class,
							"application/vnd.github+json");
				});
		try {
			return GithubApiPagesLoader.loadAll(executor, indicator, pages);
		} catch (GithubStatusCodeException ex) {
			handleException(ex, artifactId, initial.getUrl());
			return List.of();
		} catch (IOException ex) {
			LOG.warn("[%s][%s] HTTP fetching of %s failed: %s".formatted(toString(artifactId),
					getId(), url, ex.getMessage()), ex);
			exceptionConsumer.accept(ex);
			return List.of();
		}
	}

	private void handleException(GithubStatusCodeException ex, ArtifactId artifactId, String url) {
		if (ex.getStatusCode() == 404) {
			LOG.debug("[%s][%s] HTTP Status %d: %s %s".formatted(toString(artifactId), getId(),
					ex.getStatusCode(), url, ex.getError()), ex);
			throw new ArtifactNotFoundException("Action repository not found", artifactId);
		}
		LOG.warn("[%s][%s] HTTP Status %d: %s %s".formatted(toString(artifactId), getId(),
				ex.getStatusCode(), url, ex.getError()), ex);
	}

	@Override
	public String toString(ArtifactId artifactId) {
		return GitHubUtils.toString(artifactId);
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GitHubReleases that)) {
			return false;
		}
		return Objects.equals(server, that.server);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(server);
	}

	/**
	 * DTO for the GitHub {@code /repos/{owner}/{repo}/tags} response items.
	 */
	record GitHubTagDto(@JsonProperty("name") @Nullable String name,
			@JsonProperty("commit") @Nullable GitHubCommitRefDto commit) {

	}

	/**
	 * DTO for the {@code commit} sub-object of a GitHub tag entry.
	 */
	record GitHubCommitRefDto(@JsonProperty("sha") String sha) {

	}

	/**
	 * DTO for the GitHub {@code /repos/{owner}/{repo}/releases} response items.
	 */
	record GitHubReleaseDto(@JsonProperty("tag_name") String tagName,
			@JsonProperty("published_at") String publishedAt,
			@JsonProperty("draft") boolean draft) {

	}

}
