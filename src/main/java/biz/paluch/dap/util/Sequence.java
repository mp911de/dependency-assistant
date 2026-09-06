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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A finite sequence that supports repeated traversal.
 *
 * @author Mark Paluch
 */
public interface Sequence<T> extends Iterable<T> {

	/**
	 * Map eagerly into an immutable sequence in encounter order.
	 */
	default <R> Sequence<R> map(Function<? super T, ? extends R> mapper) {
		return Sequence.of(stream().map(mapper).toList());
	}

	default boolean isEmpty() {
		return !iterator().hasNext();
	}

	/**
	 * Return a new sequential stream over this sequence.
	 */
	default Stream<T> stream() {
		return StreamSupport.stream(spliterator(), false);
	}

	/**
	 * Return the elements in encounter order. The default returns an unmodifiable
	 * snapshot.
	 */
	default List<T> toList() {
		return stream().toList();
	}

	static <T> Sequence<T> empty() {
		return DefaultSequence.empty();
	}

	/**
	 * Copy the elements into an immutable sequence in encounter order.
	 */
	@SafeVarargs
	static <T> Sequence<T> of(T... items) {

		if (items.length == 0) {
			return empty();
		}
		return new DefaultSequence<>(List.of(items));
	}

	/**
	 * Consume the iterable immediately into an immutable snapshot in encounter
	 * order.
	 */
	static <T> Sequence<T> of(Iterable<? extends T> items) {

		List<T> copy = new ArrayList<>();
		for (T item : items) {
			copy.add(item);
		}

		return copy.isEmpty() ? empty() : new DefaultSequence<>(copy);
	}

}
