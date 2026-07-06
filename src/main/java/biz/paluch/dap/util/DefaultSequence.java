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

package biz.paluch.dap.util;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Immutable {@link Sequence} backed by a list snapshot.
 *
 * @author Mark Paluch
 */
record DefaultSequence<T>(List<T> items) implements Sequence<T> {

	private static final DefaultSequence<Object> EMPTY = new DefaultSequence<>(List.of());

	DefaultSequence {
		items = List.copyOf(items);
	}

	@SuppressWarnings("unchecked")
	static <T> Sequence<T> empty() {
		return (Sequence<T>) EMPTY;
	}

	@Override
	public boolean isEmpty() {
		return items.isEmpty();
	}

	@Override
	public Stream<T> stream() {
		return items.stream();
	}

	@Override
	public Iterator<T> iterator() {
		return items.iterator();
	}

	@Override
	public List<T> toList() {
		return items;
	}

}
