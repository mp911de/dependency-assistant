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
package biz.paluch.dap.gradle;

import static org.assertj.core.api.Assertions.*;

import biz.paluch.dap.ProjectId;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link ProjectId}.
 *
 * @author Mark Paluch
 */
class ProjectIdUnitTests {

	@Test
	void factoryMethodCreatesIdentity() {

		ProjectId id = ProjectId.of("com.example", "my-app");

		assertThat(id.groupId()).isEqualTo("com.example");
		assertThat(id.artifactId()).isEqualTo("my-app");
	}

	@Test
	void recordEquality() {

		ProjectId a = ProjectId.of("com.example", "artifact");
		ProjectId b = ProjectId.of("com.example", "artifact");
		ProjectId c = ProjectId.of("com.example", "other");

		assertThat(a).isEqualTo(b);
		assertThat(a).isNotEqualTo(c);
	}

	@Test
	void usableAsMapKey() {

		ProjectId key1 = ProjectId.of("com.example", "artifact");
		ProjectId key2 = ProjectId.of("com.example", "artifact");

		Map<ProjectId, String> map = new HashMap<>();
		map.put(key1, "value");

		assertThat(map.get(key2)).isEqualTo("value");
	}

	@Test
	void hashCodeConsistentWithEquals() {

		ProjectId a = ProjectId.of("com.example", "artifact");
		ProjectId b = ProjectId.of("com.example", "artifact");

		assertThat(a.hashCode()).isEqualTo(b.hashCode());
	}

}
