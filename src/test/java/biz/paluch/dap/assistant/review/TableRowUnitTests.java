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

package biz.paluch.dap.assistant.review;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.fixtures.TestCandidates;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TableRow}.
 *
 * @author Mark Paluch
 */
class TableRowUnitTests {

	private static final ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

	private static final ArtifactId SPRING_TEST = ArtifactId.of("org.springframework", "spring-test");

	@Test
	void representsMatchesRowByPackageIdentity() {

		TableRow core = candidate(SPRING_CORE);
		TableRow test = candidate(SPRING_TEST);

		assertThat(core.represents(core.getPackageIdentity())).isTrue();
		assertThat(core.represents(test.getPackageIdentity())).isFalse();
	}

	@Test
	void groupRowRepresentsItsMembers() {

		TableRow core = candidate(SPRING_CORE);
		TableRow test = candidate(SPRING_TEST);
		GroupRow group = GroupRow.governed(core, test);

		assertThat(group.represents(test.getPackageIdentity())).isTrue();
		assertThat(group.represents(candidate(ArtifactId.of("org.springframework", "spring-web"))
				.getPackageIdentity())).isFalse();
	}

	private static TableRow candidate(ArtifactId artifactId) {
		return new TableRow(TestCandidates.candidate(artifactId, "6.2.0", it -> it.releases("6.2.0", "6.2.1")));
	}

}
