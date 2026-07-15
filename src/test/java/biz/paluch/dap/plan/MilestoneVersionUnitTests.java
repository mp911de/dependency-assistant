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

import biz.paluch.dap.plan.MilestoneComparator.MilestoneVersion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MilestoneVersion}.
 *
 * @author Mark Paluch
 */
class MilestoneVersionUnitTests {

	@Test
	void ordersOlderBeforeNewer() {

		assertThat(MilestoneVersion.of("4.5")
				.compareTo(MilestoneVersion.of("4.6"))).isNegative();
	}

	@Test
	void readsVersionPrecededByTitleText() {

		assertThat(MilestoneVersion.of("Spring Data 2025.1.3")
				.compareTo(MilestoneVersion.of("Spring Data 2025.1.4"))).isNegative();
	}

	@Test
	void readsVersionThroughVPrefix() {

		assertThat(MilestoneVersion.of("v4.6")
				.compareTo(MilestoneVersion.of("4.5"))).isPositive();
	}

	@Test
	void titleWithoutVersionIsIndistinguishable() {

		assertThat(MilestoneVersion.of("Backlog")
				.compareTo(MilestoneVersion.of("4.5"))).isZero();
	}

	/**
	 * Anchoring on the token start is what keeps the trailing digits of
	 * {@code Turing-SR1} from reading as a version.
	 */
	@Test
	void digitsInsideWordsDoNotRead() {

		assertThat(MilestoneVersion.of("Turing-SR1")
				.compareTo(MilestoneVersion.of("Turing-SR2"))).isZero();
	}

}
