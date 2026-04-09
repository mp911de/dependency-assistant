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

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdates;
import biz.paluch.dap.support.DependencyCheckSupport;

import java.util.List;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Service that runs a dependency version check for a Gradle project. Mirrors the structure of
 * {@code DependencyCheckService} for Maven but uses {@link GradleDependencyCollector} for artifact collection and
 * {@link GradleProjectContext} for repository sources.
 *
 * @author Mark Paluch
 */
public class GradleDependencyCheckService extends DependencyCheckSupport {

	private final Project project;

	public GradleDependencyCheckService(Project project) {
		super(project);
		this.project = project;
	}

	public static GradleDependencyCheckService getInstance(Project project) {
		GradleDependencyCheckService service = project.getServiceIfCreated(GradleDependencyCheckService.class);
		return service != null ? service : new GradleDependencyCheckService(project);
	}

	/**
	 * Runs the full version check for the Gradle build file currently open in the editor.
	 */
	public DependencyUpdates runCheck(ProgressIndicator indicator, PsiFile buildFile) {

		String projectName = project.getName();
		GradleProjectContext buildContext = GradleProjectContext.of(project, buildFile);

		if (!buildContext.isAvailable()) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("gradle.action.check.dependencies.noProject")));
		}

		return getDependencyUpdates(indicator, buildFile, buildContext, Consistency.CACHED);
	}

	/**
	 * Collects all dependency declarations from the given Gradle build file (and related files in the same project root).
	 */
	public DependencyCollector collectArtifacts(PsiFile buildFile) {
		return new GradleDependencyCollector(project).collect(buildFile);
	}

}
