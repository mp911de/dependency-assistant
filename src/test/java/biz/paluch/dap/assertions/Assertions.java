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

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.assertions.CodeInsightAssertions.CodeInsightFixtureAssert;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;

/**
 * Project-specific assertions.
 *
 * @author Mark Paluch
 */
public class Assertions extends org.assertj.core.api.Assertions {

	/**
	 * Create an assertion for the given {@link CodeInsightTestFixture}.
	 */
	public static CodeInsightFixtureAssert assertThat(CodeInsightTestFixture fixture) {
		return new CodeInsightFixtureAssert(fixture);
	}

	/**
	 * Create an assertion for the given {@link CodeInsightTestFixture}.
	 */
	public static DependencyCollectorAssert assertThat(DependencyCollector collector) {
		return new DependencyCollectorAssert(collector);
	}

	/**
	 * Create an assertion for the given {@link PsiFile}.
	 */
	public static GutterMarksAssert assertThat(PsiFile file) {
		return LineMarkers.of(file).assertThat();
	}

}
