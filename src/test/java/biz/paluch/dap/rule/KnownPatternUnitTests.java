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

package biz.paluch.dap.rule;

import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link KnownPattern}.
 *
 * @author Mark Paluch
 */
class KnownPatternUnitTests {

	@Test
	void matchesLiteralExactly() {

		assertThat(KnownPattern.of("spring-core")).accepts("spring-core");
		assertThat(KnownPattern.of("spring-core")).rejects("spring-core-x", "x-spring-core", "spring");
	}

	@Test
	void matchesPrefixAndSuffixWildcards() {

		assertThat(KnownPattern.of("spring-*")).accepts("spring-", "spring-core", "spring-boot-starter");
		assertThat(KnownPattern.of("spring-*")).rejects("spring", "xspring-core");
		assertThat(KnownPattern.of("*-bom")).accepts("junit-bom", "-bom");
		assertThat(KnownPattern.of("*-bom")).rejects("bom", "junit-bom-x");
	}

	@Test
	void matchesInnerWildcards() {

		assertThat(KnownPattern.of("spring-*-starter")).accepts("spring-boot-starter", "spring--starter");
		assertThat(KnownPattern.of("spring-*-starter")).rejects("spring-starter", "spring-boot-starter-web");
		assertThat(KnownPattern.of("*data*mongo*")).accepts("spring-data-mongodb", "datamongo");
		assertThat(KnownPattern.of("*data*mongo*")).rejects("mongo-data");
	}

	@Test
	void matchesAnyAndCollapsedWildcards() {

		assertThat(KnownPattern.ANY).accepts("", "anything");
		assertThat(KnownPattern.of("**")).accepts("", "anything");
		assertThat(KnownPattern.of("a**b")).accepts("ab", "a-x-b");
	}

	@Test
	void rejectsOverlappingSegmentMatches() {

		assertThat(KnownPattern.of("a*a")).rejects("a");
		assertThat(KnownPattern.of("a*a")).accepts("aa", "aba");
		assertThat(KnownPattern.of("*aa*aa*")).rejects("aaa");
		assertThat(KnownPattern.of("*aa*aa*")).accepts("aaaa");
	}

	@Test
	@Timeout(value = 2, unit = TimeUnit.SECONDS)
	void matchesManyWildcardsWithoutBacktracking() {

		// A near-miss carries one literal too few, the worst case for a backtracking
		// matcher.
		String pattern = "*a".repeat(30) + "*";
		String nearMiss = ("b".repeat(200) + "a").repeat(29);

		assertThat(KnownPattern.of(pattern)).accepts("a".repeat(30), nearMiss + "a").rejects(nearMiss);
	}

}
