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

package biz.paluch.dap.assertions;

import java.util.List;

import com.intellij.codeInsight.daemon.GutterMark;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.error.MessageFormatter;

/**
 * AssertJ assertions for a list of {@link GutterMark} instances.
 *
 * <p>This assertion type represents the collection-level view of line marker
 * output. Methods either keep asserting on the collection or navigate to
 * {@link GutterMarkAssert} once the requested marker is known to exist.
 *
 * <p>Example: <pre class="code">
 * assertThat(fixture).gutters().hasSize(2);
 * assertThat(fixture).gutters().gutterAt(0).tooltipContains("Patch");
 * assertThat(fixture).gutters().hasSingleGutter().highlights("6.0.0");
 * </pre>
 *
 * @author Mark Paluch
 */
public class GutterMarksAssert
		extends AbstractAssert<GutterMarksAssert, List<GutterMark>> {

	/**
	 * Creates a new assertion object for the given gutter marks.
	 * @param gutterMarks the gutter marks under test.
	 */
	public GutterMarksAssert(List<GutterMark> gutterMarks) {
		super(gutterMarks, GutterMarksAssert.class);
	}

	/**
	 * Verifies that the actual list contains exactly one gutter mark whose tooltip
	 * contains all of the given fragments.
	 * @param expected the fragments expected in the tooltip text.
	 * @return this assertion object.
	 */
	public GutterMarkAssert hasSingleGutterContaining(String... expected) {
		return hasSingleGutter().tooltipContains(expected);
	}

	/**
	 * Verifies that the actual list contains exactly the given number of gutter
	 * marks.
	 * @param expected the expected number of gutter marks.
	 * @return this assertion object.
	 */
	public GutterMarksAssert hasSize(int expected) {
		isNotNull();
		int actual = this.actual.size();
		if (actual != expected) {

			String gutters = MessageFormatter.instance().format(info.description(), info.representation(), "%s",
					this.actual());
			failWithMessage("Expected %d gutter mark(s) but found %d: %s",
					expected, actual, gutters);
		}
		return this;
	}

	/**
	 * Verifies that the actual list contains exactly one gutter mark and returns an
	 * assertion object for it.
	 * @return an assertion object for the single gutter mark.
	 */
	public GutterMarkAssert hasSingleGutter() {
		hasSize(1);
		return new GutterMarkAssert(this.actual.get(0));
	}

	/**
	 * Verifies that a gutter mark exists at the given index and returns an
	 * assertion object for it.
	 * @param index the zero-based gutter mark index.
	 * @return an assertion object for the selected gutter mark.
	 */
	public GutterMarkAssert gutter(int index) {
		return gutterAt(index);
	}

	/**
	 * Verifies that a gutter mark exists at the given index and returns an
	 * assertion object for it.
	 * @param index the zero-based gutter mark index.
	 * @return an assertion object for the selected gutter mark.
	 */
	public GutterMarkAssert gutterAt(int index) {
		isNotNull();
		if (index < 0 || index >= this.actual.size()) {
			failWithMessage("No gutter mark at index %d; found %d gutter mark(s)",
					index, this.actual.size());
		}
		return new GutterMarkAssert(this.actual.get(index));
	}

	/**
	 * Verifies that the actual list contains no gutter marks.
	 * @return this assertion object.
	 */
	public GutterMarksAssert isEmpty() {
		isNotNull();
		int actual = this.actual.size();
		if (actual != 0) {
			String gutters = MessageFormatter.instance().format(info.description(), info.representation(), "%s",
					this.actual());
			failWithMessage("Expected no gutter marks but found %d: %s",
					actual, gutters);
		}
		return this;
	}

	/**
	 * Verifies that the actual list contains no gutter marks.
	 * @return this assertion object.
	 */
	public GutterMarksAssert hasNoGutterMarks() {
		return isEmpty();
	}

}
