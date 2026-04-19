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
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for highlighting Groovy DSLs.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GroovyDslTomlHighlightingTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			spring-dependency-management = "1.1.1"

			[plugins]
			spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
			""")
	@EditorFile(name = "build.gradle", content = """
			plugins {
			    alias(libs.plugins.spring.dependency.management)
			}
			""")
	void plugin(PsiFile toml, PsiFile buildFile) {

		GradleFixtures.analyze(toml, buildFile);
		assertThat(fixture).gutters().hasSingleGutter().tooltipContains("Patch", "1.1.7").hasNavigation();
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[libraries]
			junit = { module = "org.junit:junit-bom", version = "6.0.0" }
			""")
	@EditorFile(name = "build.gradle", content = """
			dependencies {
				    implementation libs.junit
				}
			""")
	void dependency(PsiFile toml, PsiFile buildFile) {

		GradleFixtures.analyze(toml, buildFile);
		assertThat(fixture).gutters().hasSingleGutter().tooltipContains("Patch", "6.0.3").hasNavigation();
	}

}
