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
 * @author Mark Paluch
 */
public class GutterMarksAssert
		extends AbstractAssert<GutterMarksAssert, List<GutterMark>> {

	public GutterMarksAssert(List<GutterMark> gutterMarks) {
		super(gutterMarks, GutterMarksAssert.class);
	}

	/**
	 * Verify a single gutter mark is present and that its tooltip contains all of
	 * the given strings.
	 */
	public GutterMarksAssert hasSingleGutterContaining(String... expected) {
		hasSingleGutter().tooltipContains(expected);
		return this;
	}

	/**
	 * Verify that exactly the given number of gutter marks are present.
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
	 * Verify that exactly one gutter mark is present and navigate to it.
	 */
	public GutterMarkAssert hasSingleGutter() {
		hasSize(1);
		return new GutterMarkAssert(this.actual.get(0));
	}

	/**
	 * Navigate to the gutter mark at the given index.
	 */
	public GutterMarkAssert gutter(int index) {
		return gutterAt(index);
	}

	/**
	 * Navigate to the gutter mark at the given index.
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
	 * Verify that no gutter marks are present.
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
	 * Verify that no gutter marks are present.
	 */
	public GutterMarksAssert hasNoGutterMarks() {
		return isEmpty();
	}

}
