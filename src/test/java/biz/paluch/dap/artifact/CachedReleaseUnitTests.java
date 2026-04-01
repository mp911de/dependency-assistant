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
package biz.paluch.dap.artifact;

import static org.assertj.core.api.Assertions.*;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link Release}.
 *
 * @author Mark Paluch
 */
class CachedReleaseUnitTests {

	@Test
	void shouldCompareVersions() {

		Release train = new Release(ArtifactVersion.of("Aluminium-SR1"),
				LocalDateTime.parse("2017-01-01T00:00:00"));
		Release v1 = new Release(ArtifactVersion.of("2020.0.0"), LocalDateTime.parse("2019-01-01T00:00:00"));
		Release v2 = new Release(ArtifactVersion.of("2020.0.1"), LocalDateTime.parse("2019-01-01T00:00:00"));

		assertThat(train).isLessThan(v1);
		assertThat(v1).isGreaterThan(train).isLessThan(v2);
	}

}
