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

package biz.paluch.dap.npm;

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
 * Facade-based caret placement tests for the NPM update path, with and without
 * a range operator on the version literal.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class NpmUpdateCaretTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		NpmFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "5.4.<caret>0"
			  }
			}
			""")
	void exactVersionLandsBehindDigits(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(packageJson).caretBetween("\"axios\": \"6.0.0", "\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "^5.4.<caret>0"
			  }
			}
			""")
	void rangeOperatorLandsBehindDigitsAfterOperator(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(packageJson).caretBetween("\"axios\": \"^6.0.0", "\"");
	}

}
