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
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Wrapper properties completion PSI-level tests for
 * {@link GradleWrapperCompletionContributor}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GradleWrapperCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleWrapperFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=<caret>
			""")
	void completesEmptyDistributionUrlWithDefaultUrlFromCache(PsiFile file) {

		fixture.completeBasic();
		assertThat(fixture)
				.completionSuggests("distributionUrl=https://services.gradle.org/distributions/gradle-9.5.1-bin.zip");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file)
				.containsText("distributionUrl=https://services.gradle.org/distributions/gradle-9.5.1-bin.zip")
				.caretBetween("gradle-9.5.1", "-bin.zip")
				.doesNotContainText("distributionSha256Sum");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=<caret>
			""")
	void completesEmptyDistributionUrlWithFallbackWhenCacheEmpty() {

		StateService.getInstance(fixture.getProject()).setCache(new Cache());

		fixture.completeBasic();
		assertThat(fixture)
				.completionSuggests("distributionUrl=https://services.gradle.org/distributions/gradle-9.5.1-bin.zip");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			d<caret>
			""")
	void completesDistributionLine(PsiFile file) {

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file)
				.containsText("distributionUrl=https://services.gradle.org/distributions/gradle-9.5.1-bin.zip")
				.caretBetween("gradle-9.5.1", "-bin.zip")
				.doesNotContainText("distributionSha256Sum");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """

			<caret>
			""")
	void completesDistributionLineAtEmptyLine(PsiFile file) {

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file)
				.containsText("distributionUrl=https://services.gradle.org/distributions/gradle-9.5.1-bin.zip")
				.caretBetween("gradle-9.5.1", "-bin.zip")
				.doesNotContainText("distributionSha256Sum");
	}

	@Test
	@EditorFile(name = "gradle.properties", content = """
			d<caret>
			""")
	void doesNotOfferLineCompletionInNonWrapperPropertiesFile() {

		fixture.completeBasic();
		assertThat(fixture).completionExcludes("distributionUrl");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.<caret>14.3-bin.zip
			distributionSha256Sum=xyz
			""")
	void completesInsideDistributionVersion(PsiFile file) {

		GradleWrapperFixtures.analyze(file);

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file)
				.containsText("gradle-8.14.4-bin.zip")
				.caretBetween("gradle-8.14.4", "-bin.zip")
				.doesNotContainText("distributionSha256Sum=xyz")
				.containsText("distributionSha256Sum=sha-8.14.4");
	}

}
