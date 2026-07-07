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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.github.GitHubReleaseSourceRouter;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.AbstractProjectBuildContext;
import biz.paluch.dap.support.ProjectBuildContext;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * {@link ProjectBuildContext} for a single {@code package.json} file.
 *
 * <p>
 * Each {@code package.json} file produces its own context so that dependent
 * declarations in different monorepo modules maintain independent dependency
 * state.
 * @author Mark Paluch
 */
class NpmProjectContext extends AbstractProjectBuildContext {

	/**
	 * Key used to inject a test-scoped context into a PSI file's user data.
	 */
	static final Key<NpmProjectContext> KEY = Key.create("NpmProjectContext");

	private final Project project;

	private final Versioned projectVersion;

	NpmProjectContext(Project project, ProjectId projectId, Versioned projectVersion) {
		super(projectId);
		this.project = project;
		this.projectVersion = projectVersion;
	}

	public static List<ReleaseSource> getReleaseSources(Project project) {
		if (NpmUtils.GITHUB_AVAILABLE) {
			return List.of(new GitHubReleaseSourceRouter(project, true), NpmRegistry.NPM_REGISTRY);
		}
		return List.of(NpmRegistry.NPM_REGISTRY);
	}

	/**
	 * Create a context for the given anchor file.
	 * @param anchor the {@code package.json} PSI file.
	 * @return the build context for the anchor file's {@code package.json}.
	 */
	public static NpmProjectContext of(PsiFile anchor) {
		return of(anchor.getProject(), anchor.getVirtualFile(), resolveProjectVersion(anchor));
	}

	/**
	 * Create a context for the given project and anchor file.
	 * @param project the IntelliJ project.
	 * @param anchor the {@code package.json} virtual file.
	 * @return the build context for the given {@code package.json} file.
	 */
	public static NpmProjectContext of(Project project, VirtualFile anchor) {
		return of(project, anchor, Versioned.unversioned());
	}

	private static NpmProjectContext of(Project project, VirtualFile anchor, Versioned projectVersion) {
		ProjectId projectId = ProjectId.of("npm", anchor.getNameWithoutExtension(), anchor.getPath());
		return new NpmProjectContext(project, projectId, projectVersion);
	}

	private static Versioned resolveProjectVersion(PsiFile file) {

		if (!(file instanceof JsonFile jsonFile)) {
			return Versioned.unversioned();
		}

		JsonValue topLevelValue = jsonFile.getTopLevelValue();
		if (!(topLevelValue instanceof JsonObject root)) {
			return Versioned.unversioned();
		}

		JsonProperty version = root.findProperty("version");
		if (version == null || !(version.getValue() instanceof JsonStringLiteral literal)) {
			return Versioned.unversioned();
		}

		return ArtifactVersion.from(literal.getValue().trim()).map(Versioned::of).orElseGet(Versioned::unversioned);
	}

	@Override
	public PackageSystem getPackageSystem() {
		return PackageSystem.NPM;
	}

	@Override
	public List<ReleaseSource> getReleaseSources() {
		return getReleaseSources(project);
	}

	@Override
	public Versioned getProjectVersion() {
		return projectVersion;
	}

}
