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

import java.util.Map;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for Gradle properties completion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GradlePropertiesCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junitVersion
			}
			""")
	@EditorFile(name = "gradle.properties", content = """
			junitVersion=<caret>
			""")
	void completesGradlePropertyValueInitial(@ProjectFile("gradle.properties") PsiFile propertiesFile,
			@ProjectFile("build.gradle") PsiFile buildFile) {

		prepareProjectState(propertiesFile, buildFile);

		fixture.type('6');
		fixture.completeBasic();

		assertThat(fixture).completionSuggests("6.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junitVersion
			}
			""")
	@EditorFile(name = "gradle.properties", content = """
			junitVersion=6.0.<caret>
			""")
	void completesGradlePropertyValue(@ProjectFile("gradle.properties") PsiFile propertiesFile,
			@ProjectFile("build.gradle") PsiFile buildFile) {

		prepareProjectState(propertiesFile, buildFile);

		fixture.completeBasic();

		assertThat(fixture).completionSuggests("6.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junitVersion
			}
			""")
	@EditorFile(name = "gradle.properties", content = """
			junit<caret>Version=6.0.0
			""")
	void doesNotCompleteGradlePropertyKey(@ProjectFile("gradle.properties") PsiFile propertiesFile,
			@ProjectFile("build.gradle") PsiFile buildFile) {

		prepareProjectState(propertiesFile, buildFile);

		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings()).isEmpty();
	}

	private static void prepareProjectState(PsiFile propertiesFile, PsiFile buildFile) {

		GradleFixtures.analyze(buildFile, Map.of("junitVersion", "6.0.0"));
		propertiesFile.putUserData(GradleProjectContext.KEY, buildFile.getUserData(GradleProjectContext.KEY));
	}

}
