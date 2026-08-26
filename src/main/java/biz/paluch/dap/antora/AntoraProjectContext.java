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
import biz.paluch.dap.support.ProjectBuildContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Available {@link ProjectBuildContext} for one Antora playbook file.
 *
 * <p>Each {@code antora-playbook.yml} file produces its own path-keyed context,
 * keeping dependency state for independent playbooks isolated without an
 * imported build model. Release lookup uses a strict
 * {@link GitHubReleaseSourceRouter}: only
 * {@link biz.paluch.dap.artifact.GitArtifactId} values participate, and their
 * host selects the GitHub server.
 *
 * @author Mark Paluch
 */
class AntoraProjectContext extends AbstractProjectBuildContext {

	private final Project project;

	private AntoraProjectContext(Project project, ProjectId projectId) {
		super(projectId);
		this.project = project;
	}

	/**
	 * Create a context for the given project and anchor playbook file.
	 * @param project the IntelliJ project.
	 * @param anchor the Antora playbook file.
	 * @return a context isolated to the given playbook file, keyed by its path.
	 */
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
