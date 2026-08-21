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

package biz.paluch.dap.fixtures;

import java.util.Arrays;
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
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.QuickFix;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandExecutor;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jspecify.annotations.Nullable;

import static org.assertj.core.api.Assertions.*;

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

			InspectionManager manager = InspectionManager.getInstance(project);
			ProblemsHolder holder = new ProblemsHolder(manager, file, true);
			PsiElementVisitor visitor = inspection.buildVisitor(holder, true);
			SyntaxTraverser.psiTraverser(file).forEach(visitor::visitElement);
			return holder.getResults();
	}

	/**
	 * Run the quick fix with the given name, considering fixes of all given
	 * problems. Applies the first fix that matches the given {@code name}.
	 * @param file the file to inspect.
	 * @param name the quick fix name.
	 */
	public static void applyFix(PsiFile file, String name) {
		applyFix(file.getProject(), null, Inspections.inspect(file.getProject(), file), name);
	}

	/**
	 * Run the quick fix with the given name, considering fixes of all given
	 * problems. Applies the first fix that matches the given {@code name}.
	 * @param project the project owning the inspected file.
	 * @param problems the problems to search for a matching fix.
	 * @param name the quick fix name.
	 */
	public static void applyFix(Project project, List<ProblemDescriptor> problems, String name) {
		applyFix(project, null, problems, name);
	}

	/**
	 * Run the quick fix with the given name in the context of the given editor so
	 * caret placement of the fix takes effect.
	 *
	 * @param project the project owning the inspected file.
	 * @param editor the editor the fix runs in.
	 * @param problems the problems to search for a matching fix.
	 * @param name the quick fix name.
	 */
	public static void applyFix(Project project, @Nullable Editor editor, List<ProblemDescriptor> problems,
			String name) {

		assertThat(problems.stream().flatMap(problem -> Arrays.stream(getFixes(problem)))
				.map(QuickFix::getName))
						.as("quick fix").contains(name);

		for (ProblemDescriptor problem : problems) {
			for (QuickFix<?> fix : getFixes(problem)) {
				if (name.equals(fix.getName())) {
					invoke(project, problem, (LocalQuickFix) fix, editor);
					return;
				}
			}
		}
	}

	private static QuickFix<?>[] getFixes(ProblemDescriptor problem) {
		QuickFix<?>[] fixes = problem.getFixes();
		return fixes != null ? fixes : new QuickFix<?>[0];
	}

	/**
	 * Run a quick fix the way the platform would: {@link ModCommandQuickFix}es
	 * execute themselves without a surrounding write action, while classic fixes
	 * run inside one. With an editor, the {@link ModCommand} is executed
	 * interactively against it so navigation applies; per the
	 * {@link ModCommandExecutor} contract this happens inside a command but without
	 * a write lock.
	 */
	private static void invoke(Project project, ProblemDescriptor problem, LocalQuickFix fix,
			@Nullable Editor editor) {

		if (fix instanceof ModCommandQuickFix modFix) {

			if (editor == null) {
				modFix.applyFix(project, problem);
				return;
			}

			ModCommand command = modFix.perform(project, problem);
			CommandProcessor.getInstance().executeCommand(project, () -> ModCommandExecutor.getInstance()
					.executeInteractively(ActionContext.from(problem), command, editor), fix.getName(), null);
			return;
		}

		WriteCommandAction.runWriteCommandAction(project, () -> fix.applyFix(project, problem));
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
		DependencyCollector collector = new DependencyCollector(PackageSystem.MAVEN);
		collector.registerUsage(parsed.getArtifactId(), parsed.getVersion(), DeclarationSource.dependency(),
				versionSource);
		StateService.getInstance(project)
				.getProjectState(ProjectId.of("com.example", projectId))
				.setDependencies(collector);
	}

}
