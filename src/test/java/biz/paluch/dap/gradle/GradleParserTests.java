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

import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link GradleParser} and
 * {@link DependencyCollector} (property maps, ext parsing, injected project
 * properties).
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GradleParserTests {

	@BeforeEach
	void setUp(Project project) {
		GradleFixtures.setup(project);
	}

	// -------------------------------------------------------------------------
	// GAV style
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id 'org.springframework.boot' version '4.0.3'
			}
			""")
	void pluginsWithVersionsAreDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.springframework.boot").hasVersion("4.0.3");
	}

	// -------------------------------------------------------------------------
	// Plugins (plugin ID resolution)
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "${myPlugin}" version "1.0"
			}
			""")
	void pluginIdFromGradlePropertyIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("myPlugin", "org.foo"));

		assertThat(collector)
				.hasDependencyUsage("org.foo", "org.foo")
				.hasVersion("1.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext { myPlugin = 'org.foo' }

			plugins {
			    id "${myPlugin}" version "1.0"
			}
			""")
	void pluginIdFromExtPropertyIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.foo", "org.foo").hasVersion("1.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "$myPlugin" version "1.0"
			}
			""")
	void pluginIdUnbracedGStringIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("myPlugin", "org.foo"));

		assertThat(collector).hasDependencyUsage("org.foo", "org.foo");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "com.example.${suffix}" version "1.0"
			}
			""")
	void pluginIdMixedStringIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("suffix", "bar"));

		assertThat(collector).hasDependencyUsage("com.example.bar",
				"com.example.bar");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "${a}" version "1.0"
			}
			""")
	void pluginIdChainedPropertyIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("a", "${b}", "b", "org.foo"));

		assertThat(collector).hasDependencyUsage("org.foo",
				"org.foo");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "${a}" version "1.0"
			}
			""")
	void pluginIdChainLongerThanTwoHops(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile,
				Map.of("a", "${b}", "b", "${c}", "c", "org.foo"));

		assertThat(collector)
				.hasDependencyUsage("org.foo", "org.foo");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "${a}" version "1.0"
			}
			""")
	void pluginIdCircularPropertySkipped(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("a", "${b}", "b", "${a}"));

		assertThat(collector).hasNoDependencyUsage("org.foo",
				"org.foo");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "${p1}" version "1.0"
			}
			""")
	void pluginIdDepthCapSkipped(PsiFile buildFile) {

		Map<String, String> props = new LinkedHashMap<>();
		props.put("p12", "org.foo");
		for (int i = 11; i >= 1; i--) {
			props.put("p" + i, "${p" + (i + 1) + "}");
		}

		DependencyCollector collector = GradleFixtures.analyze(buildFile, props);

		assertThat(collector).hasNoDependencyUsage("org.foo", "org.foo");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "${missing}" version "1.0"
			}
			""")
	void pluginIdUnresolvablePropertySkipped(PsiFile buildFile) {

		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "${myPlugin}" version "1.0"
			}
			""")
	void pluginIdEmptyValueSkipped(PsiFile buildFile) {

		assertThat(GradleFixtures.analyze(buildFile, Map.of("myPlugin", ""))).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "${myPlugin}" version "1.0"
			}
			""")
	void pluginIdInvalidFormatSkipped(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile, Map.of("myPlugin", "../evil"))).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id 'org.foo' version '1.0'
			}
			""")
	void pluginIdLiteralUnchanged(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.foo", "org.foo")
				.hasVersion("1.0")
				.hasNoPropertyVersion();
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id 'org.foo' version '${fooVersion}'
			}
			""")
	void pluginVersionPropertyResolvedInParsePlugin(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("fooVersion", "2.0"));

		assertThat(collector)
				.hasDependencyUsage("org.foo", "org.foo")
				.hasVersion("2.0")
				.hasPropertyVersion("fooVersion");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id "${pluginId}" version "${pluginVer}"
			}
			""")
	void pluginIdAndVersionBothFromProperties(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile,
				Map.of("pluginId", "org.foo", "pluginVer", "3.0"));

		assertThat(collector)
				.hasDependencyUsage("org.foo", "org.foo")
				.hasVersion("3.0")
				.hasPropertyVersion("pluginVer");
	}

	// -------------------------------------------------------------------------
	// Settings
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "settings.gradle", content = """
			pluginManagement {
			    plugins {
			        id "${myPlugin}" version "1.0"
			    }
			}
			""")
	void settingsGroovyPluginManagementIdResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("myPlugin", "org.foo"));

		assertThat(collector).hasDependencyUsage("org.foo",
				"org.foo");
	}

	// -------------------------------------------------------------------------
	// GAV style
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.0'
			}
			""")
	void gavInlineVersion(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: '6.0.0'
			}
			""")
	void directDependencyInMapNotationIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.apache.groovy:groovy'
			    implementation 'org.springframework.modulith:spring-modulith-starter-core'
			}
			""")
	void dependenciesWithoutVersionAreNotCollected(PsiFile buildFile) {

		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Constraints
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.0!!'
			}
			""")
	void gavEnforcedVersion(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[5.2.0, 6.0.0]'
			}
			""")
	void gavRange1(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[5.2.0,6.0.0]'
			}
			""")
	void gavRange2(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[5.0, 7.0[!!6.0.0'
			}
			""")
	void gavRangeStrictly(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:[6.0.0,)'
			}
			""")
	void gavOpenRange(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:(5.2,6.0.0]'
			}
			""")
	void gavClosedRange(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:0.10.+'
			}
			""")
	void gavPrefixVersion(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:latest.release'
			}
			""")
	void gavLatestReleaseVersion(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Version block
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '[5.0, 6.1['
			            prefer '6.0.0'
			        }
			    }
			}
			""")
	void versionBlockWithPreferAndStrictlyIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly ("[5.0, 6.1[")
			            prefer ("6.0.0")
			        }
			    }
			}
			""")
	void gavStrictlyFunctionPreferIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.slf4j:slf4j-api') {
			        version {
			            prefer '1.7.25'
			        }
			    }
			}
			""")
	void versionBlockWithPreferOnlyIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.slf4j", "slf4j-api").hasVersion("1.7.25");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.slf4j:slf4j-api') {
			        version {
			            strictly '[1,2['
			        }
			    }
			}
			""")
	void versionBlockWithRangeOnlyIsSkipped(PsiFile buildFile) {

		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '6.0.0'
			        }
			    }
			}
			""")
	void versionBlockWithExactStrictlyNoPreferIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	// -------------------------------------------------------------------------
	// Properties
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    set('junit', "6.0.0")
			}
			dependencyManagement {
			    imports {
			        mavenBom "org.junit:junit-bom:${junit}"
			    }
			}
			""")
	void managedBomWithPropertyExpressionIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasDeclaration(DeclarationSource.managed())
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencyManagement {
			    imports {
			        mavenBom "org.junit:junit-bom:${junit}"
			    }
			}
			""")
	void managedBomWithInjectedGradlePropertyIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile, Map.of("junit", "6.0.0"));

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasDeclaration(DeclarationSource.managed())
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    junit='6.0.0'
			}
			dependencyManagement {
			    imports {
			        mavenBom "org.junit:junit-bom:${junit}"
			    }
			}
			""")
	void extPropertyAssignmentManagedBomIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasDeclaration(DeclarationSource.managed());
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext.junit = '6.0.0'

			dependencyManagement {
			    imports {
			        mavenBom "org.junit:junit-bom:${junit}"
			    }
			}
			""")
	void extPropertyDotAssignmentManagedBomIsResolved(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasDeclaration(DeclarationSource.managed());
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    commonsVersion = '3.19.0'
			}

			dependencies {
			    implementation "org.apache.commons:commons-lang3:${commonsVersion}"
			}
			""")
	void dependencyVersionResolvedViaExtProperty(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.apache.commons", "commons-lang3")
				.hasVersion("3.19.0")
				.hasPropertyVersion("commonsVersion");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    guavaVersion = '33.0.0-jre'
			}

			dependencies {
			    implementation group: 'com.google.guava', name: 'guava', version: guavaVersion
			}
			""")
	void mapNotationPropertyVersionIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("com.google.guava", "guava")
				.hasVersion("33.0.0-jre")
				.hasPropertyVersion("guavaVersion");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext.junit = '6.0.0'
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: "${junit}"
			}
			""")
	void mapDependencyWithGStringVersionIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    junit = '6.0.0'
			}
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junit
			}
			""")
	void mapNotationWithVersionVariableIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junit");
	}

	// -------------------------------------------------------------------------
	// Constraints + Properties
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext.junit = '6.0.0'
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly "${junit}"
			        }
			    }
			}
			""")
	void gavStrictlyWithVersionPropertyIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0");
	}


	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    junit = '6.0.0'
			}
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly "${junit}"
			        }
			    }
			}
			""")
	void gavStrictlyWithVersionVariableIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
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
	void gavStrictlyPreferWithVersionPropertyIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    junit = '6.0.0'
			}
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '[5.0, 6.1['
			            prefer "${junit}"
			        }
			    }
			}
			""")
	void gavStrictlyPreferWithVersionVariableIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
	}

	// -------------------------------------------------------------------------
	// Map notation (edge cases)
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.apache.groovy', name: 'groovy'
			}
			""")
	void mapNotationAbsentVersionProducesNoDependency(PsiFile buildFile) {

		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation name: 'guava', group: 'com.google.guava', version: '33.0.0-jre'
			}
			""")
	void mapNotationNonCanonicalKeyOrderIsDiscovered(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("com.google.guava", "guava").hasVersion("33.0.0-jre");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'com.google.guava', name: 'guava', version: '33.0.0-jre', classifier: 'android'
			}
			""")
	void mapNotationExtraKeyIsIgnored(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("com.google.guava", "guava").hasVersion("33.0.0-jre");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation name: 'guava', version: '33.0.0-jre'
			}
			""")
	void mapNotationMissingGroupIsSkipped(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'com.google.guava', version: '33.0.0-jre'
			}
			""")
	void mapNotationMissingNameIsSkipped(PsiFile buildFile) {
		assertThat(GradleFixtures.analyze(buildFile)).isEmpty();
	}

	// -------------------------------------------------------------------------
	// Full build file
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id 'groovy'
			    id 'org.springframework.boot' version '4.0.3'
			    id 'io.spring.dependency-management' version '1.1.7'
			}

			group = 'com.example'
			version = '0.0.1-SNAPSHOT'

			ext {
			    set('springModulithVersion', "2.0.4")
			}

			repositories {
			    mavenCentral()
			}

			dependencies {
			    implementation 'org.apache.groovy:groovy'
			    implementation 'org.apache.commons:commons-lang3:3.19.0'
			    implementation 'org.springframework.modulith:spring-modulith-starter-core'
			    testImplementation 'org.springframework.boot:spring-boot-starter-test'
			    testRuntimeOnly 'org.junit.platform:junit-platform-launcher'
			}

			dependencyManagement {
			    imports {
			        mavenBom "org.springframework.modulith:spring-modulith-bom:${springModulithVersion}"
			    }
			}
			""")
	void fullGroovyBuildFileDiscovery(PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.springframework.boot", "org.springframework.boot");
		assertThat(collector).hasDependencyUsage("io.spring.dependency-management", "io.spring.dependency-management");
		assertThat(collector).hasDependencyUsage("org.apache.commons", "commons-lang3");
		assertThat(collector)
				.hasDependencyUsage("org.springframework.modulith", "spring-modulith-bom")
				.hasVersion("2.0.4")
				.hasPropertyVersion("springModulithVersion");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[libraries]
			guava = "com.google.guava:guava:32.1.3-jre"
			""")
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:6.0.0")
			}
			""")
	void kotlinBuildScriptDiscoversCatalogFromGradleDir(@ProjectFile("build.gradle.kts") PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
		assertThat(collector).hasDependencyUsage("com.google.guava", "guava").hasVersion("32.1.3-jre");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[libraries]
			guava = "com.google.guava:guava:32.1.3-jre"
			""")
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.0'
			}
			""")
	void groovyBuildScriptDiscoversCatalogFromGradleDir(@ProjectFile("build.gradle") PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector).hasDependencyUsage("org.junit", "junit-bom").hasVersion("6.0.0");
		assertThat(collector).hasDependencyUsage("com.google.guava", "guava").hasVersion("32.1.3-jre");
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			junitVersion=6.0.0
			""")
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:${property("junitVersion")}")
			}
			""")
	void buildScriptDiscoversGradlePropertiesFromProjectRoot(@ProjectFile("build.gradle.kts") PsiFile buildFile) {

		DependencyCollector collector = GradleFixtures.analyze(buildFile);

		assertThat(collector)
				.hasDependencyUsage("org.junit", "junit-bom")
				.hasVersion("6.0.0")
				.hasPropertyVersion("junitVersion");
	}

}
