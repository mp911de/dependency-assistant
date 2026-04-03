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
package biz.paluch.dap;

import org.jetbrains.idea.maven.model.MavenId;
import org.jspecify.annotations.Nullable;

/**
 * Build-tool-agnostic project identity expressed as Maven coordinates ({@code groupId} and {@code artifactId}).
 *
 * @author Mark Paluch
 */
public record ProjectId(String groupId, String artifactId, @Nullable String buildFile) {

	public static ProjectId of(MavenId mavenId) {
		return of(mavenId.getGroupId(), mavenId.getArtifactId());
	}

	public static ProjectId of(String groupId, String artifactId) {
		return new ProjectId(groupId, artifactId, null);
	}

	public static ProjectId of(String groupId, String artifactId, String buildFile) {
		return new ProjectId(groupId, artifactId, buildFile);
	}

}
