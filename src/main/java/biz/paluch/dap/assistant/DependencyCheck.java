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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.artifact.*;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.progress.StepsProgressIndicator;
import org.jspecify.annotations.Nullable;

import org.springframework.util.CollectionUtils;

/**
 * Service that runs a dependency check for a project.
 *
 * @author Mark Paluch
 */
class DependencyCheck {

	private final Project project;

	private final StateService service;

	/**
	 * Create a dependency check bound to the given project.
	 */
	public DependencyCheck(Project project) {
		this.project = project;
		this.service = StateService.getInstance(project);
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

		StepsProgressIndicator steps = new StepsProgressIndicator(indicator, 2);
		steps.setIndeterminate(false);
		ProjectState projectState = service.getProjectState(context.getProjectId());
		DependencyCollector collector = ReadAction.nonBlocking(() -> {
			DependencyCollector c = context.scanDependencies(steps);
			projectState.setDependencies(c);
			return c;
		}).inSmartMode(project).executeSynchronously();

		String projectName = project.getName();
		steps.setText(MessageBundle.message("action.check.dependencies.progress.collecting", projectName));


		if (collector.isEmpty() && collector.getDeclarations().isEmpty()) {
			indicator.stop();
			return new DependencyUpdates(projectName, List.of(),
					List.of(MessageBundle.message("action.check.dependencies.empty")));
		}

		Collection<ReleaseSource> sources = collector.getReleaseSources();
		Set<ArtifactId> seen = new LinkedHashSet<>();
		List<UpdateTask> tasks = new ArrayList<>(collector.getDeclarations().size() + collector.getUsages().size());

		for (Dependency usage : collector.getUsages()) {
			if (seen.add(usage.getArtifactId())) {
				tasks.add(new UpdateTask(usage, sources, releases -> usage));
			}
		}

		for (DeclaredDependency declaration : collector.getDeclarations()) {
			if (declaration.getVersionSources().stream().anyMatch(VersionSource::isDefined)
					&& seen.add(declaration.getArtifactId())) {
				tasks.add(new UpdateTask(declaration, sources,
						releases -> context.resolveDependency(declaration, releases)));
			}
		}
		steps.nextStep();

		this.service.getState().setUsedOnce(true);
		try {
			return fetchUpdates(steps, projectName, tasks, consistency, 0.0);
		} finally {
			steps.nextStep();
		}
	}

	/**
	 * Scan the build file associated with the project context for all artifacts.
	 */
	public List<ArtifactRefreshCandidate> collectDependencies(ProgressIndicator indicator,
			DependencyAssistant assistant) {

		indicator.setText(
				MessageBundle.message("action.index-dependencies.indexing.assistant", assistant.getDisplayName()));
		ProjectStateIndexer indexer = new ProjectStateIndexer(project, indicator);
		DependencyCollector collector = indexer.aggregate(assistant);
		List<ArtifactId> usages = collector.getUsages().stream().map(Dependency::getArtifactId).toList();
		List<ArtifactId> declarations = collector.getDeclarations().stream().map(DeclaredDependency::getArtifactId)
				.toList();

		Set<ArtifactId> unique = new LinkedHashSet<>(declarations.size() + usages.size());
		unique.addAll(usages);
		unique.addAll(declarations);

		Collection<ReleaseSource> releaseSources = collector.getReleaseSources();
		return unique.stream().map(it -> new ArtifactRefreshCandidate(it, releaseSources)).toList();
	}

	public DependencyUpdates updateReleaseMetadata(ProgressIndicator indicator,
			List<ArtifactRefreshCandidate> candidates, Consistency consistency) {


		Map<ArtifactId, ArtifactRefreshCandidate> consolidated = new LinkedHashMap<>();
		for (ArtifactRefreshCandidate candidate : candidates) {
			consolidated
					.computeIfAbsent(candidate.artifactId(), it -> new ArtifactRefreshCandidate(it, new ArrayList<>()))
					.sources().addAll(candidate.sources());
		}

		List<UpdateTask> tasks = new ArrayList<>(consolidated.size());
		for (ArtifactRefreshCandidate candidate : consolidated.values()) {
			DeclaredDependency declaration = new DeclaredDependency(candidate.artifactId());
			tasks.add(new UpdateTask(declaration, candidate.sources(),
					releases -> Dependency.from(declaration, ArtifactVersion.of("1.0"))));
		}

		return fetchUpdates(indicator, "", tasks, consistency, 0.0);
	}

	/**
	 * Run the parallel release fetch for the given tasks and assemble the result.
	 *
	 * @param indicator the progress indicator.
	 * @param projectName the name to record on the result.
	 * @param tasks the tasks to fetch releases for.
	 * @param consistency the release-cache consistency to use.
	 * @param progressStart the fraction at which iteration progress begins; the
	 * remaining range up to {@literal 1.0} is consumed by task iteration.
	 * @return the assembled dependency updates.
	 */
	private DependencyUpdates fetchUpdates(ProgressIndicator indicator, String projectName, List<UpdateTask> tasks,
			Consistency consistency, double progressStart) {

		Cache cache = service.getCache();
		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		List<DependencyUpdateOption> items = new ArrayList<>();
		List<String> errors = new ArrayList<>();
		Map<UpdateTask, Future<ResolverResult>> futures = new HashMap<>();

		for (UpdateTask task : tasks) {
			Future<ResolverResult> future = executor
					.submit(() -> {
						indicator.checkCanceled();
						return fetchReleases(indicator, task.getArtifactId(), task.sources(), executor,
								cache, consistency);
					});
			futures.put(task, future);
		}

		double total = futures.size();
		double range = 1.0 - progressStart;
		int i = 0;
		for (Map.Entry<UpdateTask, Future<ResolverResult>> entry : futures.entrySet()) {

			try {
				indicator.checkCanceled();
			} catch (ProcessCanceledException e) {
				cancelRemainingFutures(futures);
				throw e;
			}

			String artifactId = entry.getKey().getArtifactId().toString();
			indicator.setText2(artifactId);

			ResolverResult res;
			boolean abort = false;
			try {
				res = entry.getValue().get(10, TimeUnit.SECONDS);
			} catch (ProcessCanceledException e) {
				entry.getValue().cancel(true);
				cancelRemainingFutures(futures);
				throw e;
			} catch (ExecutionException e) {
				if (e.getCause() instanceof ProcessCanceledException c) {
					entry.getValue().cancel(true);
					cancelRemainingFutures(futures);
					throw c;
				}

				entry.getValue().cancel(true);
				Throwable cause = e.getCause() != null ? e.getCause() : e;
				res = new ResolverResult("%s: %s".formatted(artifactId, cause.getMessage()), List.of());
			} catch (InterruptedException e) {
				res = cancelAndRecord(entry, futures, e);
				Thread.currentThread().interrupt();
				abort = true;
			} catch (TimeoutException e) {
				res = cancelAndRecord(entry, futures, e);
				abort = true;
			}
			if (res.error() != null) {
				errors.add(res.error());
			}

			double progress = (i + 1) / total;
			indicator.setFraction(progressStart + range * progress);
			indicator.setText(MessageBundle.message("action.check.dependency.checked", artifactId));

			Dependency dependency = entry.getKey().toDependency().apply(res.releases());
			if (dependency != null) {
				items.add(new DependencyUpdateOption(dependency, res.releases()));
			}

			i++;

			if (abort) {
				break;
			}
		}

		cache.recordUpdate();

		items.sort(Comparator.comparing(DependencyUpdateOption::getArtifactId, ArtifactId.BY_ARTIFACT_ID));
		return new DependencyUpdates(projectName, items, errors);
	}

	private static void cancelRemainingFutures(Map<UpdateTask, Future<ResolverResult>> futures) {
		for (Future<ResolverResult> future : futures.values()) {
			if (!future.isDone()) {
				future.cancel(true);
			}
		}
	}

	private static ResolverResult cancelAndRecord(Map.Entry<UpdateTask, Future<ResolverResult>> entry,
			Map<UpdateTask, Future<ResolverResult>> futures, Throwable cause) {

		entry.getValue().cancel(true);
		cancelRemainingFutures(futures);
		String artifactId = entry.getKey().getArtifactId().toString();
		return new ResolverResult("%s: %s".formatted(artifactId, cause.getMessage()), List.of());
	}

	private ResolverResult fetchReleases(ProgressIndicator indicator, ArtifactId artifactId,
			Collection<ReleaseSource> sources, ExecutorService executor, Cache cache, Consistency consistency) {

		indicator.setText(MessageBundle.message("action.check.dependency", artifactId));
		indicator.checkCanceled();

		try {

			ReleaseResolver resolver = new ReleaseResolver(sources, executor);
			List<Release> releases;
			if (consistency == Consistency.NO_CACHE) {
				releases = resolver.getReleases(artifactId, indicator);
				cache.putVersionOptions(artifactId, releases);
			} else {

				releases = cache.getReleases(artifactId, consistency == Consistency.CACHED);
				if (CollectionUtils.isEmpty(releases)) {
					releases = resolver.getReleases(artifactId, indicator);
					cache.putVersionOptions(artifactId, releases);
				}
			}
			return new ResolverResult(null, releases);
		} catch (ProcessCanceledException e) {
			throw e;
		} catch (Exception e) {
			return new ResolverResult(artifactId + ": " + e.getMessage(), List.of());
		}
	}

	private record ResolverResult(@Nullable String error, List<Release> releases) {

	}

	private record UpdateTask(DeclaredDependency declared, Collection<ReleaseSource> sources,
			Function<List<Release>, @Nullable Dependency> toDependency) {

		ArtifactId getArtifactId() {
			return declared.getArtifactId();
		}

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
