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

	public List<Release> getReleases(ArtifactId artifactId) {
		return getReleases(artifactId, null);
	}

	public List<Release> getReleases(ArtifactId artifactId,
			@Nullable ArtifactVersion currentVersion) {

		Set<Release> result = new TreeSet<>(Comparator.<Release>naturalOrder().reversed());
		List<Future<List<Release>>> futures = new ArrayList<>();
		for (ReleaseSource source : sources) {
			Future<List<Release>> future = executor.submit(() -> source.getReleases(artifactId));
			futures.add(future);
		}

		List<RuntimeException> errors = new ArrayList<>();
		ArtifactNotFoundException notFoundException = null;

		for (Future<List<Release>> future : futures) {
			try {
				List<Release> releases = future.get();
				result.addAll(releases);
			} catch (InterruptedException e) {
				return new ArrayList<>(result);
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

}
