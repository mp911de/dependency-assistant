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

import java.io.PrintStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.AbstractProgressIndicatorBase;
import org.jspecify.annotations.Nullable;

/**
 * Diagnostic progress indicator with timing and cancellation observations. It
 * can record silently or forward updates to a visible indicator.
 *
 * <p>Text changes open steps. Named segments time independent blocks of work.
 * Use try-with-resources when wrapping a running indicator so the report is
 * finished when work ends. Closing the probe does not stop its delegate.
 *
 * <pre class="code">
 * try (ProbingProgressIndicator probe = ProbingProgressIndicator.reportingOnFinish(indicator)) {
 *     probe.run("resolve", () -> resolveReleases(probe));
 * }
 * </pre>
 *
 * <p>Trace reporting walks the stack for step origins and is intended for
 * diagnostics.
 *
 * @author Mark Paluch
 * @see Snapshot
 */
public class ProbingProgressIndicator extends AbstractProgressIndicatorBase implements AutoCloseable {

	private static final double DEFAULT_FRACTION_INCREMENT = 0.05d;

	private static final double FRACTION_REGRESSION_EPSILON = 1e-6d;

	private static final int REPORT_WIDTH = 72;

	private static final String OUTLIER_MARKER = "🔥";

	private static final String OUTLIER_PAD = "  ";

	private static final String PROBE_CLASS = ProbingProgressIndicator.class.getName();

	private static final String WEIGHTED_STEPS_CLASS = WeightedStepsProgressIndicator.class.getName();

	private static final String DELEGATING_STEPS_CLASS = StepsProgressIndicatorWrapper.class.getName();

	private static final PrintStream DEFAULT_OUT = System.out;

	private final Object monitor = new Object();

	private final @Nullable ProgressIndicator delegate;

	private final LongSupplier ticker;

	private final PrintStream out;

	private final EnumSet<OutputMode> outputModes;

	private final double fractionIncrement;

	private final List<MutableStep> steps = new ArrayList<>();

	private final List<CancellationObservation> cancellationObservations = new ArrayList<>();

	private final List<Segment> segments = new ArrayList<>();

	private final List<FractionRegression> fractionRegressions = new ArrayList<>();

	private long baseNanos;

	private @Nullable Long finishedAtNanos;

	private @Nullable CancellationRequest cancellationRequest;

	private @Nullable MutableStep currentStep;

	private double lastPrintedFraction = Double.NaN;

	private double lastObservedFraction = Double.NaN;

	private @Nullable String lastPrintedText;

	private @Nullable String lastPrintedText2;

	private boolean reportPrinted;

	/**
	 * Create a silent standalone probing indicator.
	 */
	public ProbingProgressIndicator() {
		this(null, EnumSet.noneOf(OutputMode.class), DEFAULT_FRACTION_INCREMENT, System::nanoTime, DEFAULT_OUT);
	}

	/**
	 * Create a silent probe forwarding updates to the delegate. A {@code null}
	 * delegate records standalone.
	 */
	public ProbingProgressIndicator(@Nullable ProgressIndicator delegate) {
		this(delegate, EnumSet.noneOf(OutputMode.class), DEFAULT_FRACTION_INCREMENT, System::nanoTime, DEFAULT_OUT);
	}

	ProbingProgressIndicator(LongSupplier ticker) {
		this(null, EnumSet.noneOf(OutputMode.class), DEFAULT_FRACTION_INCREMENT, ticker, DEFAULT_OUT);
	}

	ProbingProgressIndicator(@Nullable ProgressIndicator delegate, EnumSet<OutputMode> outputModes,
			double fractionIncrement, LongSupplier ticker, PrintStream out) {

		if (fractionIncrement <= 0 || fractionIncrement > 1) {
			throw new IllegalArgumentException("Fraction increment must be greater than 0 and less than or equal to 1");
		}

		this.delegate = delegate;
		this.outputModes = outputModes.clone();
		this.fractionIncrement = fractionIncrement;
		this.ticker = ticker;
		this.out = out;
		this.baseNanos = ticker.getAsLong();
	}

	public static ProbingProgressIndicator printingUpdates() {
		return new ProbingProgressIndicator(null, EnumSet.of(OutputMode.PRINT_UPDATES), DEFAULT_FRACTION_INCREMENT,
				System::nanoTime, DEFAULT_OUT);
	}

	public static ProbingProgressIndicator printingUpdates(ProgressIndicator delegate) {
		return new ProbingProgressIndicator(delegate, EnumSet.of(OutputMode.PRINT_UPDATES), DEFAULT_FRACTION_INCREMENT,
				System::nanoTime, DEFAULT_OUT);
	}

	public static ProbingProgressIndicator reportingOnFinish() {
		return new ProbingProgressIndicator(null, EnumSet.of(OutputMode.REPORT_ON_FINISH), DEFAULT_FRACTION_INCREMENT,
				System::nanoTime, DEFAULT_OUT);
	}

	public static ProbingProgressIndicator reportingOnFinish(ProgressIndicator delegate) {
		return new ProbingProgressIndicator(delegate, EnumSet.of(OutputMode.REPORT_ON_FINISH),
				DEFAULT_FRACTION_INCREMENT,
				System::nanoTime, DEFAULT_OUT);
	}

	public static ProbingProgressIndicator traceReportingOnFinish() {
		return new ProbingProgressIndicator(null, EnumSet.of(OutputMode.REPORT_ON_FINISH, OutputMode.CAPTURE_TRACE),
				DEFAULT_FRACTION_INCREMENT, System::nanoTime, DEFAULT_OUT);
	}

	public static ProbingProgressIndicator traceReportingOnFinish(ProgressIndicator delegate) {
		return new ProbingProgressIndicator(delegate, EnumSet.of(OutputMode.REPORT_ON_FINISH, OutputMode.CAPTURE_TRACE),
				DEFAULT_FRACTION_INCREMENT, System::nanoTime, DEFAULT_OUT);
	}

	public static ProbingProgressIndicator printingUpdatesAndReporting() {
		return new ProbingProgressIndicator(null, EnumSet.of(OutputMode.PRINT_UPDATES, OutputMode.REPORT_ON_FINISH),
				DEFAULT_FRACTION_INCREMENT, System::nanoTime, DEFAULT_OUT);
	}

	public static ProbingProgressIndicator printingUpdatesAndReporting(ProgressIndicator delegate) {
		return new ProbingProgressIndicator(delegate,
				EnumSet.of(OutputMode.PRINT_UPDATES, OutputMode.REPORT_ON_FINISH), DEFAULT_FRACTION_INCREMENT,
				System::nanoTime, DEFAULT_OUT);
	}

	@Override
	public void start() {

		long now = ticker.getAsLong();
		synchronized (monitor) {
			reset(now);
		}

		runCapturingCancellation(ProgressMethod.START, () -> {
			super.start();
			ProgressIndicator delegate = this.delegate;
			if (delegate != null && !delegate.isRunning()) {
				delegate.start();
			}
		});
		printUpdateIfEnabled(ProgressMethod.START);
	}

	@Override
	public void stop() {

		try {
			runCapturingCancellation(ProgressMethod.STOP, () -> {
				super.stop();
				ProgressIndicator delegate = this.delegate;
				if (delegate != null && delegate.isRunning()) {
					delegate.stop();
				}
			});
		} finally {
			finishAndReport();
		}
	}

	@Override
	public void close() {
		finishAndReport();
	}

	@Override
	public void cancel() {

		long now = ticker.getAsLong();
		recordCancellationRequest(ProgressMethod.CANCEL, now);

		runCapturingCancellation(ProgressMethod.CANCEL, () -> {
			super.cancel();
			ProgressIndicator delegate = this.delegate;
			if (delegate != null) {
				delegate.cancel();
			}
		});
		printUpdateIfEnabled(ProgressMethod.CANCEL);
	}

	@Override
	public boolean isCanceled() {

		boolean canceled = super.isCanceled();
		ProgressIndicator delegate = this.delegate;
		if (delegate != null && delegate.isCanceled()) {
			canceled = true;
		}

		if (canceled) {
			recordCancellationRequest(ProgressMethod.IS_CANCELED, ticker.getAsLong());
		}

		return canceled;
	}

	@Override
	public void checkCanceled() throws ProcessCanceledException {
		try {
			if (isCanceled()) {
				throw new ProcessCanceledException();
			}
			super.checkCanceled();
			ProgressIndicator delegate = this.delegate;
			if (delegate != null) {
				delegate.checkCanceled();
			}
		} catch (ProcessCanceledException e) {
			recordCancellationObservation(ProgressMethod.CHECK_CANCELED, e, ticker.getAsLong());
			printUpdateIfEnabled(ProgressMethod.CHECK_CANCELED);
			throw e;
		}
	}

	@Override
	public void setText(String text) {
		runCapturingCancellation(ProgressMethod.SET_TEXT, () -> {
			super.setText(text);
			ProgressIndicator delegate = this.delegate;
			if (delegate != null) {
				delegate.setText(text);
			}
		});
		recordProgressUpdate(ProgressMethod.SET_TEXT);
	}

	@Override
	public void setText2(String text) {
		runCapturingCancellation(ProgressMethod.SET_TEXT2, () -> {
			super.setText2(text);
			ProgressIndicator delegate = this.delegate;
			if (delegate != null) {
				delegate.setText2(text);
			}
		});
		recordProgressUpdate(ProgressMethod.SET_TEXT2);
	}

	@Override
	public void setFraction(double fraction) {
		runCapturingCancellation(ProgressMethod.SET_FRACTION, () -> {
			super.setFraction(fraction);
			ProgressIndicator delegate = this.delegate;
			if (delegate != null) {
				delegate.setFraction(fraction);
			}
		});
		recordProgressUpdate(ProgressMethod.SET_FRACTION);
	}

	@Override
	public void setIndeterminate(boolean indeterminate) {
		runCapturingCancellation(ProgressMethod.SET_INDETERMINATE, () -> {
			super.setIndeterminate(indeterminate);
			ProgressIndicator delegate = this.delegate;
			if (delegate != null) {
				delegate.setIndeterminate(indeterminate);
			}
		});
		recordProgressUpdate(ProgressMethod.SET_INDETERMINATE);
	}

	/**
	 * Time an action as a named segment. Failures are recorded and rethrown
	 * unchanged, including cancellation.
	 */
	public void run(String label, Runnable action) {
		call(label, () -> {
			action.run();
			return Boolean.TRUE;
		});
	}

	/**
	 * Time an action as a named segment and return its result. Failures are
	 * recorded and rethrown unchanged, including cancellation.
	 */
	public <T> T call(String label, Supplier<T> action) {

		long start = ticker.getAsLong();
		boolean failed = true;
		try {
			T result = action.get();
			failed = false;
			return result;
		} finally {
			recordSegment(label, start, ticker.getAsLong(), failed);
		}
	}

	/**
	 * Return an immutable snapshot including the currently open step.
	 */
	public Snapshot snapshot() {
		return snapshot(ticker.getAsLong());
	}

	/**
	 * Print the current capture without finishing it. Later observations remain
	 * visible to subsequent snapshots and reports.
	 */
	public void printReport() {
		printReport(snapshot());
	}

	private Snapshot snapshot(long now) {

		synchronized (monitor) {
			List<Step> captured = new ArrayList<>(steps.size());
			for (MutableStep step : steps) {
				captured.add(step.toStep(now));
			}

			Duration duration = elapsed(finishedAtNanos != null ? finishedAtNanos : now);
			return new Snapshot(duration, captured, List.copyOf(segments),
					List.copyOf(fractionRegressions), cancellationRequest, List.copyOf(cancellationObservations));
		}
	}

	private void recordSegment(String label, long startNanos, long endNanos, boolean failed) {

		Segment segment = new Segment(normalize(label), elapsed(startNanos),
				Duration.ofNanos(Math.max(0, endNanos - startNanos)), failed);
		synchronized (monitor) {
			segments.add(segment);
		}

		if (outputModes.contains(OutputMode.PRINT_UPDATES)) {
			out.println("🧩 segment " + segment.label() + " " + formatDuration(segment.duration())
					+ (segment.failed() ? " (failed)" : ""));
		}
	}

	private void reset(long now) {

		steps.clear();
		cancellationObservations.clear();
		segments.clear();
		fractionRegressions.clear();
		baseNanos = now;
		finishedAtNanos = null;
		cancellationRequest = null;
		currentStep = null;
		lastPrintedFraction = Double.NaN;
		lastObservedFraction = Double.NaN;
		lastPrintedText = null;
		lastPrintedText2 = null;
		reportPrinted = false;
	}

	/**
	 * Finish timing and emit the configured report at most once. Call only after no
	 * further progress updates are expected.
	 */
	public void finishAndReport() {

		boolean firstFinish;
		boolean shouldReport;
		synchronized (monitor) {
			long now = ticker.getAsLong();
			firstFinish = finishedAtNanos == null;
			if (firstFinish) {
				finishedAtNanos = now;
				if (currentStep != null) {
					currentStep.close(now, getFraction(), isIndeterminate());
				}
			}
			shouldReport = outputModes.contains(OutputMode.REPORT_ON_FINISH) && !reportPrinted;
			reportPrinted = true;
		}

		if (firstFinish) {
			printUpdateIfEnabled(ProgressMethod.STOP);
		}
		if (shouldReport) {
			printReport();
		}
	}

	private void recordProgressUpdate(ProgressMethod method) {

		long now = ticker.getAsLong();
		String text = normalize(getText());
		String text2 = normalize(getText2());
		double fraction = getFraction();
		boolean indeterminate = isIndeterminate();

		boolean opensStep = method == ProgressMethod.SET_TEXT || method == ProgressMethod.SET_TEXT2
				|| (method == ProgressMethod.SET_FRACTION && currentStep == null);
		StackTraceElement callSite = opensStep && outputModes.contains(OutputMode.CAPTURE_TRACE) ? captureCallSite()
				: null;

		synchronized (monitor) {
			if (shouldOpenStep(method, text, text2)) {
				if (currentStep != null) {
					currentStep.close(now, fraction, indeterminate);
				}

				MutableStep step = new MutableStep(steps.size() + 1, now, text, text2, fraction, indeterminate,
						Thread.currentThread().getName(), method, callSite);
				steps.add(step);
				currentStep = step;
			} else if (currentStep != null) {
				currentStep.update(fraction, indeterminate);
			} else if (method == ProgressMethod.SET_FRACTION) {
				MutableStep step = new MutableStep(steps.size() + 1, now, text, text2, fraction, indeterminate,
						Thread.currentThread().getName(), method, callSite);
				steps.add(step);
				currentStep = step;
			}

			if (method == ProgressMethod.SET_FRACTION && !indeterminate) {
				if (!Double.isNaN(lastObservedFraction)
						&& fraction < lastObservedFraction - FRACTION_REGRESSION_EPSILON) {
					fractionRegressions.add(new FractionRegression(elapsed(now), lastObservedFraction, fraction,
							Thread.currentThread().getName()));
				}
				lastObservedFraction = fraction;
			}
		}

		printUpdateIfEnabled(method);
	}

	private boolean shouldOpenStep(ProgressMethod method, String text, String text2) {

		if (method == ProgressMethod.SET_TEXT || method == ProgressMethod.SET_TEXT2) {
			MutableStep step = currentStep;
			return step == null || !step.text().equals(text) || !step.text2().equals(text2);
		}

		return false;
	}

	private void runCapturingCancellation(ProgressMethod method, Runnable action) {
		try {
			action.run();
		} catch (ProcessCanceledException e) {
			recordCancellationObservation(method, e, ticker.getAsLong());
			printUpdateIfEnabled(method);
			throw e;
		}
	}

	private void recordCancellationRequest(ProgressMethod method, long now) {

		synchronized (monitor) {
			if (cancellationRequest == null) {
				cancellationRequest = new CancellationRequest(elapsed(now), method, Thread.currentThread().getName());
			}
		}
	}

	private void recordCancellationObservation(ProgressMethod method, ProcessCanceledException exception, long now) {

		recordCancellationRequest(method, now);
		synchronized (monitor) {
			cancellationObservations.add(new CancellationObservation(elapsed(now), method,
					Thread.currentThread().getName(), exception.getClass().getName(), exception.getMessage()));
		}
	}

	private void printUpdateIfEnabled(ProgressMethod method) {

		if (!outputModes.contains(OutputMode.PRINT_UPDATES)) {
			return;
		}

		Snapshot snapshot = snapshot();
		Step step = snapshot.steps().isEmpty() ? null : snapshot.steps().get(snapshot.steps().size() - 1);
		if (!shouldPrintLiveUpdate(method, step, snapshot)) {
			return;
		}

		String label = step != null ? describeStep(step) : "";
		String fraction = step != null ? " fraction=" + formatFraction(step.endingFraction()) : "";
		out.println("🔎 progress " + formatDuration(snapshot.duration()) + fraction + " " + label);
	}

	private boolean shouldPrintLiveUpdate(ProgressMethod method, @Nullable Step step, Snapshot snapshot) {

		if (method == ProgressMethod.CANCEL || method == ProgressMethod.CHECK_CANCELED
				|| method == ProgressMethod.STOP) {
			return true;
		}

		if (step == null) {
			return method == ProgressMethod.START;
		}

		if (!step.text().equals(lastPrintedText) || !step.text2().equals(lastPrintedText2)) {
			rememberPrinted(step);
			return true;
		}

		if (method != ProgressMethod.SET_FRACTION || step.endingIndeterminate()) {
			return false;
		}

		double fraction = step.endingFraction();
		if (Double.isNaN(lastPrintedFraction) || fraction >= 1
				|| Math.abs(fraction - lastPrintedFraction) >= fractionIncrement) {
			rememberPrinted(step);
			return true;
		}

		if (!snapshot.cancellationObservations().isEmpty()) {
			return true;
		}

		return false;
	}

	private void rememberPrinted(Step step) {
		lastPrintedText = step.text();
		lastPrintedText2 = step.text2();
		lastPrintedFraction = step.endingFraction();
	}

	private void printReport(Snapshot snapshot) {

		int regressions = snapshot.fractionRegressions().size();
		printBox(List.of("📊 Progress probe report",
				"total %s · %d steps · %d segments · %d fraction %s".formatted(formatDuration(snapshot.duration()),
						snapshot.steps().size(), snapshot.segments().size(), regressions,
						regressions == 1 ? "regression" : "regressions")));

		printCancellationSection(snapshot);
		printSegmentSection(snapshot);
		printFractionRegressionSection(snapshot);
		printStepSection(snapshot);
	}

	private void printCancellationSection(Snapshot snapshot) {

		printSeparator("🛑 Cancellation");
		CancellationRequest request = snapshot.cancellationRequest();
		out.println("  requested   "
				+ (request != null ? formatDuration(request.afterStart()) + " via " + request.method() : "no"));

		if (snapshot.cancellationObservations().isEmpty()) {
			out.println("  exceptions  none");
			return;
		}

		out.println("  exceptions  " + snapshot.cancellationObservations().size());
		for (CancellationObservation observation : snapshot.cancellationObservations()) {
			out.println("    " + formatDuration(observation.afterStart()) + " " + observation.method() + " "
					+ observation.exceptionType());
		}
	}

	private void printSegmentSection(Snapshot snapshot) {

		printSeparator("🧩 Segments");
		if (snapshot.segments().isEmpty()) {
			out.println("  none");
			return;
		}

		int atWidth = "at".length();
		int durationWidth = "dur".length();
		for (Segment segment : snapshot.segments()) {
			atWidth = Math.max(atWidth, formatDuration(segment.startedAfter()).length());
			durationWidth = Math.max(durationWidth, formatDuration(segment.duration()).length());
		}

		out.println("  " + bracket("at", atWidth, true) + " " + bracket("dur", durationWidth, true) + " segment");
		for (Segment segment : snapshot.segments()) {
			out.println("  " + bracket(formatDuration(segment.startedAfter()), atWidth, true) + " "
					+ bracket(formatDuration(segment.duration()), durationWidth, true) + " " + segment.label()
					+ (segment.failed() ? "  (failed)" : ""));
		}
	}

	private void printFractionRegressionSection(Snapshot snapshot) {

		if (snapshot.fractionRegressions().isEmpty()) {
			return;
		}

		printSeparator("📉 Fraction regressions");
		int atWidth = "at".length();
		int changeWidth = "change".length();
		int threadWidth = "thread".length();
		for (FractionRegression regression : snapshot.fractionRegressions()) {
			atWidth = Math.max(atWidth, formatDuration(regression.afterStart()).length());
			changeWidth = Math.max(changeWidth, fractionChange(regression).length());
			threadWidth = Math.max(threadWidth, regression.threadName().length());
		}

		out.println("  " + bracket("at", atWidth, true) + " " + bracket("change", changeWidth, false) + " "
				+ bracket("thread", threadWidth, false));
		for (FractionRegression regression : snapshot.fractionRegressions()) {
			out.println("  " + bracket(formatDuration(regression.afterStart()), atWidth, true) + " "
					+ bracket(fractionChange(regression), changeWidth, false) + " "
					+ bracket(regression.threadName(), threadWidth, false));
		}
	}

	private void printStepSection(Snapshot snapshot) {

		if (snapshot.steps().isEmpty()) {
			printSeparator("🐢 Slowest steps");
			out.println("  none");
			return;
		}

		Duration p99 = percentile(snapshot.steps(), 99);
		printSeparator("🐢 Slowest steps · p99 " + formatDuration(p99));

		List<Step> slowest = snapshot.steps().stream().sorted(Comparator.comparing(Step::duration).reversed())
				.limit(10).toList();

		int indexWidth = "#".length();
		int durationWidth = "dur".length();
		int startWidth = 0;
		int endWidth = 0;
		int updatesWidth = "upd".length();
		int threadWidth = "thread".length();
		for (Step step : slowest) {
			indexWidth = Math.max(indexWidth, ("#" + step.index()).length());
			durationWidth = Math.max(durationWidth, formatDuration(step.duration()).length());
			startWidth = Math.max(startWidth, formatFraction(step.startingFraction()).length());
			endWidth = Math.max(endWidth, formatFraction(step.endingFraction()).length());
			updatesWidth = Math.max(updatesWidth, Integer.toString(step.updates()).length());
			threadWidth = Math.max(threadWidth, step.threadName().length());
		}
		int fractionWidth = Math.max("fraction".length(), startWidth + " → ".length() + endWidth);

		out.println("  " + OUTLIER_PAD + " " + bracket("#", indexWidth, true) + " "
				+ bracket("dur", durationWidth, true) + " " + bracket("fraction", fractionWidth, true) + " "
				+ bracket("upd", updatesWidth, true) + " " + bracket("thread", threadWidth, false) + " step");
		for (Step step : slowest) {
			boolean outlier = step.duration().compareTo(p99) > 0;
			out.println("  " + (outlier ? OUTLIER_MARKER : OUTLIER_PAD) + " "
					+ bracket("#" + step.index(), indexWidth, true) + " "
					+ bracket(formatDuration(step.duration()), durationWidth, true) + " "
					+ bracket(fractionRange(step, startWidth, endWidth), fractionWidth, true) + " "
					+ bracket(Integer.toString(step.updates()), updatesWidth, true) + " "
					+ bracket(step.threadName(), threadWidth, false) + " " + describeStep(step));

			if (outlier && step.callSite() != null) {
				out.println("       ↳ at " + step.callSite());
			}
		}
	}

	private void printBox(List<String> lines) {

		int width = 0;
		for (String line : lines) {
			width = Math.max(width, displayWidth(line));
		}

		String horizontal = "─".repeat(width + 2);
		out.println("╭" + horizontal + "╮");
		for (String line : lines) {
			out.println("│ " + line + " ".repeat(width - displayWidth(line)) + " │");
		}
		out.println("╰" + horizontal + "╯");
	}

	private void printSeparator(String title) {

		String prefix = "── " + title + " ";
		out.println();
		out.println(prefix + "─".repeat(Math.max(0, REPORT_WIDTH - displayWidth(prefix))));
	}

	private static String fractionRange(Step step, int startWidth, int endWidth) {
		return padLeft(formatFraction(step.startingFraction()), startWidth) + " → "
				+ padLeft(formatFraction(step.endingFraction()), endWidth);
	}

	private static @Nullable StackTraceElement captureCallSite() {

		for (StackTraceElement element : new Throwable().getStackTrace()) {
			if (!isInternalFrame(element)) {
				return element;
			}
		}
		return null;
	}

	private static boolean isInternalFrame(StackTraceElement element) {

		String className = element.getClassName();
		return className.startsWith("com.intellij.") || className.startsWith("org.jetbrains.")
				|| className.equals(PROBE_CLASS) || className.startsWith(PROBE_CLASS + "$")
				|| className.equals(WEIGHTED_STEPS_CLASS) || className.equals(DELEGATING_STEPS_CLASS);
	}

	private static Duration percentile(List<Step> steps, double percentile) {

		if (steps.isEmpty()) {
			return Duration.ZERO;
		}

		List<Duration> durations = steps.stream().map(Step::duration).sorted().toList();
		int rank = (int) Math.ceil(percentile / 100 * durations.size());
		return durations.get(Math.min(durations.size(), Math.max(1, rank)) - 1);
	}

	private static String fractionChange(FractionRegression regression) {
		return formatFraction(regression.previousFraction()) + " → " + formatFraction(regression.fraction());
	}

	private static String bracket(String value, int width, boolean rightAligned) {
		int padding = Math.max(0, width - displayWidth(value));
		String padded = rightAligned ? " ".repeat(padding) + value : value + " ".repeat(padding);
		return "[" + padded + "]";
	}

	private static String padLeft(String value, int width) {
		return " ".repeat(Math.max(0, width - displayWidth(value))) + value;
	}

	private static int displayWidth(String value) {

		int width = 0;
		int index = 0;
		while (index < value.length()) {
			int codePoint = value.codePointAt(index);
			index += Character.charCount(codePoint);
			width += isWide(codePoint) ? 2 : 1;
		}
		return width;
	}

	private static boolean isWide(int codePoint) {
		return (codePoint >= 0x1100 && codePoint <= 0x115F) || (codePoint >= 0x2E80 && codePoint <= 0xA4CF)
				|| (codePoint >= 0xAC00 && codePoint <= 0xD7A3) || (codePoint >= 0xF900 && codePoint <= 0xFAFF)
				|| (codePoint >= 0xFF00 && codePoint <= 0xFF60) || (codePoint >= 0x1F000 && codePoint <= 0x1FAFF);
	}

	private static String describeStep(Step step) {

		if (StringUtils.hasText(step.text2())) {
			return step.text() + " | " + step.text2();
		}

		if (StringUtils.hasText(step.text())) {
			return step.text();
		}

		return "(no text)";
	}

	private Duration elapsed(long now) {
		return Duration.ofNanos(Math.max(0, now - baseNanos));
	}

	private static String normalize(@Nullable String value) {
		return value == null ? "" : value;
	}

	private static String formatDuration(Duration duration) {

		if (duration.toMillis() < 1) {
			return duration.toNanos() + "ns";
		}

		if (duration.toSeconds() < 1) {
			return duration.toMillis() + "ms";
		}

		return String.format(Locale.ROOT, "%.3fs", duration.toNanos() / 1_000_000_000d);
	}

	private static String formatFraction(double fraction) {
		return String.format(Locale.ROOT, "%.1f%%", Math.max(0, Math.min(1, fraction)) * 100);
	}

	/**
	 * Optional behavior enabled for a probing indicator.
	 */
	public enum OutputMode {

		/**
		 * Print consolidated live progress updates.
		 */
		PRINT_UPDATES,

		/**
		 * Print a final report when the probe is stopped or closed.
		 */
		REPORT_ON_FINISH,

		/**
		 * Capture step origins for navigation from slow-step reports.
		 */
		CAPTURE_TRACE
	}

	/**
	 * Progress method that produced a recorded observation.
	 */
	public enum ProgressMethod {

		START,

		STOP,

		CANCEL,

		IS_CANCELED,

		CHECK_CANCELED,

		SET_TEXT,

		SET_TEXT2,

		SET_FRACTION,

		SET_INDETERMINATE
	}

	/**
	 * Immutable capture with steps in call order and segments in completion order.
	 *
	 * @param cancellationRequest the first observed cancellation, or {@code null}
	 * if none.
	 */
	public record Snapshot(Duration duration, List<Step> steps, List<Segment> segments,
			List<FractionRegression> fractionRegressions, @Nullable CancellationRequest cancellationRequest,
			List<CancellationObservation> cancellationObservations) {

		public Snapshot {
			steps = List.copyOf(steps);
			segments = List.copyOf(segments);
			fractionRegressions = List.copyOf(fractionRegressions);
			cancellationObservations = List.copyOf(cancellationObservations);
		}

		public boolean wasCancellationRequested() {
			return cancellationRequest != null;
		}

		public boolean sawCancellationException() {
			return !cancellationObservations.isEmpty();
		}

	}

	/**
	 * A step opened by a text change or an initial fraction update.
	 *
	 * @param index the one-based step number.
	 * @param startedAfter elapsed time from the run start.
	 * @param duration elapsed duration, including time so far for an open step.
	 * @param callSite the origin, or {@code null} if tracing was disabled or no
	 * frame qualified.
	 */
	public record Step(int index, Duration startedAfter, Duration duration, String text, String text2,
			double startingFraction, double endingFraction, boolean startingIndeterminate, boolean endingIndeterminate,
			String threadName, ProgressMethod startedBy, int updates, @Nullable StackTraceElement callSite) {
	}

	/**
	 * A named block timed independently of progress steps.
	 *
	 * @param failed whether the action threw, including cancellation.
	 */
	public record Segment(String label, Duration startedAfter, Duration duration, boolean failed) {
	}

	/**
	 * A decrease from the previously observed determinate fraction.
	 */
	public record FractionRegression(Duration afterStart, double previousFraction, double fraction,
			String threadName) {
	}

	/**
	 * First indication of cancellation, whether requested locally or observed from
	 * the delegate.
	 */
	public record CancellationRequest(Duration afterStart, ProgressMethod method, String threadName) {
	}

	/**
	 * A cancellation exception observed and propagated by the probe.
	 */
	public record CancellationObservation(Duration afterStart, ProgressMethod method, String threadName,
			String exceptionType, @Nullable String message) {
	}

	private final class MutableStep {

		private final int index;

		private final long startedAtNanos;

		private final String text;

		private final String text2;

		private final double startingFraction;

		private final boolean startingIndeterminate;

		private final String threadName;

		private final ProgressMethod startedBy;

		private final @Nullable StackTraceElement callSite;

		private long endedAtNanos = -1;

		private double endingFraction;

		private boolean endingIndeterminate;

		private int updates = 1;

		private MutableStep(int index, long startedAtNanos, String text, String text2, double startingFraction,
				boolean startingIndeterminate, String threadName, ProgressMethod startedBy,
				@Nullable StackTraceElement callSite) {

			this.index = index;
			this.startedAtNanos = startedAtNanos;
			this.text = text;
			this.text2 = text2;
			this.startingFraction = startingFraction;
			this.endingFraction = startingFraction;
			this.startingIndeterminate = startingIndeterminate;
			this.endingIndeterminate = startingIndeterminate;
			this.threadName = threadName;
			this.startedBy = startedBy;
			this.callSite = callSite;
		}

		private String text() {
			return text;
		}

		private String text2() {
			return text2;
		}

		private void update(double fraction, boolean indeterminate) {
			endingFraction = fraction;
			endingIndeterminate = indeterminate;
			updates++;
		}

		private void close(long now, double fraction, boolean indeterminate) {
			endedAtNanos = now;
			endingFraction = fraction;
			endingIndeterminate = indeterminate;
			updates++;
		}

		private Step toStep(long now) {

			long end = endedAtNanos != -1 ? endedAtNanos : now;
			return new Step(index, elapsed(startedAtNanos), Duration.ofNanos(Math.max(0, end - startedAtNanos)),
					text, text2, startingFraction, endingFraction, startingIndeterminate, endingIndeterminate,
					threadName, startedBy, updates, callSite);
		}

	}

}
