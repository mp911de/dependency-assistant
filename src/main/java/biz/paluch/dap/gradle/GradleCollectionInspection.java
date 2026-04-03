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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;

/**
 * Inspection that runs as part of the {@code DaemonCodeAnalyzer} cycle for every Gradle build file. Collects declared
 * dependency coordinates via {@link GradleDependencyCollector} and stores the result in the shared
 * {@link biz.paluch.dap.state.Cache} so that annotators, line markers, and completion contributors can read it without
 * triggering additional I/O.
 *
 * @author Mark Paluch
 */
public class GradleCollectionInspection extends LocalInspectionTool {

	@Override
	public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {

		PsiFile file = holder.getFile();
		if (!GradleUtils.isGradleFile(file)) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		Project project = holder.getProject();
		GradleProjectContext buildContext = GradleProjectContext.of(project, file);
		if (!buildContext.isAvailable()) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		return new PsiElementVisitor() {

			@Override
			public void visitFile(PsiFile psiFile) {

				DependencyAssistantService state = DependencyAssistantService.getInstance(project);
				ProjectState projectState = state.getProjectState(buildContext.getProjectId());

				if (!projectState.hasDependencies()) {
						DependencyCollector collector = GradleDependencyCheckService.getInstance(project)
							.collectArtifacts(file);
						projectState.setDependencies(collector);
				}
			}
		};
	}

}
