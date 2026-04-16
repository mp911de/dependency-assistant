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
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests for {@link GroovyDslSettingsParser}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GroovyDslSettingsParserTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	void singleCatalogIsRegistered() {

		PsiFile file = fixture.configureByText("settings.gradle", """
				dependencyResolutionManagement {
				    versionCatalogs {
				        libs { from(files("gradle/libs.versions.toml")) }
				    }
				}
				""");

		VersionCatalogRegistry registry = GroovyDslSettingsParser.parseRegistry(file);

		assertThat(registry.containsAlias("libs")).isTrue();
		assertThat(registry.pathForAlias("libs")).isEqualTo("gradle/libs.versions.toml");
	}

	@Test
	void multipleCatalogsAreRegistered() {

		PsiFile file = fixture.configureByText("settings.gradle", """
				dependencyResolutionManagement {
				    versionCatalogs {
				        libs { from(files("gradle/libs.versions.toml")) }
				        tools { from(files("gradle/tools.versions.toml")) }
				    }
				}
				""");

		VersionCatalogRegistry registry = GroovyDslSettingsParser.parseRegistry(file);

		assertThat(registry.containsAlias("libs")).isTrue();
		assertThat(registry.pathForAlias("libs")).isEqualTo("gradle/libs.versions.toml");
		assertThat(registry.containsAlias("tools")).isTrue();
		assertThat(registry.pathForAlias("tools")).isEqualTo("gradle/tools.versions.toml");
	}

	@Test
	void multipleCatalogsCallSyntaxAreRegistered() {

		PsiFile file = fixture.configureByText("settings.gradle", """
				dependencyResolutionManagement {
				    versionCatalogs {
				        create("libs") { from(files("gradle/libs.versions.toml")) }
				        create("tools") { from(files("gradle/tools.versions.toml")) }
				    }
				}
				""");

		VersionCatalogRegistry registry = GroovyDslSettingsParser.parseRegistry(file);

		assertThat(registry.containsAlias("libs")).isTrue();
		assertThat(registry.pathForAlias("libs")).isEqualTo("gradle/libs.versions.toml");
		assertThat(registry.containsAlias("tools")).isTrue();
		assertThat(registry.pathForAlias("tools")).isEqualTo("gradle/tools.versions.toml");
	}

	@Test
	void defaultLibrariesExtensionNameIsRespected() {

		PsiFile file = fixture.configureByText("settings.gradle", """
				dependencyResolutionManagement {
				    versionCatalogs {
				        projectLibs { from(files("gradle/libs.versions.toml")) }
				    }
				    defaultLibrariesExtensionName = 'projectLibs'
				}
				""");

		VersionCatalogRegistry registry = GroovyDslSettingsParser.parseRegistry(file);

		assertThat(registry.defaultAlias()).isEqualTo("projectLibs");
		assertThat(registry.containsAlias("projectLibs")).isTrue();
	}

	@Test
	void noBlockFallsBackToDefaults() {

		PsiFile file = fixture.configureByText("settings.gradle", """
				rootProject.name = 'my-project'
				""");

		VersionCatalogRegistry registry = GroovyDslSettingsParser.parseRegistry(file);

		assertThat(registry).isEqualTo(VersionCatalogRegistry.defaults());
	}

	@Test
	void emptyVersionCatalogsFallsBackToDefaults() {

		PsiFile file = fixture.configureByText("settings.gradle", """
				dependencyResolutionManagement {
				    versionCatalogs {
				    }
				}
				""");

		VersionCatalogRegistry registry = GroovyDslSettingsParser.parseRegistry(file);

		assertThat(registry).isEqualTo(VersionCatalogRegistry.defaults());
	}

}
