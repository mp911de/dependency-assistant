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

package biz.paluch.dap.gradle;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for TOML completion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class TomlCompletionTests {

	// TODO extend test cases
	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "gradle/libs.versions.toml", content = """
			[libraries]
			junit = { module = "org.junit:junit-bom", version = "6.0.<caret>" }
			""")
	void completesQuotedVersionLiteral(PsiFile tomlFile) {

		GradleFixtures.analyze(tomlFile);

		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings()).contains("6.0.3");
	}

}
