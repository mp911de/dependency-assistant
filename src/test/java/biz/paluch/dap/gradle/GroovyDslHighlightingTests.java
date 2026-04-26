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
class GroovyDslHighlightingTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	// -------------------------------------------------------------------------
	// Plugins
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			plugins {
			    id 'org.springframework.boot' version '4.0.3'
			}
			""")
	void suggestsPluginUpgrade(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(buildFile).hasSingleGutterContaining("Patch", "4.0.5");
	}

	// -------------------------------------------------------------------------
	// GAV style
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.0'
			}
			""")
	void gavInlineVersion(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	// -------------------------------------------------------------------------
	// Constraints
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.0!!'
			}
			""")
	void gavEnforcedVersion(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[5.2.0, 6.0.0]'
			}
			""")
	void gavRange1(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[5.2.0,6.0.0]'
			}
			""")
	void gavRange2(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[5.0, 7.0[!!6.0.0'
			}
			""")
	void gavRangeStrictly(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[6.0.0,)'
			}
			""")
	void gavOpenRange(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:(5.2,6.0.0]'
			}
			""")
	void gavClosedRange(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:0.10.+'
			}
			""")
	void gavPrefixVersion(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(buildFile).hasSingleGutterContaining("Major", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:latest.release'
			}
			""")
	void gavLatestReleaseVersion(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasNoGutterMarks();
	}

	// -------------------------------------------------------------------------
	// Version block
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '6.0.0'
			        }
			    }
			}
			""")
	void gavStrictly(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '[5.0, 6.1['
			            prefer '6.0.0'
			        }
			    }
			}
			""")
	void gavStrictlyPrefer(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly ("[5.0, 6.1[")
			            prefer ("6.0.0")
			        }
			    }
			}
			""")
	void gavStrictlyFunctionPrefer(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutter().tooltipContains("Patch", "6.0.3").hasPsiElementTextContaining("6.0.0");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: '6.0.0'
			}
			""")
	void mapNotationWithVersionLiteral(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).hasSingleGutter().tooltipContains("Patch", "6.0.3").hasPsiElementTextContaining("6.0.0");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junit
			}
			""")
	void mapNotationWithVersionVariable(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).gutters().hasSize(2);
		assertThat(fixture).gutter(0).hasNoNavigation();
		// variables are treated like local properties
		assertThat(fixture).gutter(1).hasNoNavigation();
	}

	// -------------------------------------------------------------------------
	// Properties
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			junit=6.0.0
			""")
	@EditorFile(name = "build.gradle", content = """
			dependencyManagement {
			    imports {
			        mavenBom "org.junit:junit-bom:${junit}"
			    }
			}
			""")
	void gradleProperty(PsiFile properties, @ProjectFile("build.gradle") PsiFile buildFile) {

		GradleFixtures.analyze(properties, buildFile);

		assertThat(fixture).hasSingleGutter().hasPsiElementTextContaining("${junit}").hasNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext {
			    set('junit', "6.0.0")
			}
			dependencyManagement {
			    imports {
			        mavenBom "org.junit:junit-bom:${junit}"
			    }
			}
			""")
	void extPropertySet(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).gutters().hasSize(2);
		assertThat(fixture).gutter(0).hasNoNavigation();
		assertThat(fixture).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext {
			    junit='6.0.0'
			}
			dependencyManagement {
			    imports {
			        mavenBom "org.junit:junit-bom:${junit}"
			    }
			}
			""")
	void extPropertyAssignment(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).gutters().hasSize(2);
		assertThat(fixture).gutter(0).hasNoNavigation();
		assertThat(fixture).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext.junit = '6.0.0'

			dependencyManagement {
			    imports {
			        mavenBom "org.junit:junit-bom:${junit}"
			    }
			}
			""")
	void extProperty(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).gutters().hasSize(2);
		assertThat(fixture).gutter(0).hasNoNavigation();
		assertThat(fixture).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext.junit = '6.0.0'
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: "${junit}"
			}
			""")
	void mapNotationWithVersionProperty(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).gutters().hasSize(2);
		assertThat(fixture).gutter(0).hasNoNavigation(); // ext.junit
		assertThat(fixture).gutter(1).hasNoNavigation(); // ${junit}
	}

	// -------------------------------------------------------------------------
	// Constraints + Properties
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext.junit = '6.0.0'
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly "${junit}"
			        }
			    }
			}
			""")
	void gavStrictlyWithVersionProperty(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).gutters().hasSize(2);
		assertThat(fixture).gutter(0).hasNoNavigation();
		assertThat(fixture).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly junit
			        }
			    }
			}
			""")
	void gavStrictlyWithVersionVariable(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).gutters().hasSize(2);
		assertThat(fixture).gutter(0).hasNoNavigation();
		assertThat(fixture).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext.junit = '6.0.0'
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '[5.0, 6.1['
			            prefer "${junit}"
			        }
			    }
			}
			""")
	void gavStrictlyPreferWithVersionProperty(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).gutters().hasSize(2);
		assertThat(fixture).gutter(0).hasNoNavigation();
		assertThat(fixture).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '[5.0, 6.1['
			            prefer junit
			        }
			    }
			}
			""")
	void gavStrictlyPreferWithVersionVariable(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		assertThat(fixture).gutters().hasSize(2);
		assertThat(fixture).gutter(0).hasNoNavigation();
		assertThat(fixture).gutter(1).hasNoNavigation();
	}

}
