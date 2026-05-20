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

package biz.paluch.dap.gradle.wrapper;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for Gradle Wrapper upgrade highlighting.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GradleWrapperHighlightingTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleWrapperFixtures.setup(fixture.getProject());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void highlightsDistributionVersion(PsiFile file) {

		GradleWrapperFixtures.analyze(file);

		assertThat(file).hasSingleGutter()
				.tooltipContains("Patch", "8.14.4")
				.highlights("8.14.3");
	}

	@Test
	@ProjectFile(name = "application.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void ignoresNonWrapperPropertiesFiles(PsiFile file) {

		GradleWrapperFixtures.analyze(file);

		assertThat(file).hasNoGutterMarks();
	}

}
