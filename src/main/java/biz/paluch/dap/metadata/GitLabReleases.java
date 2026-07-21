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
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
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
import biz.paluch.dap.artifact.GitRepositoryMetadata;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.TagSource;
import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StringUtils;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.io.HttpRequests;
import org.jspecify.annotations.Nullable;

/**
 * {@link ReleaseSource} that fetches repository tags and releases from the
 * GitLab REST API v4 anonymously, for gitlab.com and self-hosted instances
 * alike.
 *
 * <p>The result is the union of two endpoints, mirroring the GitHub release
 * source:
 * <ul>
 * <li>{@code /api/v4/projects/{id}/releases} provides the publication date used
 * for ordering and display, and</li>
 * <li>{@code /api/v4/projects/{id}/repository/tags} provides commit SHAs for
 * matching release entries and version candidates for projects that do not
 * publish GitLab Releases.</li>
 * </ul>
 *
 * <p>Tag entries without a release contribute a version with {@literal null}
 * date and the tag's commit SHA. Release entries whose tag is beyond the
 * fetched tag page fall back to the release's own commit SHA. Both fetches are
 * capped at one page of {@value #PAGE_SIZE} items, the API maximum.
 *
 * <p>The project id is the URL-encoded full namespace path, preserving nested
 * group segments
 * ({@code gitlab-org%2Fsecurity-products%2Fanalyzers%2Fsemgrep}). Requests are
 * anonymous; public projects require no token and gitlab.com allows 500
 * unauthenticated requests per minute and IP. This adapter deliberately does
 * not depend on the {@code org.jetbrains.plugins.gitlab} plugin, which exposes
 * no tags or releases API.
 *
 * @author Mark Paluch
 */
public class GitLabReleases implements ReleaseSource, TagSource {

	private static final Logger LOG = Logger.getInstance(GitLabReleases.class);

	private static final int PAGE_SIZE = 100;

	private static final String ACCEPT_HEADER = "application/json";

	private static final ObjectMapper MAPPER = new ObjectMapper()
			.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);

	private final GitRepositoryMetadata repository;

	/**
	 * Create a release source for the given GitLab repository.
	 *
	 * @param repository the repository coordinates; the host selects the GitLab
	 * instance, the owner path may contain nested group segments.
	 */
	public GitLabReleases(GitRepositoryMetadata repository) {
		this.repository = repository;
	}

	@Override
	public String getId() {
		return "GitLab[%s]".formatted(repository.host());
	}


	@Override
	public Sequence<Release> getReleases(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {

		List<IOException> exceptions = new ArrayList<>();

		indicator.checkCanceled();
		Map<String, String> shaByTag = fetchTagShas(artifactId, exceptions::add);

		indicator.checkCanceled();
		List<GitLabReleaseDto> releases = fetchReleases(artifactId, exceptions::add);

		if (shaByTag.isEmpty() && releases.isEmpty() && !exceptions.isEmpty()) {
			throw exceptions.getFirst();
		}

		return Sequence.of(createReleases(releases, shaByTag));
	}

	@Override
	public Sequence<String> getTags(ArtifactId artifactId, ProgressIndicator indicator) throws IOException {

		List<IOException> exceptions = new ArrayList<>();

		indicator.checkCanceled();
		Map<String, String> shaByTag = fetchTagShas(artifactId, exceptions::add);

		if (!exceptions.isEmpty()) {
			throw exceptions.getFirst();
		}

		return Sequence.of(shaByTag.keySet());
	}

	/**
	 * Combine release entries and the tag SHA map into a deduplicated list of
	 * {@link Release} entries: releases contribute the publication date, tags
	 * contribute the commit SHA and fill in versions without a release.
	 */
	private List<Release> createReleases(List<GitLabReleaseDto> releases, Map<String, String> shaByTag) {

		List<Release> result = new ArrayList<>(releases.size());
		Set<String> seenTags = new LinkedHashSet<>(releases.size());

		for (GitLabReleaseDto release : releases) {

			String tagName = release.tagName();
			if (StringUtils.isEmpty(tagName)) {
				continue;
			}

			seenTags.add(tagName);
			if (release.upcomingRelease()) {
				continue;
			}

			String sha = shaByTag.get(tagName);
			if (sha == null && release.commit() != null) {
				sha = release.commit().id();
			}

			Release.tryFrom(tagName, parseReleaseDate(release.releasedAt()), sha).ifPresent(result::add);
		}

		shaByTag.forEach((tag, sha) -> {

			if (seenTags.contains(tag)) {
				return;
			}

			Release.tryFrom(tag, null, sha).ifPresent(result::add);
		});

		return result;
	}

	private Map<String, String> fetchTagShas(ArtifactId artifactId, Consumer<IOException> exceptionConsumer)
			throws IOException {

		String body = fetchUrl(artifactId, tagsUri(), exceptionConsumer);
		return body != null ? parseTagShas(body) : Map.of();
	}

	private List<GitLabReleaseDto> fetchReleases(ArtifactId artifactId, Consumer<IOException> exceptionConsumer)
			throws IOException {

		String body = fetchUrl(artifactId, releasesUri(), exceptionConsumer);
		return body != null ? parseReleaseEntries(body) : List.of();
	}

	private @Nullable String fetchUrl(ArtifactId artifactId, URI uri, Consumer<IOException> exceptionConsumer) {

		try {
			return HttpClientUtil.fetchUrl(uri, requestBuilder -> requestBuilder.accept(ACCEPT_HEADER));
		} catch (HttpRequests.HttpStatusException ex) {
			if (ex.getStatusCode() == 404) {
				LOG.debug("[%s][%s] HTTP Status %d: %s".formatted(toString(artifactId), getId(),
						ex.getStatusCode(), uri), ex);
				throw new ArtifactNotFoundException("GitLab project not found", artifactId);
			}
			LOG.warn("[%s][%s] HTTP Status %d: %s".formatted(toString(artifactId), getId(), ex.getStatusCode(),
					uri), ex);
			return null;
		} catch (IOException ex) {
			LOG.warn("[%s][%s] HTTP fetching of %s failed: %s".formatted(toString(artifactId), getId(), uri,
					ex.getMessage()), ex);
			exceptionConsumer.accept(ex);
			return null;
		}
	}

	/**
	 * Parse the {@code /repository/tags} response into a name-to-SHA map, skipping
	 * unnamed tags and tags without a commit.
	 */
	Map<String, String> parseTagShas(String body) throws IOException {

		List<GitLabTagDto> tags = List.of(MAPPER.readValue(body, GitLabTagDto[].class));
		Map<String, String> shaByTag = new LinkedHashMap<>();
		for (GitLabTagDto tag : tags) {

			String name = tag.name();
			if (StringUtils.isEmpty(name)) {
				continue;
			}

			GitLabCommitDto commit = tag.commit();
			if (commit != null && StringUtils.hasText(commit.id())) {
				shaByTag.putIfAbsent(name, commit.id());
			}
		}
		return shaByTag;
	}

	List<GitLabReleaseDto> parseReleaseEntries(String body) throws IOException {
		return List.of(MAPPER.readValue(body, GitLabReleaseDto[].class));
	}

	/**
	 * Parse and merge the two response bodies as {@link #getReleases} does after
	 * fetching, exposed for fixture-driven tests.
	 */
	List<Release> mergeReleases(String releasesBody, String tagsBody) throws IOException {
		return createReleases(parseReleaseEntries(releasesBody), parseTagShas(tagsBody));
	}

	URI tagsUri() {
		return URI.create("https://%s/api/v4/projects/%s/repository/tags?per_page=%d".formatted(repository.host(),
				encodedProjectId(), PAGE_SIZE));
	}

	URI releasesUri() {
		return URI.create("https://%s/api/v4/projects/%s/releases?per_page=%d".formatted(repository.host(),
				encodedProjectId(), PAGE_SIZE));
	}

	/**
	 * Return the URL-encoded full namespace path used as the REST project id, with
	 * nested group separators encoded as {@code %2F}.
	 */
	String encodedProjectId() {
		return URLEncoder.encode(repository.owner() + "/" + repository.repository(), StandardCharsets.UTF_8);
	}

	private static @Nullable LocalDateTime parseReleaseDate(@Nullable String releasedAt) {

		if (StringUtils.isEmpty(releasedAt)) {
			return null;
		}
		try {
			return OffsetDateTime.parse(releasedAt).toLocalDateTime();
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof GitLabReleases that)) {
			return false;
		}
		return Objects.equals(repository, that.repository);
	}

	@Override
	public int hashCode() {
		return Objects.hashCode(repository);
	}

	@Override
	public String toString() {
		return "%s %s/%s".formatted(getId(), repository.owner(), repository.repository());
	}

	/**
	 * DTO for the GitLab {@code /projects/{id}/repository/tags} response items.
	 */
	record GitLabTagDto(@JsonProperty("name") @Nullable String name,
			@JsonProperty("commit") @Nullable GitLabCommitDto commit) {

	}

	/**
	 * DTO for the {@code commit} sub-object of tag and release entries.
	 */
	record GitLabCommitDto(@JsonProperty("id") @Nullable String id) {

	}

	/**
	 * DTO for the GitLab {@code /projects/{id}/releases} response items.
	 */
	record GitLabReleaseDto(@JsonProperty("tag_name") @Nullable String tagName,
			@JsonProperty("released_at") @Nullable String releasedAt,
			@JsonProperty("upcoming_release") boolean upcomingRelease,
			@JsonProperty("commit") @Nullable GitLabCommitDto commit) {

	}

}
