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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * Contract for domain types that carry a finite sequence of elements.
 *
 * <p>{@code Sequence} unifies the container surface of value objects holding an
 * ordered collection of elements: iteration through {@link Iterable}, lazy
 * consumption through {@link #stream()}, snapshot conversion through
 * {@link #toList()}, and an emptiness check through {@link #isEmpty()}.
 *
 * <p>Implementations provide a repeatable {@link #iterator()} so a sequence can
 * be consumed multiple times. The {@link #stream()}, {@link #isEmpty()}, and
 * {@link #toList()} defaults derive from iteration; override them when the
 * backing collection offers a cheaper form.
 *
 * @author Mark Paluch
 * @param <T> the element type.
 * @see Stream
 */
public interface Sequence<T> extends Iterable<T> {

	/**
	 * Transform the elements of this sequence while preserving encounter order.
	 *
	 * @param <R> the mapped element type.
	 * @param mapper the function applied to each element.
	 * @return an immutable sequence containing the mapped elements.
	 */
	default <R> Sequence<R> map(Function<? super T, ? extends R> mapper) {
		return Sequence.of(stream().map(mapper).toList());
	}

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
	 * Return a sequential {@link Stream} over the elements of this sequence.
	 *
	 * <p>Each invocation returns a new stream.
	 *
	 * @return a new stream over the elements.
	 */
	default Stream<T> stream() {
		return StreamSupport.stream(spliterator(), false);
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

	/**
	 * Return an empty sequence.
	 *
	 * @param <T> the element type.
	 * @return the shared immutable empty sequence.
	 */
	static <T> Sequence<T> empty() {
		return DefaultSequence.empty();
	}

	/**
	 * Create a sequence containing the given elements in encounter order.
	 *
	 * <p>The array is copied and is not retained by the sequence.
	 *
	 * @param <T> the element type.
	 * @param items the elements to include.
	 * @return an immutable sequence containing the elements.
	 */
	@SafeVarargs
	static <T> Sequence<T> of(T... items) {

		if (items.length == 0) {
			return empty();
		}
		return new DefaultSequence<>(List.of(items));
	}

	/**
	 * Create a sequence from the elements supplied by the iterable.
	 *
	 * <p>The iterable is consumed immediately into an immutable snapshot and is not
	 * retained by the sequence.
	 *
	 * @param <T> the element type.
	 * @param items the elements to include.
	 * @return an immutable sequence containing the elements in encounter order.
	 */
	static <T> Sequence<T> of(Iterable<? extends T> items) {

		List<T> copy = new ArrayList<>();
		for (T item : items) {
			copy.add(item);
		}

		return copy.isEmpty() ? empty() : new DefaultSequence<>(copy);
	}

}
