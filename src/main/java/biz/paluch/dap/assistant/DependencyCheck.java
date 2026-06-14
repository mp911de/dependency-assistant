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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ReleaseResolver;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.intellij.util.progress.StepsProgressIndicator;

/**
 * Package service that collects declared dependencies and resolves available
 * releases for a project.
 *
 * <p>A check first scans the selected build files, then resolves release
 * metadata for the collected artifacts. Release resolution uses the project
 * cache according to the requested {@link Consistency}; cancellation is
 * propagated through the supplied progress indicator.
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
	 * @param consistency the release-cache consistency to use.
	 * @return the merged dependency check result.
	 */
	public DependencyUpgradeCandidates findDependencyUpgrades(ProgressIndicator indicator,
			UpgradeScope scope, Consistency consistency) {
		this.service.getState().setUsedOnce(true);
		DependencyfileService ruleService = DependencyfileService.getInstance(project);
		DependencyCheckAggregator aggregator = aggregate(indicator, scope);
		Map<ArtifactId, ReleaseLookupResult> releases = resolveReleases(indicator, getArtifactSources(aggregator),
				consistency);
		return aggregator.toDependencyCheckResult(releases, ruleService);
	}

	private DependencyCheckAggregator aggregate(ProgressIndicator indicator, UpgradeScope scope) {
		DependencyCheckAggregator aggregator = new DependencyCheckAggregator(project, service);
		for (UpgradeScope.Entry entry : scope) {
			indicator.checkCanceled();
			indicator.setText(MessageBundle.message("action.check.dependencies.progress.collecting",
					entry.buildFile().getName()));
			aggregator.add(entry, indicator);
		}
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
			List<ReleaseSources> candidates, Consistency consistency) {
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
	private Map<ArtifactId, ReleaseLookupResult> resolveReleases(ProgressIndicator indicator,
			List<ReleaseSources> artifactSources, Consistency consistency) {

		StepsProgressIndicator steps = new StepsProgressIndicator(indicator, artifactSources.size() + 1);
		steps.setIndeterminate(false);

		Cache cache = service.getCache();
		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		Map<ArtifactId, Future<ReleaseLookupResult>> futures = new LinkedHashMap<>();

		for (ReleaseSources artifactSource : artifactSources) {
			futures.put(artifactSource.artifactId, executor.submit(() -> {
				indicator.checkCanceled();
				String name = artifactSource.artifactId.toString();
				indicator.setText(MessageBundle.message("action.check.dependency.loading", name));
				ReleaseLookupResult result = fetchReleases(indicator, artifactSource, executor, cache, consistency);
				indicator.setText(MessageBundle.message("action.check.dependency.checked", name));
				steps.nextStep();
				return result;
			}));
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
				res = new ReleaseLookupResult("%s: %s".formatted(name, cause.getMessage()), Releases.empty());
			} catch (InterruptedException e) {
				res = cancelAndRecord(artifactId, futures, e);
				Thread.currentThread().interrupt();
				abort = true;
			} catch (TimeoutException e) {
				res = new ReleaseLookupResult("%s: %s".formatted(name, e.getMessage()), Releases.empty());
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

	private ReleaseLookupResult fetchReleases(ProgressIndicator indicator, ReleaseSources artifactSource,
			ExecutorService executor, Cache cache, Consistency consistency) {
		indicator.checkCanceled();

		try {

			ReleaseResolver resolver = new ReleaseResolver(artifactSource.sources(), executor);
			Releases releases;
			if (consistency == Consistency.NO_CACHE) {
				releases = resolver.getReleases(artifactSource.artifactId(), indicator);
				cache.putVersionOptions(artifactSource.artifactId(), releases);
			} else {

				releases = cache.getReleases(artifactSource.artifactId(), consistency == Consistency.CACHED);
				if (releases.isEmpty()) {
					releases = resolver.getReleases(artifactSource.artifactId(), indicator);
					cache.putVersionOptions(artifactSource.artifactId(), releases);
				}
			}
			return new ReleaseLookupResult(null, releases);
		} catch (ProcessCanceledException e) {
			throw e;
		} catch (Exception e) {
			return new ReleaseLookupResult(artifactSource.artifactId() + ": " + e.getMessage(), Releases.empty());
		}
	}

	private static ReleaseLookupResult cancelAndRecord(ArtifactId artifactId,
			Map<ArtifactId, Future<ReleaseLookupResult>> futures, Throwable cause) {
		cancelRemainingFutures(futures);
		return new ReleaseLookupResult("%s: %s".formatted(artifactId, cause.getMessage()), Releases.empty());
	}

	/**
	 * Release-cache consistency mode used while resolving update candidates.
	 */
	public enum Consistency {

		/**
		 * Allow cached versions to be used.
		 */
		CACHED,

		/**
		 * Bypass the cache completely.
		 */
		NO_CACHE,
	}

	/**
	 * Release lookup inputs for one artifact.
	 *
	 * @param artifactId the artifact whose releases should be resolved.
	 * @param sources the release sources that can provide versions for the
	 * artifact.
	 */
	public record ReleaseSources(ArtifactId artifactId, Collection<ReleaseSource> sources) {

	}

}
