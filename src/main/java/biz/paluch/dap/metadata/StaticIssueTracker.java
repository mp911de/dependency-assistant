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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;

/**
 * Issue tracker that uses the same declared URL for browsing and issue
 * creation.
 *
 * @author Mark Paluch
 */
class StaticIssueTracker implements IssueTracker {

	private final URI uri;

	StaticIssueTracker(URI uri) {
		this.uri = uri;
	}

	@Override
	public URI getBaseUrl() {
		return uri;
	}

	@Override
	public URI getOpenIssuesUrl() {
		return uri;
	}

	@Override
	public URI getCreateNewIssueUrl(ArtifactId artifactId, ArtifactVersion version) {
		return uri;
	}

}
