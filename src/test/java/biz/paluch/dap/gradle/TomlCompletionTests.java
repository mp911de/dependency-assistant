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
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;


/**
 * PSI-level integration tests for TOML completion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class TomlCompletionTests {

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
		assertThat(fixture).completionSuggests("6.0.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(tomlFile).containsText("version = \"6.0.3\" }");
	}

	@Test
	@EditorFile(name = "gradle/libs.versions.toml", content = """
			[libraries]
			junit = { module = "org.junit:junit-bom", version = "<caret>6.0.3" }
			""")
	void completesQuotedVersionLiteralAtStart(PsiFile tomlFile) {

		GradleFixtures.analyze(tomlFile);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("6.0.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(tomlFile).containsText("version = \"6.1.0-M1\" }").caretBetween("6.1.0-M1", "\"");
	}

	@Test
	@EditorFile(name = "libs.versions.toml", content = """
			[versions]
			junit = "6.0.<caret>"

			[libraries]
			junit-jupiter = { module = "org.junit:junit-bom", version.ref = "junit" }
			""")
	void completesQuotedPropertyLiteral(PsiFile tomlFile) {

		GradleFixtures.analyze(tomlFile);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("6.0.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(tomlFile).containsText("junit = \"6.0.3\"").caretBetween("6.0.3", "\"");
	}


}
