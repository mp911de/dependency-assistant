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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import biz.paluch.dap.artifact.ArtifactNotFoundException;
import biz.paluch.dap.artifact.TagSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.state.CachedRepository;
import biz.paluch.dap.util.Sequence;
import biz.paluch.dap.util.StepsProgressIndicator;
import biz.paluch.dap.util.StringUtils;
import biz.paluch.dap.util.VirtualThreads;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.AppExecutorUtil;

/**
 * Startup sweep that connects captured project metadata to cached repository
 * entries and keeps their tag listings fresh.
 *
 * <p>The sweep runs in two phases. The populate phase walks all cached
 * artifacts carrying project metadata with a repository URL, detects the
 * hosting {@link Platform}, and ensures a {@link CachedRepository} exists under
 * the connection's canonical key and URL. The scan phase selects up to
 * {@link #MAX_CANDIDATES} repositories whose last scan lies beyond the
 * {@link #SCAN_INTERVAL} and fetches their tags through the platform's
 * {@link TagSource}, processed by virtual-thread workers.
 *
 * <p>Failure back-off mirrors the cached artifact's empty-lookup scheme on the
 * repository's {@code lastUpdateTimestamp}: a failed lookup advances a small
 * counter, and once {@link #EMPTY_THRESHOLD} consecutive lookups failed, the
 * timestamp switches to the current time so further attempts wait out the scan
 * interval.
 *
 * @author Mark Paluch
 * @see CachedRepository
 * @see Platform
 */
public class RepositoryTagScanner {

	private static final Logger LOG = Logger.getInstance(RepositoryTagScanner.class);

	static final int MAX_CANDIDATES = 20;

	static final int EMPTY_THRESHOLD = 3;

	static final Duration SCAN_INTERVAL = Duration.ofDays(5);

	private static final int MAX_CONCURRENT_TASKS = Runtime.getRuntime().availableProcessors();

	private static final ThreadFactory THREAD_FACTORY = VirtualThreads.ofVirtual()
			.name("DependencyAssistant")
			.factory();

	private final Project project;

	private final Cache cache;

	public RepositoryTagScanner(Project project, Cache cache) {
		this.project = project;
		this.cache = cache;
	}

	/**
	 * Run the sweep: populate repository entries from captured project metadata,
	 * then scan due repositories for their tags.
	 *
	 * <p>A successful scan replaces the cached tag list and records the scan time.
	 * Cancellation is not recorded as a failed lookup. Failed remote lookups
	 * advance the repository's back-off state.
	 *
	 * @param indicator the progress indicator to report cancellation through.
	 */
	public void scan(ProgressIndicator indicator) {

		populateRepositories(indicator);
		scanTags(indicator);
	}

	/**
	 * Ensure a {@link CachedRepository} exists for every artifact whose captured
	 * metadata carries a platform-detectable repository URL. Entries are keyed and
	 * addressed by the canonical connection key and URL. Timestamps stay untouched.
	 */
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

		try (ExecutorService virtualExecutor = Executors.newThreadPerTaskExecutor(THREAD_FACTORY);
				ExecutorService executor = AppExecutorUtil.createBoundedApplicationPoolExecutor(
						"Dependency Assistant Repository Tags", virtualExecutor, MAX_CONCURRENT_TASKS)) {

			for (TagScanCandidate candidate : candidates) {
				executor.execute(() -> scanRepository(candidate, steps));
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
			// ignore to avoid JVM default exception handler from handling this exception
			return;
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

	/**
	 * Advance the failed-lookup counter on {@code lastUpdateTimestamp}. Once
	 * {@link #EMPTY_THRESHOLD} consecutive lookups failed, the value switches to
	 * the current time, delaying further attempts by the scan interval. An expired
	 * delay timestamp restarts the counter cycle.
	 */
	private void recordFailure(String key) {
		cache.doWithRepository(key, it -> {
			long counter = it.getLastUpdateTimestamp() < EMPTY_THRESHOLD ? it.getLastUpdateTimestamp() + 1 : 1;
			it.setLastUpdateTimestamp(counter >= EMPTY_THRESHOLD ? cache.now() : counter);
		});
	}

}
