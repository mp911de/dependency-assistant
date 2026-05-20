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

package biz.paluch.dap.maven.wrapper;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests highlighting.
 * 
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenWrapperHighlightingTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenWrapperFixtures.setup(fixture.getProject());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void highlightsDistributionVersion(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		assertThat(file).hasSingleGutter()
				.tooltipContains("Patch", "3.9.9")
				.highlights("3.9.6");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			d\\u0069stribution\\u0055rl=\\u0068ttps\\://repo1\\u002emaven\\u002eorg/maven2/org/apache/maven/apache-maven/3\\u002e9\\u002e6/apache-maven-3\\u002e9\\u002e6-bin\\u002ezip
			""")
	void highlightsEncodedDistributionVersion(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		assertThat(file).hasSingleGutter()
				.tooltipContains("Patch", "3.9.9")
				.highlights("3\\u002e9\\u002e6");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
			""")
	void highlightsWrapperVersion(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		assertThat(file).hasSingleGutter()
				.tooltipContains("Patch", "3.3.3")
				.highlights("3.3.2");
	}

	@Test
	@ProjectFile(name = "application.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void getHighlightRangeFallsBackToElementRangeForNonWrapperProperty(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		assertThat(file).hasNoGutterMarks();
	}

}
