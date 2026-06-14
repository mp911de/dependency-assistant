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

package biz.paluch.dap.assistant;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyfileTemplate}.
 *
 * @author Mark Paluch
 */
class DependencyfileTemplateUnitTests {

	@Test
	void rendersKnownArtifactsAsUnconstrainedRules() {

		String content = DependencyfileTemplate.render(List.of(
				ArtifactId.of("org.springframework", "spring-core"),
				ArtifactId.of("axios", "axios"),
				ArtifactId.of("org.springframework", "spring-core")));

		assertThat(content).isEqualTo("""
				{
				  "artifacts": {
				    "axios": {
				      "name": "axios"
				    },
				    "org.springframework:spring-core": {
				      "name": "org.springframework:spring-core"
				    }
				  }
				}
				""");
	}

	@Test
	void rendersEmptyArtifactObjectWhenNoArtifactsAreKnown() {

		String content = DependencyfileTemplate.render(List.of());

		assertThat(content).isEqualTo("""
				{
				  "artifacts": {}
				}
				""");
	}

}
