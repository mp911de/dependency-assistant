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
package biz.paluch.dap.maven;

import biz.paluch.dap.ProjectBuildContext;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.support.FlushStateOnSaveSupport;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Listener that invalidates and re-collects the dependency state for build files when they are saved or reloaded.
 *
 * @author Mark Paluch
 */
public class FlushStateOnSave extends FlushStateOnSaveSupport {

	@Override
	protected DependencyCollector collectDependencies(Project project, PsiFile buildFile) {
		return MavenDependencyCheckService.getInstance(project).collectArtifacts(buildFile);
	}

	@Override
	protected ProjectBuildContext getBuildContext(Project project, VirtualFile file) {
		return MavenProjectContext.of(project, file);
	}

}
