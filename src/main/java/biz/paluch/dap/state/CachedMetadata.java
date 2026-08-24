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

package biz.paluch.dap.state;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Persistent project metadata captured for one artifact.
 *
 * <p>Every descriptive field is optional. An instance with no descriptive
 * values is a nothing-found marker whose retrieval time still participates in
 * metadata refresh back-off.
 *
 * @author Mark Paluch
 */
@Tag("projectMetadata")
public class CachedMetadata {

	/**
	 * Human-readable project name, or {@literal null} when none was reported by the
	 * metadata source.
	 */
	private @Nullable @Attribute String projectName;

	/**
	 * Human-readable project description, or {@literal null} when none was reported
	 * by the metadata source.
	 */
	private @Nullable @Attribute String projectDescription;

	private @Nullable @Attribute String repositoryUrl;

	private @Nullable @Attribute String issueTrackerUrl;

	/**
	 * Epoch-millisecond timestamp at which the inspection producing this metadata
	 * completed. Stamped by the cache write path.
	 */
	private @Attribute long retrievedAt;

	/**
	 * Create an empty metadata entry for XML deserialization.
	 */
	public CachedMetadata() {
	}

	/**
	 * Create a metadata entry from an inspection result.
	 *
	 * @param repositoryUrl the source repository URL, or {@literal null}.
	 * @param issueTrackerUrl the issue tracker URL, or {@literal null}.
	 * @param projectName the project name, or {@literal null}.
	 * @param projectDescription the project description, or {@literal null}.
	 * @return the unstamped metadata entry.
	 */
	public static CachedMetadata of(@Nullable String repositoryUrl, @Nullable String issueTrackerUrl,
			@Nullable String projectName,
			@Nullable String projectDescription) {

		CachedMetadata metadata = new CachedMetadata();
		metadata.projectName = projectName;
		metadata.projectDescription = projectDescription;
		metadata.repositoryUrl = repositoryUrl;
		metadata.issueTrackerUrl = issueTrackerUrl;

		return metadata;
	}

	public @Nullable String getProjectName() {
		return projectName;
	}

	public @Nullable String getProjectDescription() {
		return projectDescription;
	}

	public @Nullable String getRepositoryUrl() {
		return repositoryUrl;
	}

	public @Nullable String getIssueTrackerUrl() {
		return issueTrackerUrl;
	}

	/**
	 * Return when metadata inspection completed.
	 *
	 * @return the epoch-millisecond retrieval timestamp, or a small legacy retry
	 * counter for a nothing-found marker.
	 */
	public long getRetrievedAt() {
		return retrievedAt;
	}

	public void setRetrievedAt(long retrievedAt) {
		this.retrievedAt = retrievedAt;
	}

}
