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
	 * state, using coordinates in {@code group:artifact:version} form.
	 *
	 * @param project the project whose state receives the dependency.
	 * @param moduleId the module identifier within the test project.
	 * @param coordinates the dependency coordinates.
	 */
	public static void registerDependency(Project project, String moduleId, String coordinates) {

		Coordinates parsed = Coordinates.of(coordinates);
		registerDependency(project, moduleId, coordinates, VersionSource.declared(parsed.getVersion().toString()));
	}

	/**
	 * Register a dependency usage with an explicit version source for the given
	 * module in the project state, using coordinates in
	 * {@code group:artifact:version} form.
	 *
	 * @param project the project whose state receives the dependency.
	 * @param projectId the module identifier within the test project.
	 * @param coordinates the dependency coordinates.
	 * @param versionSource the declaration source of the dependency version.
	 */
	public static void registerDependency(Project project, String projectId, String coordinates,
			VersionSource versionSource) {

		Coordinates parsed = Coordinates.of(coordinates);
		DependencyCollector collector = new DependencyCollector();
		collector.registerUsage(parsed.getArtifactId(), parsed.getVersion(), DeclarationSource.dependency(),
				versionSource);
		StateService.getInstance(project)
				.getProjectState(ProjectId.of("com.example", projectId))
				.setDependencies(collector, PackageSystem.MAVEN);
	}

}
