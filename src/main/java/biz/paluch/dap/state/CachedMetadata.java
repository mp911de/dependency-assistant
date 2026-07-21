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

package biz.paluch.dap.state;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jspecify.annotations.Nullable;

/**
 * Persistent project metadata captured for one artifact.
 *
 * @author Mark Paluch
 */
@Tag("projectMetadata")
public class CachedMetadata {

	/**
	 * The human-readable project name declared by the artifact's own POM
	 * ({@code project/name}), or {@literal null} when no name was declared.
	 */
	private @Nullable @Attribute String projectName;

	/**
	 * The human-readable project name declared by the artifact's own POM
	 * ({@code project/name}), or {@literal null} when no name was declared.
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

	public long getRetrievedAt() {
		return retrievedAt;
	}

	public void setRetrievedAt(long retrievedAt) {
		this.retrievedAt = retrievedAt;
	}

}
