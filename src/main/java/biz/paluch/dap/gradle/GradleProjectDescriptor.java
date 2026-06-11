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

package biz.paluch.dap.gradle;

import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

class GradleProjectDescriptor {

	private final ProjectId projectId;

	private final @Nullable String version;

	private final String linkedProjectPath;

	private GradleProjectDescriptor(ProjectId projectId, @Nullable String version,
			String linkedProjectPath) {
		this.projectId = projectId;
		this.version = version;
		this.linkedProjectPath = linkedProjectPath;
	}

	public static GradleProjectDescriptor of(ProjectId projectId, @Nullable String version, String linkedProjectPath) {
		return new GradleProjectDescriptor(projectId, version, linkedProjectPath);
	}

	public ProjectId projectId() {
		return this.projectId;
	}

	public @Nullable String version() {
		return this.version;
	}

	public String linkedProjectPath() {
		return this.linkedProjectPath;
	}

	public boolean hasVersion() {
		return StringUtils.hasText(this.version)
		       && !"unspecified".equals(this.version);
	}

}
