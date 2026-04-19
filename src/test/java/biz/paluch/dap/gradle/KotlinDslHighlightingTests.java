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
 * PSI-level integration tests for highlighting Kotlin DSL build scripts.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class KotlinDslHighlightingTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	// -------------------------------------------------------------------------
	// GAV style
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "src/build.gradle.kts", content = """
			plugins {
			    id("org.springframework.boot") version "4.0.3"
			}
			""")
	void suggestsPluginUpgrade(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutterContaining("Patch", "4.0.5");
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:6.0.0")
			}
			""")
	void gav(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(platform("org.junit:junit-bom:6.0.0"))
			}
			""")
	void gavPlatform(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutterContaining("Patch", "6.0.3");
	}


	// -------------------------------------------------------------------------
	// Constraints
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("6.0.0")
			        }
			    }
			}
			""")
	void gavStrictly(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("[5.0, 6.1[")
			            prefer("6.0.0")
			        }
			    }
			}
			""")
	void gavStrictlyPrefer(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutterContaining("Patch", "6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("[5.0, 6.1[")
			            prefer("6.0.0")
			        }
			    }
			}
			""")
	void gavStrictlyFunctionPrefer(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutter().tooltipContains("Patch", "6.0.3")
				.hasPsiElementTextContaining("6.0.0");
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(group = "org.junit", name = "junit-bom", version = "6.0.0")
			}
			""")
	void mapNotationWithVersionLiteral(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutter().tooltipContains("Patch", "6.0.3")
				.hasPsiElementTextContaining("6.0.0");
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation(group = "org.junit", name = "junit-bom", version = junit)
			}
			""")
	void mapNotationWithVersionVariable(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		// variables are treated like local properties
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    constraints {
			        implementation("org.junit:junit-bom:6.0.0")
			    }
			}
			""")
	void dependencyConstraintLiteral(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutter().tooltipContains("Patch", "6.0.3")
				.hasPsiElementTextContaining("6.0.0");
	}


	// -------------------------------------------------------------------------
	// Properties
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			junit=6.0.0
			""")
	@EditorFile(name = "build.gradle.kts", content = """
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${property("junit")}")
			    }
			}
			""")
	void gradlePropertyViaProperty(PsiFile properties, @ProjectFile("build.gradle.kts") PsiFile buildFile) {
		GradleFixtures.analyze(properties, buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutter().hasNavigation();
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			junit=6.0.0
			""")
	@EditorFile(name = "build.gradle.kts", content = """
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${project.property("junit")}")
			    }
			}
			""")
	void gradlePropertyViaProjectProperty(PsiFile properties, @ProjectFile("build.gradle.kts") PsiFile buildFile) {
		GradleFixtures.analyze(properties, buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSingleGutter().hasNavigation();
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			junit=6.0.0
			""")
	@EditorFile(name = "build.gradle.kts", content = """
			val junit: String by project
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${junit}")
			    }
			}
			""")
	void gradlePropertyByProjectDelegate(PsiFile properties, @ProjectFile("build.gradle.kts") PsiFile buildFile) {
		GradleFixtures.analyze(properties, buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			val junit: String by extra
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${junit}")
			    }
			}
			""")
	void extraPropertyByDelegate(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		// same file
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			val junit by extra("6.0.0")
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${junit}")
			    }
			}
			""")
	void extraPropertyWithDefault(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		// same file
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			dependencyManagement {
			    imports {
			        mavenBom("org.junit:junit-bom:${extra["junit"]}")
			    }
			}
			""")
	void extPropertySet(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		// same file
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			dependencies {
			    implementation(group = "org.junit", name = "junit-bom", version = "${extra["junit"]}")
			}
			""")
	void mapNotationWithVersionProperty(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	// -------------------------------------------------------------------------
	// Constraints + Properties
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("${extra["junit"]}")
			        }
			    }
			}
			""")
	void gavStrictlyWithVersionProperty(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly(junit)
			        }
			    }
			}
			""")
	void gavStrictlyWithVersionVariable(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		// variables are treated like local properties
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			extra["junit"] = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("[5.0, 6.1[")
			            prefer("${extra["junit"]}")
			        }
			    }
			}
			""")
	void gavStrictlyPreferWithVersionProperty(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version {
			            strictly("[5.0, 6.1[")
			            prefer(junit)
			        }
			    }
			}
			""")
	void gavStrictlyPreferWithVersionVariable(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		// variables are treated like local properties
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom:$junit")
			}
			""")
	void gavStringInterpolationNoBraces(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		// variables are treated like local properties
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			val junit = "6.0.0"
			dependencies {
			    implementation("org.junit:junit-bom:${junit}")
			}
			""")
	void gavStringInterpolationWithBraces(PsiFile buildFile) {
		GradleFixtures.analyze(buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		// variables are treated like local properties
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNoNavigation();
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			junit=6.0.0
			""")
	@EditorFile(name = "build.gradle.kts", content = """
			val junit: String by project
			dependencies {
			    constraints {
			        implementation("org.junit:junit-bom:${junit}")
			    }
			}
			""")
	void dependencyConstraintWithProjectDelegate(
			PsiFile properties, @ProjectFile("build.gradle.kts") PsiFile buildFile) {
		GradleFixtures.analyze(properties, buildFile);
		assertThat(LineMarkers.of(buildFile)).hasSize(2);
		assertThat(LineMarkers.of(buildFile)).gutter(0).hasNoNavigation();
		assertThat(LineMarkers.of(buildFile)).gutter(1).hasNavigation();
	}

}
