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

import biz.paluch.dap.assistant.UpdateCaretTester;
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
 * Facade-based caret placement tests for the Gradle update path across the
 * Groovy DSL, Kotlin DSL, version catalog TOML, {@code gradle.properties}, and
 * {@code ext} property surfaces.
 *
 * <p>The Gradle inline dependency-notation completion path deliberately does
 * not route through {@code applyUpdate} (see issue 05); its caret behavior is
 * verified in the per-surface completion suites. Here the inline-notation case
 * is exercised through the update path, which rewrites the version literal like
 * every other format.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GradleUpdateCaretTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.<caret>0'
			}
			""")
	void groovyInlineNotation(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		new UpdateCaretTester(fixture).applyBestUpgradeViaModCommand();

		assertThat(buildFile).caretAfter("org.junit:junit-bom:6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:6.0.<caret>0")
			}
			""")
	void kotlinDsl(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(buildFile).caretAfter("org.junit:junit-bom:6.0.3");
	}

	@Test
	@EditorFile(name = "gradle/libs.versions.toml", content = """
			[libraries]
			junit = { module = "org.junit:junit-bom", version = "6.0.<caret>0" }
			""")
	void catalogTomlInlineVersion(PsiFile tomlFile) {

		GradleFixtures.analyze(tomlFile);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(tomlFile).caretBetween("6.0.3", "\" }");
	}

	@Test
	@EditorFile(name = "libs.versions.toml", content = """
			[versions]
			junit = "6.0.<caret>0"

			[libraries]
			junit-jupiter = { module = "org.junit:junit-bom", version.ref = "junit" }
			""")
	void catalogTomlVersionsTable(PsiFile tomlFile) {

		GradleFixtures.analyze(tomlFile);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(tomlFile).caretBetween("junit = \"6.0.3", "\"");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junitVersion
			}
			""")
	@EditorFile(name = "gradle.properties", content = """
			junitVersion=6.0.<caret>0
			""")
	void gradleProperties(@ProjectFile("gradle.properties") PsiFile propertiesFile,
			@ProjectFile("build.gradle") PsiFile buildFile) {

		GradleFixtures.analyze(buildFile, Map.of("junitVersion", "6.0.0"));
		propertiesFile.putUserData(GradleProjectContext.KEY, buildFile.getUserData(GradleProjectContext.KEY));

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(propertiesFile).caretAfter("junitVersion=6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext {
			    junit = '6.0.<caret>0'
			}
			dependencies {
			    implementation "org.junit:junit-bom:${junit}"
			}
			""")
	void extProperty(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(buildFile).caretBetween("junit = '6.0.3", "'");
	}

}
