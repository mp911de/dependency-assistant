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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.*;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.state.ProjectState;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jspecify.annotations.Nullable;

import org.springframework.util.CollectionUtils;

/**
 * Service that runs a dependency check for a project.
 *
 * @author Mark Paluch
 */
class DependencyCheck {

	private final Project project;

	private final DependencyAssistantService service;

	/**
	 * Create a dependency check bound to the given project.
	 */
	public DependencyCheck(Project project) {
		this.project = project;
		this.service = DependencyAssistantService.getInstance(project);
	}

	/**
	 * Return the cached consistency.
	 */
	public static Consistency cached() {
		return Consistency.CACHED;
	}

	/**
	 * Return the no-cache-usage consistency.
	 */
	public static Consistency bypassCache() {
		return Consistency.NO_CACHE;
	}

	/**
	 * Return the cache used by this dependency check.
	 */
	public Cache getCache() {
		return service.getCache();
	}

	/**
	 * Run the full dependency update check for the given integration context.
	 *
	 * @param indicator the progress indicator.
	 * @param context the integration context to scan.
	 * @param consistency the release-cache consistency to use.
	 * @return the dependency update result.
	 */
	public DependencyUpdates getDependencyUpdates(ProgressIndicator indicator, ProjectDependencyContext context,
			Consistency consistency) {

		return ApplicationManager.getApplication().runReadAction((Computable<DependencyUpdates>) () -> {
			ProjectState projectState = service.getProjectState(context.getProjectId());
			return getDependencyUpdates(indicator, context, () -> {
				DependencyCollector collector = context.scanDependencies(indicator);
				projectState.setDependencies(collector);
				return collector;
			}, consistency);
		});
	}

	/**
	 * Scan the build file associated with the project context for all artifacts.
	 */
	public List<ArtifactRefreshCandidate> collectDependencies(ProgressIndicator indicator,
			DependencyAssistant assistant) {

		indicator.setText(
				MessageBundle.message("action.index-dependencies.indexing.assistant", assistant.getDisplayName()));
		DependencyCollector collector = assistant.getAllDependencies(project, indicator);
		List<ArtifactId> usages = collector.getUsages().stream().map(Dependency::getArtifactId).toList();
		List<ArtifactId> declarations = collector.getDeclarations().stream().map(DeclaredDependency::getArtifactId)
				.toList();

		Set<ArtifactId> unique = new LinkedHashSet<>(declarations.size() + usages.size());
		unique.addAll(usages);
		unique.addAll(declarations);

		Collection<ReleaseSource> releaseSources = collector.getReleaseSources();
		return unique.stream().map(it -> new ArtifactRefreshCandidate(it, releaseSources)).toList();
	}

	/**
	 * Runs the full version check for a build file.
	 */
	private DependencyUpdates getDependencyUpdates(ProgressIndicator indicator,
			ProjectDependencyContext context, Supplier<DependencyCollector> dependencyCollector,
			Consistency consistency) {

		String projectName = project.getName();
		indicator.setText(MessageBundle.message("action.check.dependencies.progress.collecting", projectName));

		DependencyCollector collector = dependencyCollector.get();
		indicator.setFraction(0.3);

		if (collector.isEmpty() && collector.getDeclarations().isEmpty()) {
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("action.check.dependencies.empty")));
		}

		Cache cache = service.getCache();

		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		Collection<ReleaseSource> sources = collector.getReleaseSources();
		ReleaseResolver resolver = new ReleaseResolver(sources, executor);
		List<DependencyUpdateOption> items = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		List<Future<ResolverResult>> futures = new ArrayList<>();
		List<DeclaredDependency> tasks = new ArrayList<>(collector.getUsages());

		if (collector.isEmpty()) {
			tasks.addAll(collector.getDeclarations());
		}

		for (DeclaredDependency declaredDependency : tasks) {
			futures.add(executor.submit(() -> fetchReleases(indicator, declaredDependency.getArtifactId(), sources,
					executor, cache, consistency)));
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

			if (tasks.get(i) instanceof Dependency d) {
				items.add(new DependencyUpdateOption(d, res.releases()));
			} else if (context.resolveDependency(tasks.get(i), res.releases()) instanceof Dependency d) {
				items.add(new DependencyUpdateOption(d, res.releases()));
			}
		}

		cache.recordUpdate();

		items.sort(Comparator.comparing(DependencyUpdateOption::getArtifactId, ArtifactId.BY_ARTIFACT_ID));
		return new DependencyUpdates(projectName, items, errors);
	}


	public DependencyUpdates updateReleaseMetadata(ProgressIndicator indicator,
			List<ArtifactRefreshCandidate> candidates,
			Consistency consistency) {

		Cache cache = service.getCache();
		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		List<DependencyUpdateOption> items = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		List<Future<ResolverResult>> futures = new ArrayList<>();
		List<DeclaredDependency> tasks = new ArrayList<>();

		Map<ArtifactId, ArtifactRefreshCandidate> consolidated = new LinkedHashMap<>();
		for (ArtifactRefreshCandidate candidate : candidates) {
			consolidated
					.computeIfAbsent(candidate.artifactId(), it -> new ArtifactRefreshCandidate(it, new ArrayList<>()))
					.sources().addAll(candidate.sources());
		}

		for (ArtifactRefreshCandidate candidate : consolidated.values()) {

			DeclaredDependency dependency = new DeclaredDependency(candidate.artifactId());
			tasks.add(dependency);

			futures.add(executor.submit(() -> fetchReleases(indicator, candidate.artifactId(), candidate.sources(),
					executor, cache, consistency)));
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
			indicator.setFraction(progress);

			items.add(new DependencyUpdateOption(Dependency.from(tasks.get(i), ArtifactVersion.of("1.0")),
					res.releases()));
		}

		cache.recordUpdate();

		items.sort(Comparator.comparing(DependencyUpdateOption::getArtifactId, ArtifactId.BY_ARTIFACT_ID));
		return new DependencyUpdates("", items, errors);
	}

	private ResolverResult fetchReleases(ProgressIndicator indicator, ArtifactId artifactId,
			Collection<ReleaseSource> sources, ExecutorService executor, Cache cache, Consistency consistency) {

		try {
			indicator.setText(MessageBundle.message("action.check.dependency", artifactId));
			ReleaseResolver resolver = new ReleaseResolver(sources, executor);
			List<Release> releases;
			if (consistency == Consistency.NO_CACHE) {
				releases = resolver.getReleases(artifactId, null);
				cache.putVersionOptions(artifactId, releases);
			} else {

				releases = cache.getReleases(artifactId, consistency == Consistency.CACHED);
				if (CollectionUtils.isEmpty(releases)) {
					releases = resolver.getReleases(artifactId, null);
					cache.putVersionOptions(artifactId, releases);
				}
			}
			return new ResolverResult(null, releases);
		} catch (Exception e) {
			return new ResolverResult(artifactId + ": " + e.getMessage(), List.of());
		}
	}

	private record ResolverResult(@Nullable String error, List<Release> releases) {

	}

	/**
	 * Consistency of the dependency cache.
	 */
	public enum Consistency {

		/**
		 * Allow cached versions to be used.
		 */
		CACHED,

		/**
		 * Allow outdated cached versions to be used.
		 */
		CACHED_OUTDATED,

		/**
		 * Bypass the cache completely.
		 */
		NO_CACHE,
	}

	/**
	 * Candidate for an artifact to be refreshed containing all of its release
	 * sources.
	 * @param artifactId the artifact to refresh.
	 * @param sources the release sources to query.
	 */
	public record ArtifactRefreshCandidate(ArtifactId artifactId, Collection<ReleaseSource> sources) {

	}

}
