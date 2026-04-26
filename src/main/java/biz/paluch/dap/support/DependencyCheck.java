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
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclaredDependency;
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
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
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

		ProjectState projectState = service.getProjectState(context.getProjectId());
		Cache cache = service.getCache();

		DependencyUpdates dependencyUpdates = getDependencyUpdates(indicator, () -> {
			DependencyCollector collector = context.scanDependencies(indicator);
			projectState.setDependencies(collector);
			return collector;
		}, context.getReleaseSources(), consistency);

		cache.recordUpdate();

		return dependencyUpdates;
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
			Supplier<DependencyCollector> dependencyCollector, List<ReleaseSource> sources, Consistency consistency) {

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
		List<Dependency> tasks = new ArrayList<>(collector.getUsages());

		for (Dependency task : tasks) {
			futures.add(executor.submit(() -> {
				indicator.setText(MessageBundle.message("action.check.dependency", task.getArtifactId()));
				try {
					List<Release> releases;
					if (consistency == Consistency.NO_CACHE) {
						releases = resolver.getReleases(task.getArtifactId(), task.getCurrentVersion());
						cache.putVersionOptions(task.getArtifactId(), releases);
					} else {

						releases = cache.getReleases(task.getArtifactId(), consistency == Consistency.CACHED);
						if (CollectionUtils.isEmpty(releases)) {
							releases = resolver.getReleases(task.getArtifactId(), task.getCurrentVersion());
							cache.putVersionOptions(task.getArtifactId(), releases);
						}
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

	public DependencyUpdates updateReleaseMetadata(ProgressIndicator indicator,
			List<ArtifactRefreshCandidate> candidates,
			Consistency consistency) {

		Cache cache = service.getCache();
		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		List<DependencyUpdateOption> items = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		List<Future<ResolverResult>> futures = new ArrayList<>();
		List<Dependency> tasks = new ArrayList<>();

		Map<ArtifactId, ArtifactRefreshCandidate> consolidsated = new LinkedHashMap<>();
		for (ArtifactRefreshCandidate candidate : candidates) {
			consolidsated
					.computeIfAbsent(candidate.artifactId(), it -> new ArtifactRefreshCandidate(it, new ArrayList<>()))
					.sources().addAll(candidate.sources());
		}

		for (ArtifactRefreshCandidate candidate : consolidsated.values()) {
			futures.add(executor.submit(() -> {
				try {
					indicator.setText(MessageBundle.message("action.check.dependency", candidate.artifactId()));
					ReleaseResolver resolver = new ReleaseResolver(candidate.sources(), executor);
					List<Release> releases;

					Dependency dependency = new Dependency(candidate.artifactId(), null);
					tasks.add(dependency);
					if (consistency == Consistency.NO_CACHE) {
						releases = resolver.getReleases(candidate.artifactId(), null);
						cache.putVersionOptions(candidate.artifactId(), releases);
					} else {

						releases = cache.getReleases(candidate.artifactId(), consistency == Consistency.CACHED);
						if (CollectionUtils.isEmpty(releases)) {
							releases = resolver.getReleases(candidate.artifactId(), null);
							cache.putVersionOptions(candidate.artifactId(), releases);
						}
					}
					return new ResolverResult(null, releases);
				} catch (Exception e) {
					return new ResolverResult(candidate.artifactId() + ": " + e.getMessage(), List.of());
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
			indicator.setFraction(progress);

			items.add(new DependencyUpdateOption(tasks.get(i), res.releases()));
		}

		items.sort(Comparator.comparing(DependencyUpdateOption::getArtifactId, ArtifactId.BY_ARTIFACT_ID));
		return new DependencyUpdates("", items, errors);
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
	 * @param artifactId
	 * @param sources
	 */
	public record ArtifactRefreshCandidate(ArtifactId artifactId, Collection<ReleaseSource> sources) {

	}

}
