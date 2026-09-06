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
 * Browsable issue-tracker URLs for a dependency's upstream project.
 *
 * <p>Trackers come from a declared URL or a hosting {@link Platform}. Obtaining
 * the URLs requires no network access.
 *
 * @author Mark Paluch
 * @see Platform#findIssueTracker
 * @see ProjectMetadata#getIssueTracker()
 */
public interface IssueTracker {

	URI getBaseUrl();

	/**
	 * Return the URL for browsing existing issues.
	 */
	URI getOpenIssuesUrl();

	/**
	 * Return the URL for filing an issue about the given artifact version.
	 * <p>Trackers without an issue-creation page return their entry URL.
	 */
	URI getCreateNewIssueUrl(ArtifactId artifactId, ArtifactVersion version);

	/**
	 * Create a tracker for a declared absolute HTTP(S) URL.
	 * @return the tracker, or {@literal null} if the URL has invalid syntax, lacks
	 * a host, or uses a scheme other than HTTP(S).
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
