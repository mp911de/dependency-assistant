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

package biz.paluch.dap.metadata;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactNotFoundException;
import biz.paluch.dap.artifact.TagSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.state.CachedRepository;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StepsProgressIndicator;
import biz.paluch.dap.util.StringUtils;
import biz.paluch.dap.util.TaskScope;
import biz.paluch.dap.util.TaskScope.Subtask;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;

/**
 * Refreshes cached repository tags from captured project metadata.
 * <p>Repeated lookup failures delay further attempts. Cancellation does not
 * count as a failed lookup.
 *
 * @author Mark Paluch
 * @see CachedRepository
 */
public class RepositoryTagScanner {

	private static final Logger LOG = Logger.getInstance(RepositoryTagScanner.class);

	static final int MAX_CANDIDATES = 20;

	static final int EMPTY_THRESHOLD = 3;

	static final Duration SCAN_INTERVAL = Duration.ofDays(5);

	private static final int MAX_CONCURRENT_TASKS = Runtime.getRuntime().availableProcessors();

	private final Project project;

	private final Cache cache;

	public RepositoryTagScanner(Project project, Cache cache) {
		this.project = project;
		this.cache = cache;
	}

	/**
	 * Refresh repositories whose tags are due for a scan.
	 * <p>Successful scans replace the cached tags. Failed lookups retain them.
	 */
	public void scan(ProgressIndicator indicator) {

		populateRepositories(indicator);
		scanTags(indicator);
	}

	private void populateRepositories(ProgressIndicator indicator) {

		for (CachedArtifact artifact : cache.getCachedArtifacts()) {

			indicator.checkCanceled();

			CachedMetadata metadata = artifact.getProjectMetadata();
			String repositoryUrl = metadata != null ? metadata.getRepositoryUrl() : null;
			if (!StringUtils.hasText(repositoryUrl)) {
				continue;
			}

			RepositoryConnection connection = Platform.findConnection(repositoryUrl, null);
			if (connection != null) {
				cache.createOrUpdateRepository(connection.getKey(), connection.getUrl());
			}
		}
	}

	// TODO: populateRepositories(List<ArtifactId>,ProgressIndicator indicator) to
	// be called post DependencyCheck

	private void scanTags(ProgressIndicator indicator) {

		List<TagScanCandidate> candidates = findCandidates();
		if (candidates.isEmpty()) {
			return;
		}

		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, candidates.size());

		try (TaskScope scope = TaskScope.open("RepositoryTags", indicator, MAX_CONCURRENT_TASKS)) {

			for (TagScanCandidate candidate : candidates) {
				scope.fork(() -> {
					scanRepository(candidate, steps);
					return null;
				});
			}

			scope.joinAll();

			// Lookup failures are already recorded. Propagate cancellation and other
			// errors.
			for (Subtask<?> subtask : scope.getSubtasks()) {
				subtask.getOrThrow();
			}
		}
	}

	record TagScanCandidate(RepositoryConnection connection, ProjectRepository repository,
			TagSource tagSource) {

	}

	private List<TagScanCandidate> findCandidates() {

		long threshold = cache.now() - SCAN_INTERVAL.toMillis();
		List<TagScanCandidate> candidates = new ArrayList<>();

		for (CachedRepository repository : cache.getRepositories()) {

			if (repository.getLastUpdateTimestamp() < threshold) {

				RepositoryConnection connection = Platform.findConnection(repository.getUrl(), null);

				if (connection == null) {
					continue;
				}

				ProjectRepository projectRepository = connection.createRepository(project);

				if (projectRepository == null) {
					continue;
				}

				TagSource tagSource = projectRepository.getTagSource();
				if (tagSource == null) {
					continue;
				}

				candidates.add(new TagScanCandidate(connection, projectRepository, tagSource));
			}
			if (candidates.size() == MAX_CANDIDATES) {
				break;
			}
		}
		return candidates;
	}

	private void scanRepository(TagScanCandidate scanCandidate, StepsProgressIndicator indicator) {

		String key = scanCandidate.connection.getKey();
		try {
			indicator.checkCanceled();
			indicator.setText2(key);

			Sequence<String> tags = scanCandidate.repository.getTags(indicator);

			cache.doWithRepository(key, it -> {
				it.setTags(tags.toList());
				it.setLastUpdateTimestamp(cache.now());
			});
		} catch (ProcessCanceledException e) {
			throw e;
		} catch (ArtifactNotFoundException e) {
			LOG.info("[" + key + "] Repository not found", e);
			recordFailure(key);
		} catch (IOException | RuntimeException e) {
			LOG.warn("[" + key + "] Repository tag scan failed", e);
			recordFailure(key);
		} finally {
			indicator.nextStep();
		}
	}

	private void recordFailure(String key) {
		// The timestamp also stores the failure count until retries are exhausted.
		cache.doWithRepository(key, it -> {
			long counter = it.getLastUpdateTimestamp() < EMPTY_THRESHOLD ? it.getLastUpdateTimestamp() + 1 : 1;
			it.setLastUpdateTimestamp(counter >= EMPTY_THRESHOLD ? cache.now() : counter);
		});
	}

}
