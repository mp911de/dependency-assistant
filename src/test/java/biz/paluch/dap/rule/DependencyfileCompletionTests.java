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

package biz.paluch.dap.rule;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link DependencyfileCompletionContributor}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class DependencyfileCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		DependencyfileCompletionFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "<caret>"
			  }
			}
			""")
	void suggestsArtifactPatternsFromStateService(PsiFile file) {

		fixture.complete(CompletionType.BASIC);

		assertThat(fixture).completionSuggests(
				"org.springframework:spring-core",
				"org.springframework:spring-web",
				"org.junit.jupiter:junit-jupiter");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "<caret>"
			  }
			}
			""")
	void acceptingTopLevelSuggestionInsertsNameTemplate(PsiFile file) {

		fixture.complete(CompletionType.BASIC);
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file).containsText("\"name\": \"name\"");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:spring-core": { "generation": "6.2.x" },
			    <caret>
			  }
			}
			""")
	void acceptingTopLevelSuggestionFromWhitespaceInsertsQuotedEntry(PsiFile file) {

		fixture.complete(CompletionType.BASIC);
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file)
				.containsText("\"org.junit.jupiter:junit-jupiter\": {")
				.containsText("\"name\": \"name\"");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:spring-core": { "generation": "6.2.x" },
			    <caret>
			  }
			}
			""")
	void suggestsArtifactPatternsFromWhitespaceBetweenProperties(PsiFile file) {

		fixture.complete(CompletionType.BASIC);

		assertThat(fixture).completionSuggests(
				"org.springframework:spring-web",
				"org.junit.jupiter:junit-jupiter");

		assertThat(fixture.getLookupElementStrings())
				.doesNotContain("org.springframework:spring-core");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:spring-core": { "generation": "6.2.x" },
			    "<caret>"
			  }
			}
			""")
	void excludesAlreadyDeclaredKeysFromSuggestions(PsiFile file) {

		fixture.complete(CompletionType.BASIC);

		assertThat(fixture).completionSuggests(
				"org.springframework:spring-web",
				"org.junit.jupiter:junit-jupiter");

		assertThat(fixture.getLookupElementStrings())
				.doesNotContain("org.springframework:spring-core");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {},
			  "branches": {
			    "3.5.x": {
			      "artifacts": {
			        "<caret>"
			      }
			    }
			  }
			}
			""")
	void suggestsArtifactPatternsInsideBranchArtifacts(PsiFile file) {

		fixture.complete(CompletionType.BASIC);

		assertThat(fixture).completionSuggests(
				"org.springframework:spring-core",
				"org.springframework:spring-web",
				"org.junit.jupiter:junit-jupiter");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:spring-core": "6.2.<caret>x"
			  }
			}
			""")
	void doesNotSuggestArtifactPatternsInsideGenerationValue(PsiFile file) {

		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings()).doesNotContain("org.springframework:spring-web");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "@antora:atlas-extension": {
			      "<caret>": "*"
			    }
			  }
			}
			""")
	void doesNotSuggestArtifactPatternsInsideRuleObject(PsiFile file) {

		fixture.complete(CompletionType.BASIC);

		assertThat(fixture)
				.completionExcludes(
						"org.springframework:spring-core",
						"org.springframework:spring-web",
						"org.junit.jupiter:junit-jupiter");
	}

}
