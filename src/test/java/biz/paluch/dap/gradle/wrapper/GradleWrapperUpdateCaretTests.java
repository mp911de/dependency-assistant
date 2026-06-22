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

import biz.paluch.dap.assistant.UpdateCaretTester;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Facade-based caret placement tests for the Gradle wrapper
 * {@code distributionUrl} update path.
 *
 * <p>Unlike the Maven wrapper, the Gradle distribution URL carries the version
 * only once, in the {@code gradle-VERSION-bin.zip} file name:
 * {@code GradleWrapperUtils} recognizes that single occurrence. A version
 * embedded in a custom mirror path segment is deliberately not a caret-eligible
 * occurrence, so the caret always lands behind the rewritten file-name version.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GradleWrapperUpdateCaretTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleWrapperFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.<caret>3-bin.zip
			""")
	void singleOccurrenceLandsBehindDigits(PsiFile file) {

		GradleWrapperFixtures.analyze(file);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(file).caretBetween("gradle-8.14.4", "-bin.zip");
	}

	@Test
	@EditorFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://example.com/gradle/8.14.3/gradle-8.14.<caret>3-bin.zip
			""")
	void landsBehindFileNameVersionIgnoringUnrecognizedPathVersion(PsiFile file) {

		GradleWrapperFixtures.analyze(file);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(file).caretBetween("gradle-8.14.4", "-bin.zip")
				.containsText("/gradle/8.14.3/gradle-8.14.4-bin.zip");
	}

}
