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

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdates;
import biz.paluch.dap.support.DependencyCheckSupport;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Service that runs dependency version check against Maven repositories.
 */
public class MavenDependencyCheckService extends DependencyCheckSupport {

	private final Project project;
	private final MavenProjectsManager projectsManager;

	public MavenDependencyCheckService(Project project) {
		super(project);
		this.project = project;
		this.projectsManager = MavenProjectsManager.getInstance(project);
	}

	/**
	 * Returns the service instance for the given project.
	 */
	public static MavenDependencyCheckService getInstance(Project project) {
		return project.getService(MavenDependencyCheckService.class);
	}

	public DependencyUpdates runCheck(ProgressIndicator indicator, PsiFile pomFile)
			throws IOException {

		String projectName = project.getName();
		indicator.setText(MessageBundle.message("action.check.dependencies.progress.collecting", projectName));

		MavenProjectContext mavenContext = MavenProjectContext.of(project, pomFile);

		if (!mavenContext.isAvailable()) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("maven.action.check.dependencies.noEditor")));
		}

		return getDependencyUpdates(indicator, pomFile, mavenContext, Consistency.CACHED);
	}

	@Override
	public DependencyCollector collectArtifacts(PsiFile pomFile) {

		MavenProject currentProject = projectsManager.findProject(pomFile.getVirtualFile());
		MavenProperties mavenProperties = new MavenProperties(project, projectsManager);
		Map<String, String> allProperties = mavenProperties.getAllProperties(currentProject);
		MavenDependencyCollector collector = new MavenDependencyCollector(getCache(), allProperties);

		return collector.collect(pomFile);
	}

}
