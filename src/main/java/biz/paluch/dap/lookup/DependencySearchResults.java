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

package biz.paluch.dap.lookup;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import biz.paluch.dap.util.Sequence;

/**
 * Ordered dependency sites found within a file or aggregated across files.
 *
 * @author Mark Paluch
 * @see ArtifactReferenceResolver#search(DependencySiteQuery)
 */
public class DependencySearchResults implements Sequence<DependencySiteSearchHit> {

	private static final DependencySearchResults EMPTY = new DependencySearchResults(List.of());

	private final List<DependencySiteSearchHit> hits;

	private DependencySearchResults(List<DependencySiteSearchHit> hits) {
		this.hits = hits;
	}

	public static DependencySearchResults empty() {
		return EMPTY;
	}

	/**
	 * Copy the given hits in iteration order.
	 */
	public static DependencySearchResults of(Iterable<DependencySiteSearchHit> hits) {

		List<DependencySiteSearchHit> copy = new ArrayList<>();
		for (DependencySiteSearchHit hit : hits) {
			copy.add(hit);
		}

		return copy.isEmpty() ? EMPTY : new DependencySearchResults(copy);
	}

	/**
	 * Combine results in encounter order, retaining the first occurrence of each
	 * equal hit.
	 */
	public static DependencySearchResults concat(Iterable<DependencySearchResults> results) {

		Set<DependencySiteSearchHit> hits = new LinkedHashSet<>();
		for (DependencySearchResults result : results) {
			hits.addAll(result.hits);
		}

		return hits.isEmpty() ? EMPTY : new DependencySearchResults(List.copyOf(hits));
	}

	@Override
	public boolean isEmpty() {
		return hits.isEmpty();
	}

	@Override
	public Iterator<DependencySiteSearchHit> iterator() {
		return hits.iterator();
	}

	@Override
	public Stream<DependencySiteSearchHit> stream() {
		return hits.stream();
	}

}
