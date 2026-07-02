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

package biz.paluch.dap.fixtures;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.editor.DependencyVersionDriftInspection;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.codeInspection.InspectionManager;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;

/**
 * Test support for local inspection integration tests across Maven and Gradle
 * build files.
 *
 * @author Mark Paluch
 */
public class Inspections {

	/**
	 * Run the {@link DependencyVersionDriftInspection} over the given file and
	 * collect its problems.
	 */
	public static List<ProblemDescriptor> inspect(Project project, PsiFile file) {
		return inspect(project, file, new DependencyVersionDriftInspection());
	}

	/**
	 * Run the given inspection over the given file and collect its problems.
	 */
	public static List<ProblemDescriptor> inspect(Project project, PsiFile file, LocalInspectionTool inspection) {
		return ReadAction.compute(() -> {
			InspectionManager manager = InspectionManager.getInstance(project);
			ProblemsHolder holder = new ProblemsHolder(manager, file, true);
			PsiElementVisitor visitor = inspection.buildVisitor(holder, true);
			SyntaxTraverser.psiTraverser(file).forEach(visitor::visitElement);
			return holder.getResults();
		});
	}

	/**
	 * Register a declared dependency usage for the given module in the project
	 * state.
	 */
	public static void registerDependency(Project project, String moduleId, String groupId, String artifactId,
			String version) {

		registerDependency(project, moduleId, groupId, artifactId, version, VersionSource.declared(version));
	}

	/**
	 * Register a dependency usage with an explicit version source for the given
	 * module in the project state.
	 */
	public static void registerDependency(Project project, String projectId, String groupId, String artifactId,
			String version, VersionSource versionSource) {

		DependencyCollector collector = new DependencyCollector();
		collector.registerUsage(ArtifactId.of(groupId, artifactId), ArtifactVersion.of(version),
				DeclarationSource.dependency(), versionSource);
		StateService.getInstance(project)
				.getProjectState(ProjectId.of("com.example", projectId))
				.setDependencies(collector, PackageSystem.MAVEN);
	}

}
