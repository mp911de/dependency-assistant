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

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Sequence}.
 *
 * @author Mark Paluch
 */
class SequenceUnitTests {

	@Test
	void createsSequenceFromValues() {

		Sequence<String> sequence = Sequence.of("one", "two");

		assertThat(sequence).containsExactly("one", "two");
		assertThat(sequence.toList()).containsExactly("one", "two");
	}

	@Test
	void streamsFromIterationByDefault() {

		Sequence<String> sequence = () -> List.of("one", "two").iterator();

		assertThat(sequence.stream()).containsExactly("one", "two");
	}

	@Test
	void createsImmutableSnapshotFromIterable() {

		List<String> source = new ArrayList<>(List.of("one", "two"));
		Sequence<CharSequence> sequence = Sequence.of(source);

		source.add("three");

		assertThat(sequence).containsExactly("one", "two");
		assertThatExceptionOfType(UnsupportedOperationException.class)
				.isThrownBy(() -> sequence.toList().add("three"));
	}

	@Test
	void mapsIntoAnotherSequence() {

		Sequence<Integer> lengths = Sequence.of("one", "three")
				.map(String::length);

		assertThat(lengths).containsExactly(3, 5);
	}

}
