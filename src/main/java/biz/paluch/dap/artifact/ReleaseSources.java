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

package biz.paluch.dap.artifact;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.function.Predicate;

import biz.paluch.dap.util.Sequence;

/**
 * Release lookup inputs for one artifact: the artifact coordinates together
 * with the sources that can provide its versions.
 *
 * @author Mark Paluch
 * @param pkg the package identity whose releases should be resolved.
 * @param sources the release sources that can provide versions for the
 * artifact.
 * @see ReleaseSource
 */
public record ReleaseSources(PackageIdentity pkg, Collection<ReleaseSource> sources)
		implements Sequence<ReleaseSource> {

	/**
	 * Create a new {@link ReleaseSources} instance.
	 * @param pkg the package associated with the release sources.
	 * @param sources the release sources.
	 * @return the release sources.
	 */
	public static ReleaseSources of(PackageIdentity pkg, Collection<ReleaseSource> sources) {

		List<ReleaseSource> list = new ArrayList<>(sources.size());
		for (ReleaseSource source : sources) {
			ReleaseSource sourceToUse = source instanceof ReleaseSourceRegistry registry
					? registry.getReleaseSource(pkg.getArtifactId())
					: source;
			list.add(sourceToUse);
		}
		return new ReleaseSources(pkg, list);
	}

	/**
	 * Return the artifact coordinates of {@link #pkg()}.
	 */
	public ArtifactId artifactId() {
		return pkg.getArtifactId();
	}

	/**
	 * Return the package ecosystem of {@link #pkg()}.
	 */
	public PackageSystem packageSystem() {
		return pkg.getPackageSystem();
	}

	/**
	 * Retain only the sources accepted by the given predicate.
	 * @param predicate the predicate selecting the sources to query.
	 * @return release sources narrowed to the accepted sources, possibly empty.
	 */
	public ReleaseSources filter(Predicate<ReleaseSource> predicate) {
		return ReleaseSources.of(pkg, sources.stream().filter(predicate).toList());
	}

	/**
	 * Return the identifiers of the configured release sources in encounter order.
	 * @return the source identifiers.
	 */
	public Collection<String> sourceIds() {
		List<String> ids = new ArrayList<>(sources.size());
		for (ReleaseSource releaseSource : sources) {
			ids.add(releaseSource.getId());
		}
		return ids;
	}

	/**
	 * Check whether the release sources contain only the given
	 * {@link ReleaseSource#getId() identifiers}.
	 * @param ids collection of release source identifiers.
	 * @return {@code true} if the source count matches and every source identifier
	 * is contained in {@code ids}.
	 */
	public boolean containsOnlyReleaseSourceIds(Collection<String> ids) {
		if (ids.size() != sources.size()) {
			return false;
		}
		for (ReleaseSource source : sources) {
			if (!ids.contains(source.getId())) {
				return false;
			}
		}
		return true;
	}

	@Override
	public Iterator<ReleaseSource> iterator() {
		return sources.iterator();
	}

	@Override
	public boolean isEmpty() {
		return sources.isEmpty();
	}

}
