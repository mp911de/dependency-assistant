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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import biz.paluch.dap.util.WeightedStepsProgressIndicator;
import com.google.common.base.Supplier;
import com.intellij.concurrency.virtualThreads.IntelliJVirtualThreads;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

/**
 * Package service that collects declared dependencies and resolves available
 * releases for a project.
 *
 * <p>A check first scans the selected build files, then resolves release
 * metadata for the collected artifacts. Release resolution uses the project
 * cache according to the requested {@link ReleaseResolver.Consistency};
 * cancellation is propagated through the supplied progress indicator.
 *
 * @author Mark Paluch
 */
class DependencyCheck {

	private final Project project;

	private final StateService service;

	/**
	 * Create a dependency check bound to the given project.
	 *
	 * @param project the IntelliJ project whose dependency state should be used.
	 */
	public DependencyCheck(Project project) {
		this.project = project;
		this.service = StateService.getInstance(project);
	}

	/**
	 * Run the dependency update check over an {@link UpgradeScope upgrade scope} of
	 * one or more build files.
	 *
	 * @param indicator the progress indicator.
	 * @param scope the in-scope build files with their contexts.
	 * @return the merged dependency check result.
	 */
	public DependencyUpgradeCandidates findDependencyUpgrades(ProgressIndicator indicator,
			UpgradeScope scope) {
		this.service.getState().setUsedOnce(true);
		indicator.setIndeterminate(false);

		// 🦄🔢
		double scanWeight = scope.entries().size() > 50 ? 0.3 : 0.1;
		WeightedStepsProgressIndicator steps = new WeightedStepsProgressIndicator(indicator, scanWeight, 0.9);
		DependencyfileService ruleService = DependencyfileService.getInstance(project);
		DependencyCheckAggregator aggregator = ReadAction.nonBlocking(() -> {
			return aggregate(StepsProgressIndicator
					.forSteps(indicator, scope.size()), scope);
		}).inSmartMode(project).executeSynchronously();
		steps.nextStep();

		Map<ArtifactId, ReleaseLookupResult> releases = resolveReleases(steps, getArtifactSources(aggregator),
				ReleaseResolver.cached());
		return aggregator.toDependencyCheckResult(releases, ruleService);
	}

	private DependencyCheckAggregator aggregate(StepsProgressIndicator steps, UpgradeScope scope) {
		DependencyCheckAggregator aggregator = new DependencyCheckAggregator(project, service);
		steps.setText(MessageBundle.message("action.check.dependencies.progress.collecting"));
		scope.forEach(entry -> {
			steps.checkCanceled();
			steps.setText2(entry.buildFile().getName());
			aggregator.add(entry, steps);
			steps.nextStep();
		});

		steps.setText2("");
		return aggregator;
	}

	/**
	 * Scan all available project contexts for declared dependencies and release
	 * sources.
	 *
	 * @param indicator the progress indicator used for cancellation and user
	 * feedback.
	 * @param assistant the dependency assistant that provides project entries.
	 * @return one release-source group per collected artifact.
	 */
	public List<ReleaseSources> collectDependencies(ProgressIndicator indicator,
			DependencyAssistant assistant) {
		ProjectStateIndexer indexer = new ProjectStateIndexer(project, indicator);
		DependencyCheckAggregator aggregator = new DependencyCheckAggregator(project, service);
		indexer.forEachAvailableEntry(assistant, (psiFile, context) -> {
			aggregator.add(psiFile.getVirtualFile(), context, indicator);
		});
		return getArtifactSources(aggregator);
	}

	private List<ReleaseSources> getArtifactSources(DependencyCheckAggregator aggregator) {
		List<ReleaseSources> candidates = new ArrayList<>();
		aggregator.forEachArtifact((artifactId, releaseSources) -> {
			candidates.add(new ReleaseSources(artifactId, releaseSources));
		});
		return candidates;
	}

	/**
	 * Resolve available releases for the given artifact groups.
	 *
	 * <p>Artifacts whose release lookup fails are omitted from the returned map;
	 * errors remain available only to the full dependency-check flow.
	 *
	 * @param indicator the progress indicator used for cancellation and user
	 * feedback.
	 * @param candidates the artifacts and release sources to query.
	 * @param consistency the release-cache consistency to use.
	 * @return successfully resolved releases keyed by artifact, in encounter order.
	 */
	public Map<ArtifactId, Releases> getReleases(ProgressIndicator indicator,
			List<ReleaseSources> candidates, ReleaseResolver.Consistency consistency) {
		indicator.setText(MessageBundle.message("action.check.dependency.loading.remote"));
		Map<ArtifactId, ReleaseLookupResult> resultMap = resolveReleases(indicator, candidates, consistency);
		Map<ArtifactId, Releases> releases = new LinkedHashMap<>();
		for (Map.Entry<ArtifactId, ReleaseLookupResult> entry : resultMap.entrySet()) {
			if (entry.getValue().error() == null) {
				releases.put(entry.getKey(), entry.getValue().releases());
			}
		}
		return releases;
	}

	/**
	 * Resolve releases for each artifact in parallel, collecting one
	 * {@link ReleaseLookupResult} per artifact with timeout and cancellation
	 * handling.
	 *
	 * @param indicator the progress indicator.
	 * @param artifactSources the release sources to query per artifact.
	 * @param consistency the release-cache consistency to use.
	 * @return the resolver result per artifact, in encounter order; a run aborted
	 * by timeout or interruption yields a partial map.
	 */
	protected Map<ArtifactId, ReleaseLookupResult> resolveReleases(ProgressIndicator indicator,
			List<ReleaseSources> artifactSources, ReleaseResolver.Consistency consistency) {

		ThreadFactory threadFactory = IntelliJVirtualThreads.ofVirtual().name("DependencyAssistant").factory();
		ThreadFactory resolverFactory = IntelliJVirtualThreads.ofVirtual().name("DependencyAssistant-ReleaseResolver")
				.factory();
		try (ExecutorService executor = Executors.newThreadPerTaskExecutor(threadFactory);
				ExecutorService resolverExecutor = Executors.newThreadPerTaskExecutor(resolverFactory)) {
			return resolveReleases(indicator, artifactSources, consistency, resolverExecutor, executor);
		}
	}

	protected Map<ArtifactId, ReleaseLookupResult> resolveReleases(ProgressIndicator indicator,
			List<ReleaseSources> artifactSources, ReleaseResolver.Consistency consistency,
			ExecutorService resolverExecutor, ExecutorService executor) {

		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, artifactSources.size() + 1);
		steps.setIndeterminate(false);

		Cache cache = service.getCache();
		ReleaseResolver resolver = new ReleaseResolver(resolverExecutor, indicator, cache);
		Map<ArtifactId, Future<ReleaseLookupResult>> futures = new LinkedHashMap<>();

		for (ReleaseSources artifactSource : artifactSources) {

			Supplier<ReleaseLookupResult> lookupReleaseSupplier = () -> {
				indicator.checkCanceled();
				String name = artifactSource.artifactId().toString();
				indicator.setText(MessageBundle.message("action.check.dependency.loading", name));
				ReleaseLookupResult result = resolver.getReleases(artifactSource, consistency);
				indicator.setText(MessageBundle.message("action.check.dependency.checked", name));
				steps.nextStep();
				return result;
			};

			futures.put(artifactSource.artifactId(), executor.submit(() -> lookupReleaseSupplier.get()));
		}


		steps.nextStep();

		Map<ArtifactId, ReleaseLookupResult> results = new LinkedHashMap<>();
		for (Map.Entry<ArtifactId, Future<ReleaseLookupResult>> entry : futures.entrySet()) {

			ArtifactId artifactId = entry.getKey();
			String name = artifactId.toString();

			try {
				indicator.checkCanceled();
			} catch (ProcessCanceledException e) {
				cancelRemainingFutures(futures);
				throw e;
			}

			ReleaseLookupResult res;
			boolean abort = false;
			try {
				res = entry.getValue().get(60, TimeUnit.SECONDS);
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
				res = ReleaseLookupResult.failed("%s: %s".formatted(name, cause.getMessage()));
			} catch (InterruptedException e) {
				res = cancelAndRecord(artifactId, futures, e);
				Thread.currentThread().interrupt();
				abort = true;
			} catch (TimeoutException e) {
				res = ReleaseLookupResult.failed("%s: %s".formatted(name, e.getMessage()));
			}

			results.put(artifactId, res);

			if (abort) {
				break;
			}
		}

		cache.recordUpdate();
		return results;
	}

	private static void cancelRemainingFutures(Map<ArtifactId, Future<ReleaseLookupResult>> futures) {
		for (Future<ReleaseLookupResult> future : futures.values()) {
			if (!future.isDone()) {
				future.cancel(true);
			}
		}
	}

	private static ReleaseLookupResult cancelAndRecord(ArtifactId artifactId,
			Map<ArtifactId, Future<ReleaseLookupResult>> futures, Throwable cause) {
		cancelRemainingFutures(futures);
		return ReleaseLookupResult.failed("%s: %s".formatted(artifactId, cause.getMessage()));
	}

}
