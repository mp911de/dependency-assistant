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

package biz.paluch.dap.util;

import java.util.List;
import java.util.stream.Stream;

/**
 * Contract for domain types that carry a finite sequence of elements.
 *
 * <p>{@code Sequence} unifies the container surface of value objects holding an
 * ordered collection of elements: iteration through {@link Iterable}, lazy
 * consumption through {@link #stream()}, snapshot conversion through
 * {@link #toList()}, and an emptiness check through {@link #isEmpty()}.
 *
 * <p>Implementations provide {@link #iterator()} and {@link #stream()}. Both
 * must be repeatable so a sequence can be consumed multiple times. The
 * {@link #isEmpty()} and {@link #toList()} defaults derive from these methods;
 * override them when the backing collection offers a cheaper form.
 *
 * @param <T> the element type.
 * @author Mark Paluch
 * @see Stream
 */
public interface Sequence<T> extends Iterable<T> {

	/**
	 * Return a sequential {@link Stream} over the elements of this sequence.
	 *
	 * <p>Each invocation returns a new stream; the sequence itself is not consumed.
	 *
	 * @return a new stream over the elements.
	 */
	Stream<T> stream();

	/**
	 * Return whether this sequence contains no elements.
	 *
	 * <p>The default implementation obtains an {@link #iterator()} and probes it
	 * for a first element.
	 *
	 * @return {@literal true} if the sequence contains no elements;
	 * {@literal false} otherwise.
	 */
	default boolean isEmpty() {
		return !iterator().hasNext();
	}

	/**
	 * Return the elements of this sequence as an immutable {@link List}.
	 *
	 * <p>The default implementation collects {@link #stream()} into an unmodifiable
	 * snapshot.
	 *
	 * @return the elements as an immutable list.
	 */
	default List<T> toList() {
		return stream().toList();
	}

}
