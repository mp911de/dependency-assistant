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

package biz.paluch.dap.plan;

import biz.paluch.dap.util.MessageBundle;
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
		assertThat(MessageBundle.message("plan.create-tickets.selected.description", 3))
				.isEqualTo("Create tickets for 3 selected upgrades");
	}

	@Test
	void describesWholePlanScope() {
		assertThat(MessageBundle.message("plan.create-tickets.all.description", 2))
				.isEqualTo("Create tickets for 2 planned upgrades without tickets");
	}

	@Test
	void describesCompletedScope() {
		assertThat(MessageBundle.message("plan.create-tickets.selected.description", 0))
				.isEqualTo("All selected upgrades already have tickets");
	}

}
