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

package biz.paluch.dap.lookup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import biz.paluch.dap.util.Sequence;

/**
 * Immutable, ordered collection of the {@link DependencySiteSearchHit hits} a
 * Dependency Site Find located.
 *
 * <p>Results carry no query of their own; they are the outcome of
 * {@link ArtifactReferenceResolver#search(DependencySiteQuery)} for one file,
 * of {@link ArtifactReferenceResolver#inlineDefinitions inlineDefinitions} for
 * the inline-only find, or of {@link #concat(Iterable)} aggregating several
 * per-file results.
 *
 * @author Mark Paluch
 * @see DependencySiteSearchHit
 * @see ArtifactReferenceResolver#search(DependencySiteQuery)
 */
public class DependencySearchResults implements Sequence<DependencySiteSearchHit> {

	private static final DependencySearchResults EMPTY = new DependencySearchResults(List.of());

	private final List<DependencySiteSearchHit> hits;

	private DependencySearchResults(List<DependencySiteSearchHit> hits) {
		this.hits = hits;
	}

	/**
	 * Return the shared empty results instance.
	 *
	 * @return the empty results (shared, immutable).
	 */
	public static DependencySearchResults empty() {
		return EMPTY;
	}

	/**
	 * Create results wrapping the given hits in iteration order.
	 *
	 * @param hits the located hits, may be empty.
	 * @return the results holding a defensive copy of the hits; never
	 * {@literal null}.
	 */
	public static DependencySearchResults of(Iterable<DependencySiteSearchHit> hits) {

		List<DependencySiteSearchHit> copy = new ArrayList<>();
		for (DependencySiteSearchHit hit : hits) {
			copy.add(hit);
		}

		return copy.isEmpty() ? EMPTY : new DependencySearchResults(copy);
	}

	/**
	 * Combine several per-file results into one, unioning their hits in encounter
	 * order and dropping sites seen through more than one file.
	 *
	 * @param results the per-file results to aggregate; must not be
	 * {@literal null}.
	 * @return the deduplicated union of all hits.
	 */
	public static DependencySearchResults concat(Iterable<DependencySearchResults> results) {

		Set<DependencySiteSearchHit> hits = new LinkedHashSet<>();
		for (DependencySearchResults result : results) {
			hits.addAll(result.hits);
		}

		return hits.isEmpty() ? EMPTY : new DependencySearchResults(List.copyOf(hits));
	}

	/**
	 * Return whether no hits were located.
	 *
	 * @return {@literal true} if there are no hits; {@literal false} otherwise.
	 */
	@Override
	public boolean isEmpty() {
		return hits.isEmpty();
	}

	@Override
	public Iterator<DependencySiteSearchHit> iterator() {
		return hits.iterator();
	}

	/**
	 * Return a stream of the hits in iteration order.
	 *
	 * @return a stream over the hits.
	 */
	@Override
	public Stream<DependencySiteSearchHit> stream() {
		return hits.stream();
	}

}
