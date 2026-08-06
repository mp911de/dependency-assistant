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
import java.net.URISyntaxException;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.util.HttpClientUtil;
import org.jspecify.annotations.Nullable;

/**
 * Browsable issue-tracker URLs of a dependency's upstream project.
 *
 * <p>A tracker either wraps the URL declared in the project metadata, created
 * through {@link #parse(String)}, or is derived from a detected
 * {@link RepositoryConnection} by the hosting {@link Platform}. Rendering the
 * URLs never probes the network.
 *
 * @author Mark Paluch
 * @see Platform#findIssueTracker
 * @see ProjectMetadata#getIssueTracker()
 */
public interface IssueTracker {

	/**
	 * Return the issue-tracker entry URL.
	 */
	URI getBaseUrl();

	/**
	 * Return the URL of the open-issues listing, the target for browsing existing
	 * issues.
	 */
	URI getOpenIssuesUrl();

	/**
	 * Return the URL for filing a new issue about the given artifact.
	 * @param artifactId the artifact the issue is about.
	 * @param version the artifact version the issue applies to.
	 * @return the new-issue page URL; trackers without a dedicated new-issue page.
	 */
	URI getCreateNewIssueUrl(ArtifactId artifactId, ArtifactVersion version);

	/**
	 * Create a tracker for a declared issue-tracker URL.
	 * <p>Declared values arrive from remote project metadata and are not guaranteed
	 * to be well-formed.
	 * @param url the declared issue-tracker URL.
	 * @return the tracker, or {@literal null} if the value is not a parseable URI.
	 */
	static @Nullable IssueTracker parse(String url) {
		try {
			URI uri = new URI(url);
			if (uri.getHost() == null || !HttpClientUtil.isBrowsable(uri)) {
				return null;
			}
			return new StaticIssueTracker(uri);
		} catch (URISyntaxException e) {
			return null;
		}
	}


}
