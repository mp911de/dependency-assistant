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

package biz.paluch.dap.artifact;

import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import org.jspecify.annotations.Nullable;

/**
 * Resolves available versions by fetching {@link Release}s from
 * {@link ReleaseSource}s.
 *
 * @author Mark Paluch
 */
public class ReleaseResolver {

	private final Collection<ReleaseSource> sources;

	private final ExecutorService executor;

	/**
	 * Create a resolver using the given release sources and executor.
	 */
	public ReleaseResolver(Collection<ReleaseSource> sources, ExecutorService executor) {
		this.sources = sources instanceof Set<ReleaseSource> s ? s : new LinkedHashSet<>(sources);
		this.executor = executor;
	}

	public List<Release> getReleases(ArtifactId artifactId, @Nullable ProgressIndicator indicator) {
		return getReleases(artifactId, null, indicator);
	}

	/**
	 * Resolve releases for the given artifact across all configured sources.
	 *
	 * @param artifactId the artifact to resolve releases for; must not be
	 * {@literal null}.
	 * @param currentVersion the current version to retain in the result; can be
	 * {@literal null}.
	 * @param indicator the progress indicator to use for cancellation; can be
	 * {@literal null} in which case an {@link EmptyProgressIndicator} is
	 * substituted.
	 * @return the resolved releases, newest first; guaranteed to be not
	 * {@literal null}.
	 */
	public List<Release> getReleases(ArtifactId artifactId, @Nullable ArtifactVersion currentVersion,
			@Nullable ProgressIndicator indicator) {

		ProgressIndicator progressIndicator = indicator != null ? indicator : new EmptyProgressIndicator();
		Set<Release> result = new TreeSet<>(Comparator.<Release>naturalOrder().reversed());
		List<Future<List<Release>>> futures = new ArrayList<>();
		for (ReleaseSource source : sources) {
			progressIndicator.checkCanceled();
			Future<List<Release>> future = executor.submit(() -> source.getReleases(artifactId, progressIndicator));
			futures.add(future);
		}

		List<RuntimeException> errors = new ArrayList<>();
		ArtifactNotFoundException notFoundException = null;

		for (int i = 0; i < futures.size(); i++) {
			Future<List<Release>> future = futures.get(i);
			try {
				List<Release> releases = future.get(10, TimeUnit.SECONDS);
				result.addAll(releases);
			} catch (InterruptedException e) {
				future.cancel(true);
				cancelRemaining(futures, i + 1);
				Thread.currentThread().interrupt();
				return new ArrayList<>(result);
			} catch (TimeoutException e) {
				future.cancel(true);
				errors.add(new RuntimeException("Release source timed out", e));
			} catch (ExecutionException e) {

				if (e.getCause() instanceof ArtifactNotFoundException) {
					notFoundException = (ArtifactNotFoundException) e.getCause();
					continue;
				}
				errors.add(e.getCause() instanceof RuntimeException re ? re
						: new UndeclaredThrowableException(e.getCause()));
			}
		}

		if (result.isEmpty() || errors.size() == sources.size()) {
			if (!errors.isEmpty()) {
				throw errors.getFirst();
			}
		}
		if (result.isEmpty() && notFoundException != null) {
			throw notFoundException;
		}

		if (!result.isEmpty() && currentVersion != null) {
			result.add(Release.of(currentVersion));
		}

		return new ArrayList<>(result);
	}

	private static void cancelRemaining(List<Future<List<Release>>> futures, int from) {
		for (int j = from; j < futures.size(); j++) {
			futures.get(j).cancel(true);
		}
	}

}
