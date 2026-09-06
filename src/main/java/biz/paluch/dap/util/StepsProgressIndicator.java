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

import com.intellij.openapi.progress.ProgressIndicator;

/**
 * Progress within a sequence of steps. A child fraction describes progress in
 * the current step. Implementations determine each step's share of the parent.
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
	 * Create equally weighted steps with atomic advancement.
	 *
	 * @throws IllegalArgumentException if the step count is not positive.
	 */
	static StepsProgressIndicator forSteps(ProgressIndicator indicator, int totalSteps) {
		return WeightedStepsProgressIndicator.forTasks(indicator, totalSteps);
	}

}
