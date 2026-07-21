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

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import biz.paluch.dap.artifact.ArtifactVersion;
import org.jspecify.annotations.Nullable;

/**
 * Resolved project metadata facade for one artifact, assembled by
 * {@link ProjectMetadataService} from the captured metadata and the detected
 * {@link Platform}.
 *
 * @author Mark Paluch
 * @see ProjectMetadataService
 */
public class ProjectMetadata {

	private static final ProjectMetadata ABSENT = new ProjectMetadata(null, null, null, null, List.of());

	private final @Nullable String projectName;

	private final @Nullable RepositoryConnection connection;

	private final @Nullable ProjectRepository repository;

	private final @Nullable IssueTracker issueTracker;

	private final Map<ArtifactVersion, String> tags = new TreeMap<>();

	private ProjectMetadata(@Nullable String projectName, @Nullable RepositoryConnection connection,
			@Nullable ProjectRepository repository,
			@Nullable IssueTracker issueTracker,
			List<String> tags) {
		this.projectName = projectName;
		this.connection = connection;
		this.repository = repository;
		this.issueTracker = issueTracker;

		for (String tag : tags) {
			// first tag per version wins: the listing arrives newest-first, so current
			// naming conventions take precedence over historic prefixes
			ArtifactVersion.fromTag(tag).ifPresent(it -> this.tags.putIfAbsent(it, tag));
		}
	}

	/**
	 * Return the facade for an artifact without captured metadata. All accessors
	 * return {@literal null}.
	 * @return the absent facade; guaranteed to be not {@literal null}.
	 */
	public static ProjectMetadata absent() {
		return ABSENT;
	}

	/**
	 * Create a facade from the resolved platform objects.
	 * @param projectName the project name.
	 * @param connection the detected repository connection.
	 * @param repository the detected repository.
	 * @param issueTracker the declared or derived issue tracker; can be
	 * {@literal null}.
	 * @param tags the cached repository tag names; the list is copied.
	 * @return the metadata facade, {@link #absent()} if nothing was resolved.
	 */
	public static ProjectMetadata from(@Nullable String projectName, @Nullable RepositoryConnection connection,
			@Nullable ProjectRepository repository, @Nullable IssueTracker issueTracker,
			List<String> tags) {

		if (projectName == null && connection == null && issueTracker == null && repository == null && tags.isEmpty()) {
			return ABSENT;
		}

		return new ProjectMetadata(projectName, connection, repository, issueTracker, tags);
	}

	public @Nullable String getProjectName() {
		return projectName;
	}

	/**
	 * Return the detected repository connection.
	 * @return the connection, or {@literal null} if no platform recognized the
	 * repository URL.
	 */
	public @Nullable RepositoryConnection getRepositoryConnection() {
		return connection;
	}

	/**
	 * Return the issue tracker, either declared by the project or derived from the
	 * repository connection.
	 * @return the issue tracker, or {@literal null} if none is known.
	 */
	public @Nullable IssueTracker getIssueTracker() {
		return issueTracker;
	}

	/**
	 * Return the browsable repository URL of the detected connection.
	 * @return the repository URL, or {@literal null} if no platform recognized the
	 * repository.
	 */
	public @Nullable String getRepositoryUrl() {
		return connection != null ? connection.getUrl() : null;
	}

	/**
	 * Return the issue-tracker base URL.
	 * @return the issue-tracker URL, or {@literal null} if no tracker is known.
	 */
	public @Nullable String getIssueTrackerUrl() {
		return issueTracker != null ? issueTracker.getBaseUrl().toString() : null;
	}

	public @Nullable URI findReleaseNotesUrl(ArtifactVersion version) {
		if (repository != null) {
			String tag = tags.get(version);
			if (tag != null) {
				return repository.getReleaseNotesUrl(tag);
			}
		}
		return null;
	}

	@Override
	public String toString() {

		if (this == ABSENT) {
			return "ProjectMetadata[absent]";
		}
		return "ProjectMetadata[connection=%s, issueTracker=%s, tags=%d]".formatted(connection, issueTracker,
				tags.size());
	}

}
