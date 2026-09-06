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

package biz.paluch.dap.assertions;

import java.util.List;

import com.intellij.codeInsight.daemon.GutterMark;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.error.MessageFormatter;

/**
 * Assertions for gutter marks with navigation to individual markers.
 *
 * @author Mark Paluch
 */
public class GutterMarksAssert
		extends AbstractAssert<GutterMarksAssert, List<GutterMark>> {

	public GutterMarksAssert(List<GutterMark> gutterMarks) {
		super(gutterMarks, GutterMarksAssert.class);
	}

	public GutterMarkAssert hasSingleGutterContaining(String... expected) {
		return hasSingleGutter().tooltipContains(expected);
	}

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

	public GutterMarkAssert hasSingleGutter() {
		hasSize(1);
		return new GutterMarkAssert(this.actual.get(0));
	}

	public GutterMarkAssert gutter(int index) {
		return gutterAt(index);
	}

	public GutterMarkAssert gutterAt(int index) {
		isNotNull();
		if (index < 0 || index >= this.actual.size()) {
			failWithMessage("No gutter mark at index %d; found %d gutter mark(s)",
					index, this.actual.size());
		}
		return new GutterMarkAssert(this.actual.get(index));
	}

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

	public GutterMarksAssert hasNoGutterMarks() {
		return isEmpty();
	}

}
