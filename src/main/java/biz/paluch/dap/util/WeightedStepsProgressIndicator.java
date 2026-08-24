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
 * Progress indicator that divides a parent indicator into weighted steps.
 *
 * <p>This is a weighted variant of the platform
 * {@link com.intellij.util.progress.StepsProgressIndicator}: instead of equally
 * sized steps it accepts one weight per step, and the fraction reported to the
 * parent is the completed weight plus the current step's weighted share,
 * divided by the total weight. A child fraction in {@code [0, 1]} therefore
 * advances the parent only across the current step's slice. Weights are
 * relative and need not sum to {@code 1}. {@code (0.1, 0.9)} and {@code (1, 9)}
 * behave identically.
 *
 * <pre class="code">
 * parent.setIndeterminate(false);
 * WeightedStepsProgressIndicator steps = new WeightedStepsProgressIndicator(parent, 0.1, 0.9);
 * scan(steps);             // owns the first 10%
 * steps.nextStep();
 * resolve(steps);          // owns the remaining 90%
 * </pre>
 *
 * <p>{@link #nextStep()} advances an atomic step counter, so concurrent step
 * advances are not lost. Parent fraction writes are not serialized. When step
 * advances and fraction updates race, the parent reflects whichever update is
 * written last.
 *
 * @author Mark Paluch
 * @see StepsProgressIndicator
 * @see com.intellij.util.progress.StepsProgressIndicator
 */
public class WeightedStepsProgressIndicator extends DelegatingProgressIndicator implements StepsProgressIndicator {

	private static final AtomicIntegerFieldUpdater<WeightedStepsProgressIndicator> CURRENT_STEP = AtomicIntegerFieldUpdater
			.newUpdater(WeightedStepsProgressIndicator.class, "currentStep");

	private final double[] weights;

	private final double[] completedWeights;

	private final double totalWeight;

	private volatile int currentStep;

	/**
	 * Create a stepped indicator over the given parent.
	 * @param indicator the indicator to forward to.
	 * @param weights the relative weight of each step in encounter order. The array
	 * must contain at least one entry; zero and negative weights are rejected.
	 * Relative weights are not required to sum to {@code 1}. {@code (0.1, 0.9)} and
	 * {@code (1, 9)} behave identically. The array is copied.
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
	 * Create a stepped indicator with {@code taskCount} equally weighted steps.
	 *
	 * <p>This is the atomic-step counterpart to the platform
	 * {@code StepsProgressIndicator}: each {@link #nextStep()} advances the parent
	 * fraction by {@code 1 / taskCount}.
	 * @param indicator the indicator to forward to.
	 * @param taskCount the number of equally weighted steps. It must be greater
	 * than {@code 0}.
	 * @return a stepped indicator over {@code taskCount} equally weighted steps.
	 * @throws IllegalArgumentException if {@code taskCount} is not positive.
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
	 * Advance to the next step, reporting the parent fraction at its start.
	 *
	 * <p>Calls beyond the last step are ignored and leave the indicator reporting a
	 * full parent fraction.
	 */
	@Override
	public void nextStep() {
		CURRENT_STEP.updateAndGet(this, step -> Math.min(step + 1, weights.length));
		setFraction(0.0);
	}

	/**
	 * Report progress within the current step.
	 *
	 * <p>The supplied fraction is clamped to {@code [0, 1]} before it is mapped to
	 * the current step's share of the parent indicator.
	 *
	 * @param fraction the fraction within the current step.
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
