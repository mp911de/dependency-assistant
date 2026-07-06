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

package biz.paluch.dap.plan;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link CreateTicketsAction}.
 *
 * @author Mark Paluch
 */
class CreateTicketsActionUnitTests {

	@Test
	void describesSelectedUpgradeScope() {
		assertThat(CreateTicketsAction.description(3, true))
				.isEqualTo("Create tickets for 3 selected upgrades");
	}

	@Test
	void describesWholePlanScope() {
		assertThat(CreateTicketsAction.description(2, false))
				.isEqualTo("Create tickets for 2 planned upgrades without tickets");
	}

	@Test
	void describesCompletedScope() {
		assertThat(CreateTicketsAction.description(0, true))
				.isEqualTo("All selected upgrades already have tickets");
	}

}
