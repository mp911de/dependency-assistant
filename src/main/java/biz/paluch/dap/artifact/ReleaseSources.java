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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Release lookup inputs for one artifact: the artifact coordinates together
 * with the sources that can provide its versions.
 *
 * @author Mark Paluch
 * @param artifactId the artifact whose releases should be resolved.
 * @param sources the release sources that can provide versions for the
 * artifact.
 * @see ReleaseSource
 */
public record ReleaseSources(ArtifactId artifactId, PackageSystem packageSystem, Collection<ReleaseSource> sources) {

	/**
	 * Retain only the sources accepted by the given predicate. If the predicate
	 * would reject every source, the original sources are kept so the artifact is
	 * still queried rather than starved.
	 * @param predicate the predicate selecting the sources to query; must not be
	 * {@literal null}.
	 * @return release sources narrowed to the accepted sources, or {@code this}
	 * when none would remain.
	 */
	public ReleaseSources filter(Predicate<ReleaseSource> predicate) {
		List<ReleaseSource> filtered = sources.stream().filter(predicate).toList();
		return filtered.isEmpty() ? this : new ReleaseSources(artifactId, packageSystem, filtered);
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
	 * @param ids collection of release source identifers.
	 * @return {@literal true} if the sources contain only the given identifiers.
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

}
