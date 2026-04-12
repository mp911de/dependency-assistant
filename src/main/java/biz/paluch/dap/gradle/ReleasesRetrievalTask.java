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

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdates;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.support.DependencyCheckSupport;
import biz.paluch.dap.support.ReleasesRetrievalTaskSupport;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.progress.StepsProgressIndicator;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.jspecify.annotations.Nullable;

/**
 * Background task that refreshes the release state for each used dependency.
 *
 * @author Mark Paluch
 */
class ReleasesRetrievalTask extends ReleasesRetrievalTaskSupport {

	private final Project project;
	private final UpdateProjectState updateState;
	private volatile @Nullable DependencyUpdates updates;
	private volatile long duration;

	public ReleasesRetrievalTask(Project project) {
		super(project);
		this.project = project;
		this.updateState = new UpdateProjectState(project, DependencyAssistantService.getInstance(project));
	}

	@Override
	public void run(ProgressIndicator indicator) {

		long startNs = System.nanoTime();
		StepsProgressIndicator steps = new StepsProgressIndicator(indicator, 2);

		GradleSettings settings = GradleSettings.getInstance(project);
		Collection<GradleProjectSettings> linkedProjects = settings.getLinkedProjectsSettings();
		Set<RemoteRepository> repositories = new LinkedHashSet<>();
		for (GradleProjectSettings linkedProject : linkedProjects) {
			repositories
					.addAll(GradleUtils.getRepositoriesFromImportedProject(project, linkedProject.getExternalProjectPath()));
		}

		GradleDependencyCheckService service = GradleDependencyCheckService.getInstance(project);
		DependencyCollector allDependencies = ApplicationManager.getApplication()
				.runReadAction((Computable<DependencyCollector>) () -> updateState.getAllDependencies(steps));
		steps.nextStep();
		updates = service.getDependencyUpdates(indicator, () -> allDependencies,
				GradleProjectContext.getReleaseSources(repositories), DependencyCheckSupport.Consistency.NO_CACHE);
		duration = TimeoutUtil.getDurationMillis(startNs);
	}

	@Override
	protected @Nullable DependencyUpdates getUpdates() {
		return updates;
	}

	@Override
	protected long getDuration() {
		return duration;
	}

}
