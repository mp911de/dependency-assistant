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

import java.util.List;

import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.state.ProjectId;

/**
 * Wrapper for {@link ProjectBuildContext} that delegates all methods to a
 * {@link ProjectBuildContext delegate}.
 * @author Mark Paluch
 */
public class ProjectBuildContextWrapper implements ProjectBuildContext {

	private final ProjectBuildContext delegate;

	public ProjectBuildContextWrapper(ProjectBuildContext delegate) {
		this.delegate = delegate;
	}

	@Override
	public boolean isAvailable() {
		return delegate.isAvailable();
	}

	@Override
	public boolean isAbsent() {
		return delegate.isAbsent();
	}

	@Override
	public ProjectId getProjectId() {
		return delegate.getProjectId();
	}

	@Override
	public PackageSystem getPackageSystem() {
		return delegate.getPackageSystem();
	}

	@Override
	public Versioned getProjectVersion() {
		return delegate.getProjectVersion();
	}

	@Override
	public List<ReleaseSource> getReleaseSources() {
		return delegate.getReleaseSources();
	}

	@Override
	public String toString() {
		return delegate.toString();
	}

}
