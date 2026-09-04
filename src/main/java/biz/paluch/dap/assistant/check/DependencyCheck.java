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

package biz.paluch.dap.assistant.check;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.BomMembershipResolver;
import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.metadata.ProjectMetadataIndexer;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.state.ApplicationSettings;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import biz.paluch.dap.util.TaskScope;
import biz.paluch.dap.util.TaskScope.Subtask;
import biz.paluch.dap.util.WeightedStepsProgressIndicator;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

/**
 * Coordinates a dependency check for an IntelliJ project.
 *
 * <p>A check scans the selected build files, aggregates declarations by package
 * identity, resolves and caches release metadata, updates BOM membership and
 * project metadata, and performs a best-effort delta vulnerability scan for
 * newly fetched releases. Release resolution honors the requested
 * {@link ReleaseResolver.Consistency}. Cancellation is propagated through the
 * supplied progress indicator.
 *
 * @author Mark Paluch
 */
public class DependencyCheck {

	static final Duration LOOKUP_TIMEOUT = Duration.ofSeconds(60);

	private final Project project;

	private final ProjectMetadataIndexer metadataIndexer;

	private final StateService stateService;

	private final ApplicationSettings settings;

	/**
	 * Create a dependency check bound to the given project.
	 *
	 * @param project the IntelliJ project whose dependency state should be used.
	 */
	public DependencyCheck(Project project) {
		this.project = project;
		this.stateService = StateService.getInstance(project);
		this.settings = ApplicationSettings.getInstance();
		this.metadataIndexer = new ProjectMetadataIndexer(project);
	}

	/**
	 * Run the dependency update check over an {@link UpgradeScope upgrade scope} of
	 * one or more build files.
	 *
	 * @param indicator the progress indicator.
	 * @param scope the in-scope build files with their contexts.
	 * @return the sorted upgrade candidates, checked file scope, and non-fatal
	 * lookup errors.
	 */
	public DependencyCheckResult findDependencyUpgrades(ProgressIndicator indicator,
			UpgradeScope scope) {

		this.stateService.markUsed();
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

		Map<PackageIdentity, ReleaseLookupResult> releases = resolveReleases(steps,
				aggregator.getReleaseSources(), ReleaseResolver.cached());

		reindexMetadata(indicator, aggregator.getDependencyVersions());

		return aggregator.toDependencyCheckResult(releases, ruleService);
	}

	private void reindexMetadata(ProgressIndicator indicator,
			Map<PackageIdentity, ArtifactVersion> dependencyVersions) {
		metadataIndexer.update(indicator, dependencyVersions);
	}

	private DependencyCheckAggregator aggregate(StepsProgressIndicator steps, UpgradeScope scope) {
		DependencyCheckAggregator aggregator = new DependencyCheckAggregator(project, stateService, settings);
		steps.setText(MessageBundle.message("action.check.dependencies.progress.collecting"));
		scope.forEach(entry -> {
			steps.checkCanceled();
			steps.setText2(entry.buildFile().getName());
			aggregator.add(entry, steps);
			steps.nextStep();
		});

		steps.setText2("");
		aggregator.addContextReleaseSources();
		return aggregator;
	}

	/**
	 * Scan all available project contexts for declared dependencies and release
	 * sources.
	 *
	 * @param indicator the progress indicator used for cancellation and user
	 * feedback.
	 * @param assistant the dependency assistant that provides project entries.
	 * @return the aggregated declarations, build files, contexts, and release
	 * sources.
	 */
	public DependencyCheckAggregator collectDependencies(ProgressIndicator indicator,
			DependencyAssistant assistant) {
		ProjectStateIndexer indexer = new ProjectStateIndexer(project, indicator);
		DependencyCheckAggregator aggregator = new DependencyCheckAggregator(project, stateService, settings);
		indexer.forEachAvailableEntry(assistant, (psiFile, context) -> {
			aggregator.add(psiFile.getVirtualFile(), context, indicator);
		});
		aggregator.addContextReleaseSources();
		return aggregator;
	}

	/**
	 * Resolve available releases for the given artifact groups.
	 *
	 * <p>Artifacts whose release lookup fails are omitted from the returned map.
	 * Errors remain available only to the full dependency-check flow.
	 *
	 * @param indicator the progress indicator used for cancellation and user
	 * feedback.
	 * @param aggregators the aggregated scans whose release sources should be
	 * queried.
	 * @param consistency the release-cache consistency to use.
	 * @return successfully resolved releases keyed by package identity, in
	 * encounter order.
	 */
	public Map<PackageIdentity, Releases> getReleases(ProgressIndicator indicator,
			List<DependencyCheckAggregator> aggregators, ReleaseResolver.Consistency consistency) {
		indicator.setText(MessageBundle.message("action.check.dependency.loading.remote"));

		Map<PackageIdentity, ArtifactVersion> dependencyVersions = new HashMap<>();
		List<ReleaseSources> sources = new ArrayList<>();

		for (DependencyCheckAggregator aggregator : aggregators) {
			sources.addAll(aggregator.getReleaseSources());
			dependencyVersions.putAll(aggregator.getDependencyVersions());
		}

		Map<PackageIdentity, ReleaseLookupResult> resultMap = resolveReleases(indicator, sources, consistency);
		reindexMetadata(indicator, dependencyVersions);
		Map<PackageIdentity, Releases> releases = new LinkedHashMap<>();
		for (Map.Entry<PackageIdentity, ReleaseLookupResult> entry : resultMap.entrySet()) {
			if (entry.getValue().error() == null) {
				releases.put(entry.getKey(), entry.getValue().releases());
			}
		}
		return releases;
	}

	/**
	 * Resolve releases for each artifact in parallel, collecting one
	 * {@link ReleaseLookupResult} per artifact.
	 *
	 * @param indicator the progress indicator.
	 * @param artifactSources the release sources to query per artifact.
	 * @param consistency the release-cache consistency to use.
	 * @return the resolver result per artifact, in encounter order. A lookup
	 * exceeding {@link #LOOKUP_TIMEOUT} or throwing is represented as a failed
	 * result. Cancellation propagates as {@link ProcessCanceledException} and
	 * cancels outstanding lookups.
	 */
	protected Map<PackageIdentity, ReleaseLookupResult> resolveReleases(ProgressIndicator indicator,
			List<ReleaseSources> artifactSources, ReleaseResolver.Consistency consistency) {

		VulnerabilityScanner scanner = VulnerabilityScanner.create(project, stateService);
		int stepCount = artifactSources.size() + 1;
		if (scanner.isPresent()) {
			stepCount++;
		}
		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, stepCount);
		steps.setIndeterminate(false);

		Cache cache = stateService.getCache();
		ReleaseResolver resolver = new ReleaseResolver(indicator, cache);

		Map<PackageIdentity, ReleaseLookupResult> results = new LinkedHashMap<>();

		try (TaskScope scope = TaskScope.open("ReleaseLookup", indicator)) {

			Map<PackageIdentity, Subtask<ReleaseLookupResult>> lookups = new LinkedHashMap<>();
			for (ReleaseSources artifactSource : artifactSources) {

				lookups.put(artifactSource.pkg(), scope.fork(() -> {
					indicator.checkCanceled();
					String name = artifactSource.artifactId().toString();
					indicator.setText(MessageBundle.message("action.check.dependency.loading", name));
					ReleaseLookupResult result = resolver.getReleases(artifactSource, consistency);

					indicator.setText(MessageBundle.message("action.check.dependency.checked", name));
					steps.nextStep();
					return result;
				}));
			}

			steps.nextStep();

			lookups.forEach((pkg, lookup) -> {

				lookup.join(LOOKUP_TIMEOUT);

				results.put(pkg, switch (lookup.state()) {
				case SUCCESS -> lookup.get();
				case CANCELLED -> throw new ProcessCanceledException();
				default -> {
					Throwable failure = lookup.exception();
					yield ReleaseLookupResult.failed("%s: %s".formatted(pkg,
							failure != null ? failure.getMessage() : lookup.state()));
				}
				});
			});
		}

		List<PackageIdentity> artifactIds = results.entrySet().stream()
				.filter(e -> !e.getValue().newReleases().isEmpty())
				.map(Map.Entry::getKey).toList();
		indicator.checkCanceled();
		if (!artifactIds.isEmpty()) {
			BomMembershipResolver.create(project, cache)
					.resolve(artifactIds, indicator);
		}

		indicator.checkCanceled();

		if (scanner.isPresent()) {
			indicator.setText(MessageBundle.message("action.check.dependency.vulnerability-scan"));
			scanner.scanNewReleases(indicator, artifactSources, results);
		}
		steps.nextStep();

		cache.recordUpdate();
		return results;
	}

}
