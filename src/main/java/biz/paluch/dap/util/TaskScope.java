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

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import org.jspecify.annotations.Nullable;

/**
 * Virtual-thread subtasks owned by one thread and joined under a progress
 * indicator. Use try-with-resources so outstanding tasks are cancelled when the
 * scope closes. Cleanup waits only a bounded grace period. Tasks ignoring
 * interruption may outlive it.
 *
 * <p>Joins observe indicator cancellation. Task failures remain in their
 * handles for the owner to inspect or propagate through
 * {@link Subtask#getOrThrow()}. Direct or asynchronously wrapped cancellation
 * is reported as {@link State#CANCELLED}.
 *
 * <p>A bounded scope limits executing tasks. Tasks waiting for a permit remain
 * cancellable.
 *
 * @author Mark Paluch
 */
public class TaskScope implements AutoCloseable {

	private static final Logger LOG = Logger.getInstance(TaskScope.class);

	private static final long POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(10);

	static final Duration CLOSE_GRACE = Duration.ofSeconds(10);

	private final String name;

	private final ProgressIndicator indicator;

	private final ExecutorService executor;

	private final @Nullable Semaphore permits;

	private final List<Subtask<?>> subtasks = new ArrayList<>();

	private TaskScope(String name, ProgressIndicator indicator, @Nullable Semaphore permits) {
		this.name = name;
		this.indicator = indicator;
		this.permits = permits;
		this.executor = Executors
				.newThreadPerTaskExecutor(
						VirtualThreads.ofVirtual().name("DependencyAssistant-" + name + "-", 0).factory());
	}

	/**
	 * Open an unbounded scope. The opening thread owns and must close it.
	 */
	public static TaskScope open(String name, ProgressIndicator indicator) {
		return new TaskScope(name, indicator, null);
	}

	/**
	 * Limit concurrent tasks. The opening thread owns and must close the scope.
	 *
	 * @throws IllegalArgumentException if the concurrency limit is not positive.
	 */
	public static TaskScope open(String name, ProgressIndicator indicator, int maxConcurrency) {

		if (maxConcurrency <= 0) {
			throw new IllegalArgumentException("Max concurrency must be greater than 0: " + maxConcurrency);
		}
		return new TaskScope(name, indicator, new Semaphore(maxConcurrency));
	}

	/**
	 * Schedule a subtask. In a bounded scope, the subtask waits for a permit before
	 * running.
	 *
	 * @throws java.util.concurrent.RejectedExecutionException if the scope is
	 * closed.
	 */
	public <T> Subtask<T> fork(Callable<? extends T> task) {

		Callable<T> bounded = () -> {
			if (permits != null) {
				permits.acquire();
			}
			try {
				return task.call();
			} finally {
				if (permits != null) {
					permits.release();
				}
			}
		};

		Subtask<T> subtask = new Subtask<>(this, executor.submit(bounded));
		subtasks.add(subtask);
		return subtask;
	}

	/**
	 * Return an unmodifiable live view of subtasks in fork order.
	 */
	public List<Subtask<?>> getSubtasks() {
		return Collections.unmodifiableList(subtasks);
	}

	/**
	 * Join all subtasks in fork order without a timeout.
	 *
	 * @throws ProcessCanceledException if the indicator is cancelled or the owner
	 * thread is interrupted while waiting.
	 */
	public void joinAll() {
		for (Subtask<?> subtask : subtasks) {
			subtask.join();
		}
	}

	/**
	 * Join all subtasks in fork order against one shared deadline. Subtasks not
	 * completed when {@code timeout} elapses are cancelled and report
	 * {@link State#TIMED_OUT}.
	 *
	 * @param timeout the maximum time to wait for all subtasks together.
	 * @throws ProcessCanceledException if the indicator is cancelled or the owner
	 * thread is interrupted while waiting.
	 */
	public void joinAll(Duration timeout) {

		long deadline = System.nanoTime() + timeout.toNanos();
		for (Subtask<?> subtask : subtasks) {
			subtask.await(Math.max(0, deadline - System.nanoTime()));
		}
	}

	/**
	 * Cancel every subtask that has not completed, interrupting running ones.
	 */
	public void cancelAll() {
		for (Subtask<?> subtask : subtasks) {
			subtask.cancel();
		}
	}

	/**
	 * Cancel outstanding subtasks and wait up to {@link #CLOSE_GRACE} for their
	 * threads to finish. A subtask that ignores interruption is logged and left to
	 * run out on its daemon thread.
	 */
	@Override
	public void close() {

		cancelAll();
		executor.shutdownNow();

		// an interrupted owner is the usual cancellation path, so finish the bounded
		// wait first and restore the interrupt afterwards
		long deadline = System.nanoTime() + CLOSE_GRACE.toNanos();
		boolean interrupted = false;
		boolean terminated = false;
		while (!terminated) {
			long remaining = deadline - System.nanoTime();
			if (remaining <= 0) {
				break;
			}
			try {
				terminated = executor.awaitTermination(remaining, TimeUnit.NANOSECONDS);
				if (!terminated) {
					break;
				}
			} catch (InterruptedException e) {
				interrupted = true;
			}
		}

		if (!terminated) {
			LOG.warn("[%s] Subtasks did not terminate within %s after cancellation".formatted(name, CLOSE_GRACE));
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * Completion state of a {@link Subtask}.
	 */
	public enum State {

		/** Not yet completed. */
		RUNNING,

		/** Completed with a result. */
		SUCCESS,

		/** Completed by throwing. */
		FAILED,

		/** Cancelled by a join timeout. */
		TIMED_OUT,

		/** Cancelled by the owner or the task observed indicator cancellation. */
		CANCELLED

	}

	/**
	 * Handle for a forked task. Joins observe cancellation. Outcome access does not
	 * block and requires a completed state.
	 */
	public static class Subtask<T> {

		private final TaskScope scope;

		private final Future<T> future;

		private volatile @Nullable TimeoutException timeout;

		Subtask(TaskScope scope, Future<T> future) {
			this.scope = scope;
			this.future = future;
		}

		public State state() {

			if (timeout != null) {
				return State.TIMED_OUT;
			}

			return switch (future.state()) {
			case RUNNING -> State.RUNNING;
			case SUCCESS -> State.SUCCESS;
			case CANCELLED -> State.CANCELLED;
			case FAILED -> isCancellation(future.exceptionNow()) ? State.CANCELLED : State.FAILED;
			};
		}

		/**
		 * Wait for completion without a timeout. Task failures remain in the handle.
		 *
		 * @throws ProcessCanceledException if the indicator is cancelled or the waiting
		 * thread interrupted.
		 */
		public Subtask<T> join() {
			await(-1);
			return this;
		}

		/**
		 * Wait for completion, cancelling the task on timeout and reporting
		 * {@link State#TIMED_OUT}.
		 *
		 * @throws ProcessCanceledException if the indicator is cancelled or the waiting
		 * thread interrupted.
		 */
		public Subtask<T> join(Duration timeout) {
			await(timeout.toNanos());
			return this;
		}

		/**
		 * Return the result of a {@link State#SUCCESS successful} subtask.
		 *
		 * @throws IllegalStateException if the subtask is not in state {@code SUCCESS}.
		 */
		public T get() {

			if (state() != State.SUCCESS) {
				throw new IllegalStateException("Subtask is " + state());
			}
			return future.resultNow();
		}

		/**
		 * Return the result or propagate the outcome: a failure is rethrown unchecked,
		 * a timeout as {@link RuntimeException} wrapping the {@link TimeoutException},
		 * a cancellation as {@link ProcessCanceledException}.
		 *
		 * @throws IllegalStateException if the subtask is still running.
		 */
		public T getOrThrow() {

			State state = state();
			if (state == State.SUCCESS) {
				return future.resultNow();
			}
			if (state == State.CANCELLED) {
				throw new ProcessCanceledException();
			}
			if (state == State.RUNNING) {
				throw new IllegalStateException("Subtask is still running");
			}

			Throwable exception = exception();
			if (exception instanceof RuntimeException runtimeException) {
				throw runtimeException;
			}
			if (exception instanceof Error error) {
				throw error;
			}
			throw new RuntimeException(exception);
		}

		/**
		 * Return the exception of a {@link State#FAILED failed} or
		 * {@link State#TIMED_OUT timed out} subtask, {@literal null} otherwise.
		 */
		public @Nullable Throwable exception() {

			return switch (state()) {
			case TIMED_OUT -> timeout;
			case FAILED -> future.exceptionNow();
			default -> null;
			};
		}

		/**
		 * Cancel the subtask, interrupting it if running. No-op once completed.
		 */
		public void cancel() {
			future.cancel(true);
		}

		void await(long timeoutNanos) {

			long deadline = System.nanoTime() + timeoutNanos;

			// checked on entry so a completed subtask cannot be consumed after
			// cancellation, and on every poll while waiting. Honors non-cancelable
			// sections, coroutine job cancellation, and indicators with PCE disabled,
			// see ProgressIndicatorUtils.awaitWithCheckCanceled
			ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(scope.indicator);

			while (!future.isDone()) {

				ProgressIndicatorUtils.checkCancelledEvenWithPCEDisabled(scope.indicator);

				long wait = POLL_NANOS;
				if (timeoutNanos >= 0) {
					long remaining = deadline - System.nanoTime();
					if (remaining <= 0) {
						this.timeout = new TimeoutException(
								"Subtask timed out after %d ms".formatted(TimeUnit.NANOSECONDS.toMillis(timeoutNanos)));
						future.cancel(true);
						return;
					}
					wait = Math.min(wait, remaining);
				}

				try {
					future.get(wait, TimeUnit.NANOSECONDS);
				} catch (TimeoutException e) {
					// poll again
				} catch (ExecutionException | CancellationException e) {
					return;
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					throw new ProcessCanceledException(e);
				}
			}
		}

		private static boolean isCancellation(Throwable throwable) {

			// Async wrappers must not turn cancellation into an ordinary failure.
			Throwable cause = throwable;
			while ((cause instanceof CompletionException || cause instanceof ExecutionException)
					&& cause.getCause() != null) {
				cause = cause.getCause();
			}
			return cause instanceof ProcessCanceledException || cause instanceof CancellationException;
		}

	}

}
