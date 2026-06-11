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

package biz.paluch.dap.rule;

import biz.paluch.dap.artifact.ArtifactVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Generations}.
 *
 * @author Mark Paluch
 */
class GenerationsUnitTests {

	@Test
	void matchesAnyListedGeneration() {

		Generations generations = Generations.of("1.0.x", "1.1.x");

		assertThat(generations).accepts("1.0.5").accepts("1.1.2");
		assertThat(generations).rejects("1.2.0").rejects("0.9.1");
	}

	@Test
	void unconstrainedMatchesAnything() {

		assertThat(Generations.unconstrained()).accepts("6.1.0").accepts("foo");
		assertThat(Generations.unconstrained().isConstrained()).isFalse();
	}

	@Test
	void emptyInputIsUnconstrained() {
		assertThat(Generations.of().isConstrained()).isFalse();
	}

	@Test
	void listedGenerationsAreConstrained() {
		assertThat(Generations.of("1.0.x").isConstrained()).isTrue();
	}

	@Test
	void rendersSingleGenerationNormalized() {
		assertThat(Generations.of("3.2")).hasToString("3.2.x");
	}

	@Test
	void rendersTwoGenerationsWithOr() {
		assertThat(Generations.of("3.2.x", "4")).hasToString("3.2.x or 4.x");
	}

	@Test
	void rendersThreeGenerationsOxfordStyle() {
		assertThat(Generations.of("3.1.x", "3.2.x", "4")).hasToString("3.1.x, 3.2.x, or 4.x");
	}

	@Test
	void rendersUnconstrainedAsEmptyValue() {
		assertThat(Generations.unconstrained().value()).isEmpty();
	}

	@Test
	void collapsesWildcardToUnconstrained() {

		Generations generations = Generations.of("*", "1.0.x");

		assertThat(generations.isConstrained()).isFalse();
		assertThat(generations).accepts("9.9.9");
	}

	@Test
	void dropsDuplicatesPreservingDeclaredOrder() {
		assertThat(Generations.of("1.1", "1.0.x", "1.0")).hasToString("1.1.x or 1.0.x");
	}

	@Test
	void testsInnerMostVersionAgainstAnyGeneration() {

		Generations generations = Generations.of("1.2", "2.0");

		assertThat(generations.asVersionPredicate())
				.accepts(ArtifactVersion.of("v1.2.3"), ArtifactVersion.of("2.0.1"))
				.rejects(ArtifactVersion.of("v1.20.0"));
	}

}
