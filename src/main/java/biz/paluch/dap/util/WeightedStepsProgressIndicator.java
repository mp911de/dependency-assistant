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

package biz.paluch.dap.util;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

import com.intellij.ide.util.DelegatingProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Progress divided into relative step weights. Weights need not sum to one. For
 * example, {@code (1, 9)} assigns 10% to the first step and 90% to the second.
 *
 * <p>Step advancement is atomic, so concurrent advances are not lost. Parent
 * fraction writes are not serialized and may race with step advances.
 *
 * @author Mark Paluch
 * @see StepsProgressIndicator
 */
public class WeightedStepsProgressIndicator extends DelegatingProgressIndicator implements StepsProgressIndicator {

	private static final AtomicIntegerFieldUpdater<WeightedStepsProgressIndicator> CURRENT_STEP = AtomicIntegerFieldUpdater
			.newUpdater(WeightedStepsProgressIndicator.class, "currentStep");

	private final double[] weights;

	private final double[] completedWeights;

	private final double totalWeight;

	private volatile int currentStep;

	/**
	 * Copy the relative weights in step order.
	 *
	 * @throws IllegalArgumentException if no weights are supplied or a weight is
	 * zero or negative.
	 */
	public WeightedStepsProgressIndicator(ProgressIndicator indicator, double... weights) {

		super(indicator);
		if (weights.length == 0) {
			throw new IllegalArgumentException("At least one step weight is required");
		}

		double[] completed = new double[weights.length + 1];
		double total = 0;
		for (int i = 0; i < weights.length; i++) {
			if (weights[i] <= 0) {
				throw new IllegalArgumentException("Step weights must be greater than 0: " + Arrays.toString(weights));
			}
			total += weights[i];
			completed[i + 1] = total;
		}

		this.weights = weights.clone();
		this.completedWeights = completed;
		this.totalWeight = total;
	}

	/**
	 * Create equally weighted steps with atomic advancement.
	 *
	 * @throws IllegalArgumentException if the task count is not positive.
	 */
	public static WeightedStepsProgressIndicator forTasks(ProgressIndicator indicator, int taskCount) {

		if (taskCount <= 0) {
			throw new IllegalArgumentException("Task count must be greater than 0: " + taskCount);
		}

		double[] weights = new double[taskCount];
		Arrays.fill(weights, 1);
		return new WeightedStepsProgressIndicator(indicator, weights);
	}

	/**
	 * Advance to the next step. Calls beyond the last step keep the parent at full
	 * progress.
	 */
	@Override
	public void nextStep() {
		CURRENT_STEP.updateAndGet(this, step -> Math.min(step + 1, weights.length));
		setFraction(0.0);
	}

	/**
	 * Map current-step progress to its parent share, clamping the fraction to
	 * {@code [0, 1]}.
	 */
	@Override
	public void setFraction(double fraction) {

		int step = CURRENT_STEP.get(this);
		double completed = completedWeights[step];
		double current = step < weights.length ? weights[step] : 0;
		double clamped = Math.clamp(fraction, 0, 1);
		super.setFraction((completed + current * clamped) / totalWeight);
	}

}
