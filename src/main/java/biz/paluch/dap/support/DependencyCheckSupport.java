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
package biz.paluch.dap.support;

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.ProjectBuildContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdateOption;
import biz.paluch.dap.artifact.DependencyUpdates;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseResolver;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;

import org.springframework.util.CollectionUtils;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;

/**
 * Service that runs a dependency check for a project.
 *
 * @author Mark Paluch
 */
public abstract class DependencyCheckSupport {

	private final Project project;
	private final DependencyAssistantService service;

	public DependencyCheckSupport(Project project) {
		this.project = project;
		this.service = DependencyAssistantService.getInstance(project);
	}

	/**
	 * Runs the full version check for the Gradle build file currently open in the editor.
	 */
	protected DependencyUpdates getDependencyUpdates(ProgressIndicator indicator, PsiFile buildFile,
			ProjectBuildContext buildContext) {

		ProjectState projectState = service.getProjectState(buildContext.getProjectId());
		Cache cache = service.getCache();

		DependencyUpdates dependencyUpdates = getDependencyUpdates(indicator, () -> {
			DependencyCollector collector = ApplicationManager.getApplication()
					.runReadAction((Computable<DependencyCollector>) () -> collectArtifacts(buildFile));
			projectState.setDependencies(collector);
			return collector;
		}, buildContext.getReleaseSources(project));

		cache.recordUpdate();

		return dependencyUpdates;
	}

	/**
	 * Runs the full version check for the Gradle build file currently open in the editor.
	 */
	public DependencyUpdates getDependencyUpdates(ProgressIndicator indicator,
			Supplier<DependencyCollector> dependencyCollector, List<ReleaseSource> sources) {

		String projectName = project.getName();
		indicator.setText(MessageBundle.message("action.check.dependencies.progress.collecting", projectName));

		DependencyCollector collector = dependencyCollector.get();
		indicator.setFraction(0.3);

		if (collector.isEmpty()) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("action.check.dependencies.empty")));
		}

		Cache cache = service.getCache();

		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		ReleaseResolver resolver = new ReleaseResolver(sources, executor);
		List<DependencyUpdateOption> items = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		List<Future<ResolverResult>> futures = new ArrayList<>();
		List<Dependency> tasks = new ArrayList<>(collector.getDependencies());

		for (Dependency task : tasks) {
			futures.add(executor.submit(() -> {
				try {
					List<Release> releases = cache.getReleases(task.getArtifactId(), true);
					if (CollectionUtils.isEmpty(releases)) {
						releases = resolver.getReleases(task.getArtifactId(), task.getCurrentVersion());
						cache.putVersionOptions(task.getArtifactId(), releases);
					}
					return new ResolverResult(null, releases);
				} catch (Exception e) {
					return new ResolverResult(task.getArtifactId() + ": " + e.getMessage(), List.of());
				}
			}));
		}

		double total = futures.size();

		for (int i = 0; i < futures.size(); i++) {
			indicator.checkCanceled();

			indicator.setText2(tasks.get(i).getArtifactId().toString());
			ResolverResult res;
			try {
				res = futures.get(i).get();
			} catch (ExecutionException e) {
				String msg = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
				res = new ResolverResult(tasks.get(i).getArtifactId() + ": " + msg, List.of());
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				res = new ResolverResult(tasks.get(i).getArtifactId() + ": " + e.getMessage(), List.of());
			}
			if (res.error() != null) {
				errors.add(res.error());
			}

			double progress = (i + 1) / total;
			indicator.setFraction(0.5 + (progress / 2));

			items.add(new DependencyUpdateOption(tasks.get(i), res.releases()));
		}

		items.sort(Comparator.comparing(DependencyUpdateOption::getArtifactId, ArtifactId.BY_ARTIFACT_ID));
		return new DependencyUpdates(projectName, items, errors);
	}

	public abstract DependencyCollector collectArtifacts(PsiFile buildFile);

	private record ResolverResult(@Nullable String error, List<Release> releases) {

	}

}
