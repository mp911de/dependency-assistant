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

package biz.paluch.dap.util;

import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StepsProgressIndicator}.
 *
 * @author Mark Paluch
 */
class StepsProgressIndicatorTests {

	@Test
	void forStepsWrapsPlatformIndicatorWithEqualSteps() {

		AbstractProgressIndicatorBase parent = new AbstractProgressIndicatorBase();
		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(parent, 4);

		steps.nextStep();
		assertThat(parent.getFraction()).isEqualTo(0.25);

		steps.nextStep();
		assertThat(parent.getFraction()).isEqualTo(0.5);
	}

	@Test
	void weightedIndicatorIsAStepsProgressIndicator() {

		AbstractProgressIndicatorBase parent = new AbstractProgressIndicatorBase();

		assertThat(WeightedStepsProgressIndicator.forTasks(parent, 2)).isInstanceOf(StepsProgressIndicator.class);
	}

}
