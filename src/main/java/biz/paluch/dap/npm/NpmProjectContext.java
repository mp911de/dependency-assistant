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

package biz.paluch.dap.npm;

import java.util.List;

import biz.paluch.dap.ProjectBuildContext;
import biz.paluch.dap.ProjectId;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.github.GitReleaseSource;
import com.intellij.ide.plugins.PluginManagerCore;
import com.intellij.openapi.extensions.PluginId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * {@link ProjectBuildContext} for a single {@code package.json} file.
 *
 * <p>Each {@code package.json} file produces its own context so that dependent
 * declarations in different monorepo modules maintain independent dependency
 * state.
 * @author Mark Paluch
 */
class NpmProjectContext implements ProjectBuildContext {

	/**
	 * Key used to inject a test-scoped context into a PSI file's user data.
	 */
	static final Key<NpmProjectContext> KEY = Key.create("NpmProjectContext");

	private static final PluginId GITHUB = PluginId.getId("org.jetbrains.plugins.github");

	private static final boolean GITHUB_AVAILABLE = PluginManagerCore.isPluginInstalled(GITHUB)
			&& !PluginManagerCore.isDisabled(GITHUB);

	private final List<ReleaseSource> releaseSources;

	private final ProjectId projectId;

	NpmProjectContext(Project project, ProjectId projectId) {
		this.projectId = projectId;
		this.releaseSources = getReleaseSources(project);
	}

	public static List<ReleaseSource> getReleaseSources(Project project) {
		if (GITHUB_AVAILABLE) {
			return List.of(NpmRegistryReleaseSource.NPM_REGISTRY, new GitReleaseSource(project, true));
		}
		return List.of(NpmRegistryReleaseSource.NPM_REGISTRY);
	}

	/**
	 * Create a context for the given anchor file.
	 * @param anchor the {@code package.json} PSI file.
	 * @return the context to be used.
	 */
	public static NpmProjectContext of(PsiFile anchor) {
		return of(anchor.getProject(), anchor.getVirtualFile());
	}

	/**
	 * Create a context for the given project and anchor file.
	 * @param project the IntelliJ project.
	 * @param anchor the {@code package.json} virtual file.
	 * @return the context to be used.
	 */
	public static NpmProjectContext of(Project project, VirtualFile anchor) {
		ProjectId projectId = ProjectId.of("npm", anchor.getNameWithoutExtension(), anchor.getPath());
		return new NpmProjectContext(project, projectId);
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
	public List<ReleaseSource> getReleaseSources() {
		return releaseSources;
	}

}
