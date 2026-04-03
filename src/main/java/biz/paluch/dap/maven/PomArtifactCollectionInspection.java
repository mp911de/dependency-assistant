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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;

import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.XmlElementVisitor;
import com.intellij.psi.xml.XmlDocument;

/**
 * Inspection that runs as part of the {@code DaemonCodeAnalyzer} analysis cycle for every {@code pom.xml}. It collects
 * declared artifact coordinates via {@link MavenDependencyCheckService#collectArtifacts} and stores the result in
 * {@link Cache} so that annotators, line markers, and completion contributors can read it without triggering additional
 * IO.
 */
public class PomArtifactCollectionInspection extends LocalInspectionTool {

	@Override
	public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {

		PsiFile file = holder.getFile();
		if (!MavenUtils.isMavenPomFile(file)) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		Project project = holder.getProject();
		MavenProjectContext mavenContext = MavenProjectContext.of(project, file);
		if (!mavenContext.isAvailable()) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		return new XmlElementVisitor() {

			@Override
			public void visitXmlDocument(XmlDocument document) {

				DependencyAssistantService state = DependencyAssistantService.getInstance(project);
				ProjectState projectState = state.getProjectState(mavenContext.getProjectId());

				if (!projectState.hasDependencies()) {

						DependencyCollector collector = MavenDependencyCheckService.getInstance(project)
							.collectArtifacts(file);
						projectState.setDependencies(collector);
				}
			}
		};
	}

}
