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

import com.intellij.openapi.progress.ProgressIndicator;

/**
 * {@link ProgressIndicator} that advances a parent indicator through a sequence
 * of steps.
 *
 * <p>A child fraction in {@code [0, 1]} reflects progress within the current
 * step; {@link #nextStep()} commits the current step and moves to the next one.
 * The mapping from steps to the parent fraction is left to the implementation,
 * which may weight steps equally or individually.
 *
 * <p>{@link WeightedStepsProgressIndicator} is the thread-safe implementation
 * and supports per-step weights. {@link #forSteps(ProgressIndicator, int)}
 * adapts the platform equal-step indicator for callers that do not need thread
 * safety.
 *
 * @author Mark Paluch
 * @see WeightedStepsProgressIndicator
 */
public interface StepsProgressIndicator extends ProgressIndicator {

	/**
	 * Commit the current step and advance to the next one, reporting the parent
	 * fraction at its start.
	 */
	void nextStep();

	/**
	 * Adapt the platform {@code StepsProgressIndicator} to this interface, dividing
	 * the parent into {@code totalSteps} equally sized steps.
	 *
	 * <p>The returned indicator is not thread-safe; advance steps from a single
	 * thread. Use
	 * {@link WeightedStepsProgressIndicator#forTasks(ProgressIndicator, int)} for a
	 * thread-safe equal-step indicator.
	 * @param indicator the indicator to forward to; must not be {@literal null}.
	 * @param totalSteps the number of equally sized steps.
	 * @return a steps indicator backed by the platform implementation.
	 */
	static StepsProgressIndicator forSteps(ProgressIndicator indicator, int totalSteps) {
		return new StepsProgressIndicatorWrapper(
				new com.intellij.util.progress.StepsProgressIndicator(indicator, totalSteps));
	}

}
