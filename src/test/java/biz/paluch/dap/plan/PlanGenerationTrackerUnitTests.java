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

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradePlanState.PlanGenerationTracker}.
 *
 * @author Mark Paluch
 */
class PlanGenerationTrackerUnitTests {

	public static final Runnable RUNNABLE = () -> {
	};

	@Test
	void undoAndRedoAdvanceRevisionWhileRestoringLogicalState() {

		UpgradePlanState.PlanGenerationTracker tracker = new UpgradePlanState.PlanGenerationTracker();
		PlanGeneration initial = tracker.current();
		PlanGeneration first = tracker.tryAdvance(initial, RUNNABLE);
		PlanGeneration second = tracker.tryAdvance(first, RUNNABLE);

		PlanGeneration undoSecond = tracker.tryAdvance(second, first, RUNNABLE);
		PlanGeneration undoFirst = tracker.tryAdvance(first, initial, RUNNABLE);
		PlanGeneration redoFirst = tracker.tryAdvance(initial, first, RUNNABLE);
		PlanGeneration redoSecond = tracker.tryAdvance(first, second, RUNNABLE);

		assertThat(undoSecond.revision).isGreaterThan(second.revision);
		assertThat(undoSecond.hasState(first)).isTrue();
		assertThat(undoFirst.hasState(initial)).isTrue();
		assertThat(redoFirst.hasState(first)).isTrue();
		assertThat(redoSecond.hasState(second)).isTrue();
		assertThat(redoSecond.revision).isGreaterThan(redoFirst.revision);
		assertThat(tracker.isCurrent(redoSecond)).isTrue();
	}

	@Test
	void staleRevisionIsRejectedAfterRedoRevisitsTheSameState() {

		UpgradePlanState.PlanGenerationTracker tracker = new UpgradePlanState.PlanGenerationTracker();
		PlanGeneration initial = tracker.current();
		PlanGeneration applied = tracker.tryAdvance(initial, RUNNABLE);
		PlanGeneration undone = tracker.tryAdvance(applied, initial, RUNNABLE);
		PlanGeneration redone = tracker.tryAdvance(initial, applied, RUNNABLE);
		AtomicBoolean invoked = new AtomicBoolean();

		PlanGeneration result = tracker.tryAdvance(applied, () -> invoked.set(true));

		assertThat(redone.hasState(applied)).isTrue();
		assertThat(redone.revision).isGreaterThan(undone.revision);
		assertThat(result).isNull();
		assertThat(invoked).isFalse();
	}

	@Test
	void laterMutationPreventsUndoOfAnEarlierLogicalState() {

		UpgradePlanState.PlanGenerationTracker tracker = new UpgradePlanState.PlanGenerationTracker();
		PlanGeneration initial = tracker.current();
		PlanGeneration first = tracker.tryAdvance(initial, RUNNABLE);
		PlanGeneration second = tracker.tryAdvance(first, RUNNABLE);
		PlanGeneration undoSecond = tracker.tryAdvance(second, first, RUNNABLE);
		PlanGeneration later = tracker.tryAdvance(undoSecond, RUNNABLE);

		PlanGeneration result = tracker.tryAdvance(first, initial, RUNNABLE);

		assertThat(tracker.isCurrent(later)).isTrue();
		assertThat(result).isNull();
	}

}
