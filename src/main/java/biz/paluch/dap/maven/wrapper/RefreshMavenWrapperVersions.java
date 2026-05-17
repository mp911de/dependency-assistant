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

package biz.paluch.dap.maven.wrapper;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.ReleaseResolver;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.Cache;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.util.concurrency.AppExecutorUtil;

/**
 * Refresh Maven wrapper versions.
 * 
 * @author Mark Paluch
 */
class RefreshMavenWrapperVersions {

	private static final Logger LOG = Logger.getInstance(RefreshMavenWrapperVersions.class);

	private final Cache cache;

	private final Set<ReleaseSource> releaseSources;

	public RefreshMavenWrapperVersions(Cache cache, Set<ReleaseSource> releaseSources) {
		this.cache = cache;
		this.releaseSources = releaseSources.isEmpty() ? Set.of(ReleaseSource.mavenCentral()) : releaseSources;
	}

	/**
	 * Refreshes the wrapper versions if required.
	 * 
	 * @param indicator the current progress indicator.
	 */
	public void refreshWrapperVersions(ProgressIndicator indicator) {

		ExecutorService executor = AppExecutorUtil.getAppExecutorService();
		ReleaseResolver releaseResolver = new ReleaseResolver(releaseSources, executor);

		Map<ArtifactId, Future<List<Release>>> updateFutures = new HashMap<>();
		for (WrapperProperty property : WrapperProperty.values()) {

			indicator.checkCanceled();
			List<Release> releases = cache.getReleases(property.artifactId());
			if (releases.isEmpty()) {
				ArtifactId artifactId = property.artifactId();
				updateFutures.put(artifactId,
						executor.submit(() -> releaseResolver.getReleases(artifactId, indicator)));
			}
		}

		for (Map.Entry<ArtifactId, Future<List<Release>>> entry : updateFutures.entrySet()) {

			indicator.checkCanceled();

			try {
				List<Release> releases = entry.getValue().get(10, TimeUnit.SECONDS);
				cache.putVersionOptions(entry.getKey(), releases);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
				return;
			} catch (ExecutionException e) {
				LOG.warn("Release retrieval for '%s' failed".formatted(entry.getKey()), e.getCause());
			} catch (TimeoutException e) {
				LOG.warn("Release retrieval for '%s' timed out".formatted(entry.getKey()), e);
			}
		}
	}

}
