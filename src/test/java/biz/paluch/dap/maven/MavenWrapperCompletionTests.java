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

package biz.paluch.dap.maven;

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
 * PSI-level integration tests for Maven Wrapper version completion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenWrapperCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenWrapperFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.<caret>/apache-maven-3.9.6-bin.zip
			""")
	void completesInsideFirstVersion(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("3.9.9");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-<caret>3.9.6-bin.zip
			""")
	void completesAtVersionStart(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("3.9.9");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/<caret>/apache-maven-3.9.6-bin.zip
			""")
	void completesAtEmptyPathVersionStart(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("3.9.9");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/<caret>/apache-maven-3.9.6-bin.zip
			""")
	void completesAtEmptyPathVersionStartComplete(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("3.9.9");

		fixture.finishLookup(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/<caret>/apache-maven-3.9.6-bin.zip
			""")
	void completesAtEmptyPathVersionStartTab(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("3.9.9");

		fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-<caret>-bin.zip
			""")
	void completesAtEmptyFileVersionStartTab(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("3.9.9");

		fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-<caret>-bin.zip
			""")
	void completesAtEmptyFileVersionStartComplete(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("3.9.9");

		fixture.finishLookup(Lookup.COMPLETE_STATEMENT_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-<caret>3.9.6-bin.zip
			""")
	void completesAtVersionStartTab(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("3.9.9");

		fixture.finishLookup(Lookup.REPLACE_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.<caret>-bin.zip
			""")
	void completesInsideSecondVersion(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("3.9.9");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
	}

	@Test
	@EditorFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5<caret>-bin.zip
			""")
	void doesNotOfferCompletionForGradleWrapper(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).isEmpty();
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven<caret>/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void doesNotOfferCompletionInsideArtifactId(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).isEmpty();
	}

}
