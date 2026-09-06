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
 * Package identity and sources to query for its releases.
 *
 * @author Mark Paluch
 */
public record ReleaseSources(PackageIdentity pkg, Collection<ReleaseSource> sources)
		implements Sequence<ReleaseSource> {

	/**
	 * Resolve source registries for this package and copy the resulting sources.
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

	public ArtifactId artifactId() {
		return pkg.getArtifactId();
	}

	public PackageSystem packageSystem() {
		return pkg.getPackageSystem();
	}

	/**
	 * Return a selection containing only sources accepted by the predicate.
	 */
	public ReleaseSources filter(Predicate<ReleaseSource> predicate) {
		return ReleaseSources.of(pkg, sources.stream().filter(predicate).toList());
	}

	/**
	 * Return source identifiers in encounter order.
	 */
	public Collection<String> sourceIds() {
		List<String> ids = new ArrayList<>(sources.size());
		for (ReleaseSource releaseSource : sources) {
			ids.add(releaseSource.getId());
		}
		return ids;
	}

	/**
	 * Return whether the source count matches and every source identifier occurs in
	 * {@code ids}.
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
