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

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.EnumSet;

import biz.paluch.dap.util.ProbingProgressIndicator.CancellationObservation;
import biz.paluch.dap.util.ProbingProgressIndicator.OutputMode;
import biz.paluch.dap.util.ProbingProgressIndicator.ProgressMethod;
import biz.paluch.dap.util.ProbingProgressIndicator.Snapshot;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ProbingProgressIndicator}.
 *
 * @author Mark Paluch
 */
class ProbingProgressIndicatorTests {

	@Test
	void capturesStepDurationsFromTextUpdates() {

		MutableTicker ticker = new MutableTicker();
		ProbingProgressIndicator indicator = new ProbingProgressIndicator(ticker::nanos);

		indicator.setIndeterminate(false);
		indicator.setText("Collect dependencies");
		ticker.advance(Duration.ofMillis(25));
		indicator.setText2("build.gradle");
		ticker.advance(Duration.ofMillis(10));
		indicator.setFraction(0.5);
		ticker.advance(Duration.ofMillis(40));
		indicator.setText("Resolve releases");
		ticker.advance(Duration.ofMillis(5));

		Snapshot snapshot = indicator.snapshot();

		assertThat(snapshot.duration()).isEqualTo(Duration.ofMillis(80));
		assertThat(snapshot.steps()).hasSize(3);
		assertThat(snapshot.steps().get(0)).satisfies(step -> {
			assertThat(step.text()).isEqualTo("Collect dependencies");
			assertThat(step.text2()).isEmpty();
			assertThat(step.duration()).isEqualTo(Duration.ofMillis(25));
			assertThat(step.endingFraction()).isZero();
		});
		assertThat(snapshot.steps().get(1)).satisfies(step -> {
			assertThat(step.text()).isEqualTo("Collect dependencies");
			assertThat(step.text2()).isEqualTo("build.gradle");
			assertThat(step.duration()).isEqualTo(Duration.ofMillis(50));
			assertThat(step.endingFraction()).isEqualTo(0.5);
		});
		assertThat(snapshot.steps().get(2)).satisfies(step -> {
			assertThat(step.text()).isEqualTo("Resolve releases");
			assertThat(step.text2()).isEqualTo("build.gradle");
			assertThat(step.duration()).isEqualTo(Duration.ofMillis(5));
		});
	}

	@Test
	void capturesCallSiteOfStepUnderTraceMode() {

		ProbingProgressIndicator indicator = new ProbingProgressIndicator(null, EnumSet.of(OutputMode.CAPTURE_TRACE),
				0.05d, new MutableTicker()::nanos, new PrintStream(new ByteArrayOutputStream(), true,
						StandardCharsets.UTF_8));

		indicator.setIndeterminate(false);
		indicator.setText("Resolve releases");

		assertThat(indicator.snapshot().steps().get(0).callSite()).isNotNull()
				.satisfies(callSite -> assertThat(callSite.getClassName()).isEqualTo(getClass().getName()));
	}

	@Test
	void doesNotCaptureCallSiteWithoutTraceMode() {

		ProbingProgressIndicator indicator = new ProbingProgressIndicator(new MutableTicker()::nanos);

		indicator.setText("Resolve releases");

		assertThat(indicator.snapshot().steps().get(0).callSite()).isNull();
	}

	@Test
	void capturesSegmentTiming() {

		MutableTicker ticker = new MutableTicker();
		ProbingProgressIndicator indicator = new ProbingProgressIndicator(ticker::nanos);

		String result = indicator.call("Resolve releases", () -> {
			ticker.advance(Duration.ofMillis(30));
			return "done";
		});

		assertThat(result).isEqualTo("done");
		assertThat(indicator.snapshot().segments()).singleElement().satisfies(segment -> {
			assertThat(segment.label()).isEqualTo("Resolve releases");
			assertThat(segment.duration()).isEqualTo(Duration.ofMillis(30));
			assertThat(segment.failed()).isFalse();
		});
	}

	@Test
	void recordsFailedSegmentAndRethrows() {

		MutableTicker ticker = new MutableTicker();
		ProbingProgressIndicator indicator = new ProbingProgressIndicator(ticker::nanos);

		assertThatExceptionOfType(ProcessCanceledException.class)
				.isThrownBy(() -> indicator.run("Collect dependencies", () -> {
					ticker.advance(Duration.ofMillis(5));
					throw new ProcessCanceledException();
				}));

		assertThat(indicator.snapshot().segments()).singleElement().satisfies(segment -> {
			assertThat(segment.label()).isEqualTo("Collect dependencies");
			assertThat(segment.duration()).isEqualTo(Duration.ofMillis(5));
			assertThat(segment.failed()).isTrue();
		});
	}

	@Test
	void recordsFractionRegressionWhenFractionDecreases() {

		MutableTicker ticker = new MutableTicker();
		ProbingProgressIndicator indicator = new ProbingProgressIndicator(ticker::nanos);

		indicator.setIndeterminate(false);
		indicator.setFraction(0.5);
		ticker.advance(Duration.ofMillis(8));
		indicator.setFraction(0.6);
		indicator.setFraction(0.4);

		assertThat(indicator.snapshot().fractionRegressions()).singleElement().satisfies(regression -> {
			assertThat(regression.afterStart()).isEqualTo(Duration.ofMillis(8));
			assertThat(regression.previousFraction()).isEqualTo(0.6);
			assertThat(regression.fraction()).isEqualTo(0.4);
		});
	}

	@Test
	void recordsCancellationRequestAndCheckCanceledException() {

		MutableTicker ticker = new MutableTicker();
		ProbingProgressIndicator indicator = new ProbingProgressIndicator(ticker::nanos);

		indicator.cancel();
		ticker.advance(Duration.ofMillis(7));

		assertThatExceptionOfType(ProcessCanceledException.class).isThrownBy(indicator::checkCanceled);

		Snapshot snapshot = indicator.snapshot();
		assertThat(snapshot.wasCancellationRequested()).isTrue();
		assertThat(snapshot.cancellationRequest()).satisfies(request -> {
			assertThat(request.afterStart()).isZero();
			assertThat(request.method()).isEqualTo(ProgressMethod.CANCEL);
		});
		assertThat(snapshot.cancellationObservations()).singleElement().satisfies(observation -> {
			assertThat(observation.afterStart()).isEqualTo(Duration.ofMillis(7));
			assertThat(observation.method()).isEqualTo(ProgressMethod.CHECK_CANCELED);
		});
	}

	@Test
	void recordsCancellationExceptionsFromDelegatedSetters() {

		MutableTicker ticker = new MutableTicker();
		ProbingProgressIndicator indicator = new ProbingProgressIndicator(new CancelingDelegate(),
				EnumSet.noneOf(OutputMode.class), 0.05d, ticker::nanos,
				new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

		indicator.setIndeterminate(false);
		assertThatExceptionOfType(ProcessCanceledException.class).isThrownBy(() -> indicator.setText("Loading"));
		ticker.advance(Duration.ofMillis(3));
		assertThatExceptionOfType(ProcessCanceledException.class).isThrownBy(() -> indicator.setFraction(0.5));

		assertThat(indicator.snapshot().cancellationObservations()).extracting(CancellationObservation::method)
				.containsExactly(ProgressMethod.SET_TEXT, ProgressMethod.SET_FRACTION);
	}

	@Test
	void printsConsolidatedUpdatesAndFinalReport() {

		MutableTicker ticker = new MutableTicker();
		ByteArrayOutputStream output = new ByteArrayOutputStream();
		ProbingProgressIndicator indicator = new ProbingProgressIndicator(null, EnumSet.allOf(OutputMode.class),
				0.10d, ticker::nanos, new PrintStream(output, true, StandardCharsets.UTF_8));

		indicator.setIndeterminate(false);
		indicator.setText("Resolve releases");
		indicator.setFraction(0.03);
		indicator.setFraction(0.11);
		indicator.setFraction(0.15);
		indicator.close();

		String report = output.toString(StandardCharsets.UTF_8);
		assertThat(report).contains("🔎 progress", "📊 Progress probe report", "Resolve releases", "11.0%");
		assertThat(report).doesNotContain("3.0%");
	}

	private static class CancelingDelegate extends AbstractProgressIndicatorBase {

		@Override
		public void setText(String text) {
			throw new ProcessCanceledException();
		}

		@Override
		public void setFraction(double fraction) {
			throw new ProcessCanceledException();
		}

	}

	private static class MutableTicker {

		private long nanos;

		private long nanos() {
			return nanos;
		}

		private void advance(Duration duration) {
			nanos += duration.toNanos();
		}

	}

}
