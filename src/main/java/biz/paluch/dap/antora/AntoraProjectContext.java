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

package biz.paluch.dap.antora;

import java.util.List;

import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.github.GitHubReleaseSourceRouter;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.AbstractProjectBuildContext;
import biz.paluch.dap.support.ProjectBuildContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * {@link ProjectBuildContext} for a single Antora playbook file.
 *
 * <p>
 * Each {@code antora-playbook.yml} file produces its own context so that
 * dependency state for independent playbooks remains isolated. Release lookup
 * uses a strict {@link GitHubReleaseSourceRouter} keyed on the host parsed from the
 * declared {@code ui.bundle.url}.
 *
 * @author Mark Paluch
 */
class AntoraProjectContext extends AbstractProjectBuildContext {

	/**
	 * Key used to inject a test-scoped context into a PSI file's user data.
	 */
	static final Key<AntoraProjectContext> KEY = Key.create("AntoraProjectContext");

	private final Project project;

	/**
	 * Create a context for the given project identity and release sources.
	 *
	 * @param project
	 * @param projectId the project identity.
	 */
	AntoraProjectContext(Project project, ProjectId projectId) {
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
		return PackageSystem.OTHER;
	}

	@Override
	public List<ReleaseSource> getReleaseSources() {
		return List.of(new GitHubReleaseSourceRouter(project, true));
	}

}
