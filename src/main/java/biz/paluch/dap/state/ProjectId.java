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

import org.jspecify.annotations.Nullable;

/**
 * Build-tool-agnostic project identity expressed as coordinates
 * ({@code groupId}, {@code artifactId}, and {@code buildFile}).
 *
 * TODO: Explain group/artifact id in various contexts such as gradle having
 * multiple files per project or GHAactions not having a groupId for an action
 * file.
 * @author Mark Paluch
 */
public record ProjectId(String groupId, String artifactId, @Nullable String buildFile) {

	/**
	 * Create a project id without a build-file descriptor.
	 */
	public static ProjectId of(String groupId, String artifactId) {
		return new ProjectId(groupId, artifactId, null);
	}

	/**
	 * Create a project id with an optional build-file descriptor.
	 */
	public static ProjectId of(String groupId, String artifactId, String buildFile) {
		return new ProjectId(groupId, artifactId, buildFile);
	}

}
