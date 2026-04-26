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

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;

/**
 * AssertJ entry point for {@link CodeInsightTestFixture}-based assertions.
 *
 * <p>Typical usage: <pre class="code">
 * import static ...CodeInsightAssertions.assertThat;
 *
 * assertThat(fixture).hasSingleGutterContaining("Patch", "4.0.5");
 * assertThat(fixture).hasSingleGutter().tooltipContains("Patch", "4.0.5");
 * assertThat(fixture).hasSingleGutter().hasPsiElementText("4.0.3");
 * assertThat(fixture).hasSingleGutter().hasPsiElementTextContaining("4.0");
 * </pre>
 *
 * @author Mark Paluch
 */
public class CodeInsightAssertions {

	private CodeInsightAssertions() {
	}

	/**
	 * Create an assertion for the given {@link CodeInsightTestFixture}.
	 */
	public static CodeInsightFixtureAssert assertThat(CodeInsightTestFixture fixture) {
		return new CodeInsightFixtureAssert(fixture);
	}


	/**
	 * AssertJ assertions for {@link CodeInsightTestFixture}.
	 */
	public static class CodeInsightFixtureAssert
			extends AbstractAssert<CodeInsightFixtureAssert, CodeInsightTestFixture>
			implements AssertProvider<CodeInsightFixtureAssert> {

		CodeInsightFixtureAssert(CodeInsightTestFixture fixture) {
			super(fixture, CodeInsightFixtureAssert.class);
		}

		@Override
		public CodeInsightFixtureAssert assertThat() {
			return this;
		}

		/**
		 * Navigate to gutter mark assertions for all gutters found in the fixture.
		 */
		public GutterMarksAssert gutters() {
			isNotNull();
			return new GutterMarksAssert(this.actual.findAllGutters());
		}

		/**
		 * Navigate to gutter mark assertions for all gutters found in the fixture.
		 */
		public GutterMarkAssert gutter(int index) {
			isNotNull();
			return gutters().gutterAt(index);
		}

		/**
		 * Verify a single gutter mark is present and navigate to it.
		 */
		public GutterMarkAssert hasSingleGutter() {
			return gutters().hasSingleGutter();
		}

		/**
		 * Verify a single gutter mark is present and that its tooltip contains all of
		 * the given strings.
		 */
		public GutterMarksAssert hasSingleGutterContaining(String... expected) {
			return gutters().hasSingleGutterContaining(expected);
		}

		/**
		 * Verify that no gutter marks are present.
		 */
		public GutterMarksAssert hasNoGutterMarks() {
			return gutters().isEmpty();
		}

	}
}
