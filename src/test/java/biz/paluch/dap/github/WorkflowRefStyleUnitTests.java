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

package biz.paluch.dap.github;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link WorkflowRefStyle}.
 *
 * @author Mark Paluch
 */
class WorkflowRefStyleUnitTests {

	private static final String FULL_SHA = "be666c2fcd27ec809703dec50e508c2fdc7f6654";

	@Test
	void classifiesFullSha() {
		assertThat(WorkflowRefStyle.from(FULL_SHA)).isEqualTo(WorkflowRefStyle.SHA);
	}

	@Test
	void defaultsToSha() {
		assertThat(WorkflowRefStyle.from("")).isEqualTo(WorkflowRefStyle.SHA);
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.2.3", "4", "main", "1.2.3-alpha"})
	void classifiesPlainVersion(String ref) {
		assertThat(WorkflowRefStyle.from(ref)).isEqualTo(WorkflowRefStyle.VERSION);
	}

}
