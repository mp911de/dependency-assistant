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

package biz.paluch.dap.support;

import biz.paluch.dap.state.ProjectId;

/**
 * Base class for {@link ProjectBuildContext} implementations.
 *
 * <p>The context is always {@link #isAvailable() available}; integrations that
 * need an absent state use a dedicated sentinel rather than this base.
 * Subclasses pass their identity and release sources to the constructor and
 * inherit the three view methods unchanged.
 *
 * @author Mark Paluch
 * @see ProjectBuildContext
 */
public abstract class AbstractProjectBuildContext implements ProjectBuildContext {

	private final ProjectId projectId;

	/**
	 * Create a context for the given identity and release sources.
	 * @param projectId the build-tool-agnostic identity.
	 */
	protected AbstractProjectBuildContext(ProjectId projectId) {
		this.projectId = projectId;
	}

	@Override
	public boolean isAvailable() {
		return true;
	}

	@Override
	public ProjectId getProjectId() {
		return projectId;
	}


	@Override
	public String toString() {
		return "%s,".formatted(projectId);
	}

}
