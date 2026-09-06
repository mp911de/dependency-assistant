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

import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.assertj.core.api.AbstractAssert;
import org.assertj.core.api.AssertProvider;

/**
 * Entry point for code-insight assertions.
 * <p>Use {@link Assertions} when standard AssertJ assertions are also needed.
 *
 * @author Mark Paluch
 */
public class CodeInsightAssertions {

	private CodeInsightAssertions() {
	}

	public static CodeInsightFixtureAssert assertThat(CodeInsightTestFixture fixture) {
		return new CodeInsightFixtureAssert(fixture);
	}

	/**
	 * Assertions against the current fixture state.
	 * <p>Invoke completion before checking suggestions. An absent lookup counts as
	 * an empty suggestion list.
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

		public GutterMarksAssert gutters() {
			isNotNull();
			return new GutterMarksAssert(this.actual.findAllGutters());
		}

		public GutterMarkAssert gutter(int index) {
			isNotNull();
			return gutters().gutterAt(index);
		}

		public GutterMarkAssert hasSingleGutter() {
			return gutters().hasSingleGutter();
		}

		public GutterMarkAssert hasSingleGutterContaining(String... expected) {
			return gutters().hasSingleGutterContaining(expected);
		}

		public GutterMarksAssert hasNoGutterMarks() {
			return gutters().isEmpty();
		}

		public CodeInsightFixtureAssert completionSuggests(String... expected) {
			isNotNull();
			org.assertj.core.api.Assertions.assertThat(lookupStrings()).contains(expected);
			return this;
		}

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
