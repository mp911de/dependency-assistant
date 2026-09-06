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

package biz.paluch.dap.antora;

import java.util.List;

import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.github.GitHubReleaseSourceRouter;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.AbstractProjectBuildContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Build context for one Antora playbook.
 * <p>Contexts are keyed by file path to isolate independent playbooks. Release
 * lookup accepts only Git identities and uses their host to select the server.
 *
 * @author Mark Paluch
 */
class AntoraProjectContext extends AbstractProjectBuildContext {

	private final Project project;

	private AntoraProjectContext(Project project, ProjectId projectId) {
		super(projectId);
		this.project = project;
	}

	static AntoraProjectContext of(Project project, VirtualFile anchor) {

		ProjectId projectId = ProjectId.of("antora", anchor.getNameWithoutExtension(), anchor.getPath());
		return new AntoraProjectContext(project, projectId);
	}

	@Override
	public PackageSystem getPackageSystem() {
		return PackageSystem.GITHUB;
	}

	@Override
	public List<ReleaseSource> getReleaseSources() {
		return List.of(new GitHubReleaseSourceRouter(project, true));
	}

}
