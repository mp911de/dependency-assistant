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
 * PSI-level integration tests for NPM version completion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class NpmCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		NpmFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "<caret>1.0.0-alpha.1"
			  }
			}
			""")
	void completesExactVersionAtStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3", "1.0.0-alpha.2", "1.0.0-alpha.1");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "5.3<caret>"
			  }
			}
			""")
	void completesExactVersionAtStartConsideringPrefix(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.containsOnly("5.3.1", "5.3.0");
		fixture.finishLookup(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
		assertThat(packageJson.getText()).contains("\"axios\": \"5.3.1\"");
	}


	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1<caret>
			  }
			}
			""")
	void completesExactElement(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3", "1.0.0-alpha.2", "1.0.0-alpha.1");

		fixture.finishLookup(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
		assertThat(packageJson.getText()).contains("\"@springio/antora-xref-extension\": \"1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1<caret>"
			  }
			}
			""")
	void suggestsExactVersionAtStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3", "1.0.0-alpha.2", "1.0.0-alpha.1");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson).containsText("\"@springio/antora-xref-extension\": \"1.0.0-alpha.5\"")
				.caretAfter("alpha.5");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1<caret>"
			  }
			}
			""")
	void completesStatementExactVersionAtStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3", "1.0.0-alpha.2", "1.0.0-alpha.1");

		fixture.finishLookup(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
		assertThat(packageJson).containsText("\"@springio/antora-xref-extension\": \"1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0<caret>.0-alpha.1"
			  }
			}
			""")
	void completesExactVersionInsideVersion(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson).containsText("\"@springio/antora-xref-extension\": \"1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "<caret>^1.0.0-alpha.1"
			  }
			}
			""")
	void completesExactWithModifierAtStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson).containsText("\"1.0.0-alpha.5")
				.caretAfter("alpha.5");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "^<caret>1.0.0-alpha.1"
			  }
			}
			""")
	void completesExactWithModifierAfterModifier(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson).containsText("\"^1.0.0-alpha.5")
				.caretAfter("alpha.5");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "^1.<caret>0.0-alpha.1"
			  }
			}
			""")
	void completesExactWithModifierInsideVersion(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson.getText()).contains("\"@springio/antora-xref-extension\": \"^1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "<caret>1.0.0-alpha.1 - 1.0.0-alpha.5"
			  }
			}
			""")
	void completesHyphenRangeAtStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson.getText())
				.contains("@springio/antora-xref-extension\": \"1.0.0-alpha.5 - 1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.1 - 1.<caret>0.0-alpha.3"
			  }
			}
			""")
	void completesHyphenRangeAtSecondElement(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson.getText())
				.contains("@springio/antora-xref-extension\": \"1.0.0-alpha.1 - 1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.1 - 1<caret>"
			  }
			}
			""")
	void completesHyphenRangeAtSecondElementStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson.getText())
				.contains("@springio/antora-xref-extension\": \"1.0.0-alpha.1 - 1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0<caret>.0-alpha.1 - 1.0.0-alpha.5"
			  }
			}
			""")
	void completesHyphenRangeInsideLowerBound(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": ">=1.0.0-alpha.1 <<caret>1.0.0-alpha.5"
			  }
			}
			""")
	void completesComparatorAtOfSecondVersion(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": ">=1<caret>"
			  }
			}
			""")
	void completesComparatorAtFirstVersionStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson.getText()).contains("@springio/antora-xref-extension\": \">=1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": ">=1.0.0-alpha.1 <1<caret>"
			  }
			}
			""")
	void completesComparatorAtOfSecondVersionStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson.getText())
				.contains("\">=1.0.0-alpha.1 <1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": ">=1.<caret>0.0-alpha.1 <1.0.0-alpha.5"
			  }
			}
			""")
	void completesComparatorPairInsideLowerBound(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson)
				.containsText("\">=1.0.0-alpha.5 <1.0.0-alpha.5\"")
				.caretAfter("alpha.5").caretBefore(" <1");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "<caret>1.0.x"
			  }
			}
			""")
	void completesPrefixRangeAtStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		// Prefix entries are non-updatable but the contributor still surfaces
		// suggestions so the user can act manually.
		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0<caret>.x"
			  }
			}
			""")
	void completesPrefixRangeInsidePrefix(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "npm:@springio/antora-xref-extension@<caret>1.0.0-alpha.1"
			  }
			}
			""")
	void completesAliasAtStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "npm:@springio/antora-xref-extension@^<caret>1.0.0-alpha.1"
			  }
			}
			""")
	void completesAtAliasVersionStart(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "git+https://github.com/spring-io/antora-xref-extension.git#1.<caret>0.0-alpha.1"
			  }
			}
			""")
	void completesGitUrlInsideVersion(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "git+https://github.com/owner/repo.git#semver:<caret>"
			  }
			}
			""")
	void completesGitUrlAfterSemver(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.complete(CompletionType.BASIC);

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "git+https://github.com/owner/repo.git#semver:1<caret>"
			  }
			}
			""")
	void completesGitUrlAfterSemverType(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson).containsText(
				"\"@springio/antora-xref-extension\": \"git+https://github.com/owner/repo.git#semver:1.0.0-alpha.5\"")
				.caretAfter("alpha.5");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "springio/antora-xref-extension#1<caret>"
			  }
			}
			""")
	void completesGitHubUrlAfterHash(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson.getText())
				.contains("\"@springio/antora-xref-extension\": \"springio/antora-xref-extension#1.0.0-alpha.5\"");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "springio/antora-xref-extension#aaa<caret>bbdde"
			  }
			}
			""")
	void completesGitHubUrlInCommit(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings())
				.contains("1.0.0-alpha.5", "1.0.0-alpha.4", "1.0.0-alpha.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(packageJson)
				.containsText("\"@springio/antora-xref-extension\": \"springio/antora-xref-extension#7b4f3880\"")
				.caretAfter("7b4f3880");
	}

}
