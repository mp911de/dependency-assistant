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

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;

/**
 * AssertJ entry point dedicated to {@link CodeInsightTestFixture}-based
 * assertions.
 *
 * <p>Use this facade when a test only needs code-insight assertions. Prefer
 * {@link Assertions} in tests that also use standard AssertJ or dependency
 * collector assertions.
 *
 * <p>Example: <pre class="code">
 * import static biz.paluch.dap.assertions.CodeInsightAssertions.assertThat;
 *
 * assertThat(fixture).hasSingleGutterContaining("Patch", "4.0.5");
 * assertThat(fixture).hasSingleGutter().tooltipContains("Patch", "4.0.5");
 * assertThat(fixture).hasSingleGutter().hasPsiElementText("4.0.3");
 * assertThat(fixture).hasSingleGutter().hasPsiElementTextContaining("4.0");
 * assertThat(fixture).hasSingleGutter().tooltipContains("4.0.5").highlights("4.0.3");
 * assertThat(fixture).completionSuggests("6.0.3").completionExcludes("6.0.0-RC1");
 * </pre>
 *
 * @author Mark Paluch
 */
public class CodeInsightAssertions {

	private CodeInsightAssertions() {
	}

	/**
	 * Creates a new assertion for the given IntelliJ fixture.
	 * @param fixture the fixture under test.
	 * @return the created assertion object.
	 */
	public static CodeInsightFixtureAssert assertThat(CodeInsightTestFixture fixture) {
		return new CodeInsightFixtureAssert(fixture);
	}

	/**
	 * AssertJ assertions for a {@link CodeInsightTestFixture}.
	 *
	 * <p>The fixture assertion is primarily a navigation point to gutter mark
	 * assertions. Gutter marks are resolved from the current fixture state when a
	 * gutter assertion is requested.
	 */
	public static class CodeInsightFixtureAssert
			extends AbstractAssert<CodeInsightFixtureAssert, CodeInsightTestFixture>
			implements AssertProvider<CodeInsightFixtureAssert> {

		CodeInsightFixtureAssert(CodeInsightTestFixture fixture) {
			super(fixture, CodeInsightFixtureAssert.class);
		}

		/**
		 * Returns this assertion object for AssertJ {@link AssertProvider} integration.
		 */
		@Override
		public CodeInsightFixtureAssert assertThat() {
			return this;
		}

		/**
		 * Returns an assertion object for all gutter marks currently found in the
		 * fixture.
		 * @return the created gutter mark list assertion.
		 */
		public GutterMarksAssert gutters() {
			isNotNull();
			return new GutterMarksAssert(this.actual.findAllGutters());
		}

		/**
		 * Verifies that a gutter mark exists at the given index and returns an
		 * assertion object for it.
		 * @param index the zero-based gutter mark index.
		 * @return an assertion object for the selected gutter mark.
		 */
		public GutterMarkAssert gutter(int index) {
			isNotNull();
			return gutters().gutterAt(index);
		}

		/**
		 * Verifies that the fixture contains exactly one gutter mark and returns an
		 * assertion object for it.
		 * @return an assertion object for the single gutter mark.
		 */
		public GutterMarkAssert hasSingleGutter() {
			return gutters().hasSingleGutter();
		}

		/**
		 * Verifies that the fixture contains exactly one gutter mark whose tooltip
		 * contains all of the given fragments.
		 * @param expected the fragments expected in the tooltip text.
		 * @return an assertion object for the asserted gutter mark.
		 */
		public GutterMarkAssert hasSingleGutterContaining(String... expected) {
			return gutters().hasSingleGutterContaining(expected);
		}

		/**
		 * Verifies that the fixture contains no gutter marks.
		 * @return an assertion object for the fixture gutter mark list.
		 */
		public GutterMarksAssert hasNoGutterMarks() {
			return gutters().isEmpty();
		}

		/**
		 * Verifies that the current completion lookup offers all of the given elements.
		 *
		 * <p>Invoke completion (for example {@code fixture.completeBasic()}) before
		 * calling this verb. An absent lookup is treated as an empty suggestion list.
		 * @param expected the lookup strings expected to be offered.
		 * @return this assertion object.
		 */
		public CodeInsightFixtureAssert completionSuggests(String... expected) {
			isNotNull();
			org.assertj.core.api.Assertions.assertThat(lookupStrings()).contains(expected);
			return this;
		}

		/**
		 * Verifies that the current completion lookup offers none of the given
		 * elements.
		 *
		 * <p>Invoke completion (for example {@code fixture.completeBasic()}) before
		 * calling this verb. An absent lookup is treated as an empty suggestion list.
		 * @param unexpected the lookup strings expected to be absent.
		 * @return this assertion object.
		 */
		public CodeInsightFixtureAssert completionExcludes(String... unexpected) {
			isNotNull();
			org.assertj.core.api.Assertions.assertThat(lookupStrings()).doesNotContain(unexpected);
			return this;
		}

		private List<String> lookupStrings() {
			List<String> strings = this.actual.getLookupElementStrings();
			return strings == null ? List.of() : strings;
		}

	}

}
