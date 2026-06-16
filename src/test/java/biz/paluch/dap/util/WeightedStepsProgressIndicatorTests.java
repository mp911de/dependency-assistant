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

import java.util.Arrays;
import java.util.concurrent.CountDownLatch;

import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link WeightedStepsProgressIndicator}.
 *
 * @author Mark Paluch
 */
class WeightedStepsProgressIndicatorTests {

	@Test
	void mapsFractionWithinCurrentStep() {

		AbstractProgressIndicatorBase parent = new AbstractProgressIndicatorBase();
		WeightedStepsProgressIndicator indicator = new WeightedStepsProgressIndicator(parent, 0.1, 0.9);

		indicator.setFraction(0.5);
		assertThat(parent.getFraction()).isEqualTo(0.05);

		indicator.nextStep();
		assertThat(parent.getFraction()).isEqualTo(0.1);

		indicator.setFraction(0.5);
		assertThat(parent.getFraction()).isEqualTo(0.55);

		indicator.setFraction(1.0);
		assertThat(parent.getFraction()).isEqualTo(1.0);
	}

	@Test
	void treatsWeightsAsProportionsOfTheirTotal() {

		AbstractProgressIndicatorBase parent = new AbstractProgressIndicatorBase();
		WeightedStepsProgressIndicator indicator = new WeightedStepsProgressIndicator(parent, 1, 9);

		indicator.setFraction(0.5);
		assertThat(parent.getFraction()).isEqualTo(0.05);

		indicator.nextStep();
		assertThat(parent.getFraction()).isEqualTo(0.1);

		indicator.setFraction(1.0);
		assertThat(parent.getFraction()).isEqualTo(1.0);
	}

	@Test
	void forTasksDistributesEqualWeight() {

		AbstractProgressIndicatorBase parent = new AbstractProgressIndicatorBase();
		WeightedStepsProgressIndicator indicator = WeightedStepsProgressIndicator.forTasks(parent, 4);

		indicator.setFraction(0.5);
		assertThat(parent.getFraction()).isEqualTo(0.125);

		indicator.nextStep();
		assertThat(parent.getFraction()).isEqualTo(0.25);

		indicator.nextStep();
		assertThat(parent.getFraction()).isEqualTo(0.5);
	}

	@Test
	void forTasksRejectsNonPositiveCount() {

		AbstractProgressIndicatorBase parent = new AbstractProgressIndicatorBase();

		assertThatIllegalArgumentException().isThrownBy(() -> WeightedStepsProgressIndicator.forTasks(parent, 0));
	}

	@Test
	void clampsFractionToUnitRange() {

		AbstractProgressIndicatorBase parent = new AbstractProgressIndicatorBase();
		WeightedStepsProgressIndicator indicator = new WeightedStepsProgressIndicator(parent, 0.1, 0.9);

		indicator.setFraction(2.0);
		assertThat(parent.getFraction()).isEqualTo(0.1);

		indicator.setFraction(-1.0);
		assertThat(parent.getFraction()).isZero();
	}

	@Test
	void advancesAtomicallyUnderConcurrentNextStep() throws InterruptedException {

		int steps = 64;
		double[] weights = new double[steps];
		Arrays.fill(weights, 1.0);
		AbstractProgressIndicatorBase parent = new AbstractProgressIndicatorBase();
		WeightedStepsProgressIndicator indicator = new WeightedStepsProgressIndicator(parent, weights);

		CountDownLatch start = new CountDownLatch(1);
		CountDownLatch done = new CountDownLatch(steps);
		for (int i = 0; i < steps; i++) {
			Thread thread = new Thread(() -> {
				try {
					start.await();
					indicator.nextStep();
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
				} finally {
					done.countDown();
				}
			});
			thread.start();
		}
		start.countDown();
		done.await();

		indicator.setFraction(0.0);
		assertThat(parent.getFraction()).isEqualTo(1.0);
	}

	@Test
	void rejectsMissingOrNonPositiveWeights() {

		AbstractProgressIndicatorBase parent = new AbstractProgressIndicatorBase();

		assertThatIllegalArgumentException().isThrownBy(() -> new WeightedStepsProgressIndicator(parent));
		assertThatIllegalArgumentException().isThrownBy(() -> new WeightedStepsProgressIndicator(parent, 0.1, 0.0));
	}

}
