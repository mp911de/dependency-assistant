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

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import biz.paluch.dap.util.TaskScope.State;
import biz.paluch.dap.util.TaskScope.Subtask;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link TaskScope}.
 *
 * @author Mark Paluch
 */
class TaskScopeTests {

	// isCancelable() consults ProgressManager, which needs the application
	private final AbstractProgressIndicatorBase indicator = new AbstractProgressIndicatorBase() {

		@Override
		public boolean isCancelable() {
			return true;
		}

	};

	@Test
	void joinedSubtaskExposesResult() {

		try (TaskScope scope = TaskScope.open("test", indicator)) {

			Subtask<String> subtask = scope.fork(() -> "hello");

			assertThat(subtask.join().state()).isEqualTo(State.SUCCESS);
			assertThat(subtask.get()).isEqualTo("hello");
			assertThat(subtask.exception()).isNull();
		}
	}

	@Test
	void failedSubtaskExposesException() {

		try (TaskScope scope = TaskScope.open("test", indicator)) {

			Subtask<String> subtask = scope.fork(() -> {
				throw new IOException("boom");
			});

			assertThat(subtask.join().state()).isEqualTo(State.FAILED);
			assertThat(subtask.exception()).isInstanceOf(IOException.class).hasMessage("boom");
			assertThatIllegalStateException().isThrownBy(subtask::get);
			assertThatThrownBy(subtask::getOrThrow).isInstanceOf(RuntimeException.class)
					.hasCauseInstanceOf(IOException.class);
		}
	}

	@Test
	void joinWithTimeoutCancelsSubtask() throws InterruptedException {

		AtomicBoolean interrupted = new AtomicBoolean();
		CountDownLatch finished = new CountDownLatch(1);

		try (TaskScope scope = TaskScope.open("test", indicator)) {

			Subtask<Void> subtask = scope.fork(() -> {
				try {
					Thread.sleep(Duration.ofSeconds(30));
				} catch (InterruptedException e) {
					interrupted.set(true);
				} finally {
					finished.countDown();
				}
				return null;
			});

			assertThat(subtask.join(Duration.ofMillis(50)).state()).isEqualTo(State.TIMED_OUT);
			assertThat(subtask.exception()).isInstanceOf(java.util.concurrent.TimeoutException.class);
		}

		assertThat(finished.await(5, TimeUnit.SECONDS)).isTrue();
		assertThat(interrupted).isTrue();
	}

	@Test
	void boundsConcurrentSubtasks() {

		AtomicInteger running = new AtomicInteger();
		AtomicInteger peak = new AtomicInteger();

		try (TaskScope scope = TaskScope.open("test", indicator, 2)) {

			for (int i = 0; i < 8; i++) {
				scope.fork(() -> {
					peak.accumulateAndGet(running.incrementAndGet(), Math::max);
					Thread.sleep(20);
					running.decrementAndGet();
					return null;
				});
			}

			scope.joinAll();
		}

		assertThat(peak).hasValue(2);
	}

	@Test
	void cancelledIndicatorAbortsJoinAndCloseCancelsSubtasks() {

		Subtask<Void> subtask;
		try (TaskScope scope = TaskScope.open("test", indicator)) {

			subtask = scope.fork(() -> {
				Thread.sleep(Duration.ofSeconds(30));
				return null;
			});

			indicator.cancel();

			assertThatExceptionOfType(ProcessCanceledException.class).isThrownBy(subtask::join);
		}

		assertThat(subtask.state()).isEqualTo(State.CANCELLED);
		assertThatExceptionOfType(ProcessCanceledException.class).isThrownBy(subtask::getOrThrow);
	}

	@Test
	void cancellationDuringJoinAbortsWait() {

		try (TaskScope scope = TaskScope.open("test", indicator)) {

			Subtask<Void> subtask = scope.fork(() -> {
				Thread.sleep(Duration.ofSeconds(30));
				return null;
			});

			CompletableFuture.delayedExecutor(50, TimeUnit.MILLISECONDS).execute(indicator::cancel);

			assertThatExceptionOfType(ProcessCanceledException.class).isThrownBy(subtask::join);
		}
	}

	@Test
	void joinOfCompletedSubtaskObservesCancellation() {

		try (TaskScope scope = TaskScope.open("test", indicator)) {

			Subtask<String> subtask = scope.fork(() -> "hello").join();
			indicator.cancel();

			assertThatExceptionOfType(ProcessCanceledException.class).isThrownBy(subtask::join);
		}
	}

	@Test
	void wrappedCancellationReportsCancelled() {

		try (TaskScope scope = TaskScope.open("test", indicator)) {

			Subtask<Void> subtask = scope.fork(() -> {
				throw new CompletionException(new CancellationException());
			});

			assertThat(subtask.join().state()).isEqualTo(State.CANCELLED);
		}
	}

	@Test
	void subtaskThrowingProcessCanceledExceptionReportsCancelled() {

		try (TaskScope scope = TaskScope.open("test", indicator)) {

			Subtask<Void> subtask = scope.fork(() -> {
				throw new ProcessCanceledException();
			});

			assertThat(subtask.join().state()).isEqualTo(State.CANCELLED);
			assertThat(subtask.exception()).isNull();
		}
	}

	@Test
	void parkedSubtaskIsCancelledOnClose() {

		Subtask<Void> parked;
		try (TaskScope scope = TaskScope.open("test", indicator, 1)) {

			scope.fork(() -> {
				Thread.sleep(Duration.ofSeconds(30));
				return null;
			});
			parked = scope.fork(() -> null);
		}

		assertThat(parked.state()).isEqualTo(State.CANCELLED);
	}

}
