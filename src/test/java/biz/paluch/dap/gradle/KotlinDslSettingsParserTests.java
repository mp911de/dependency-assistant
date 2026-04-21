/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
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
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests for {@link KotlinDslSettingsParser}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class KotlinDslSettingsParserTests {

	@Test
	@EditorFile(name = "settings.gradle.kts", content = """
			dependencyResolutionManagement {
			    versionCatalogs {
			        create("libs") { from(files("gradle/libs.versions.toml")) }
			    }
			}
			""")
	void singleCatalogIsRegistered(PsiFile buildFile) {

		VersionCatalogRegistry registry = KotlinDslSettingsParser.parseRegistry(buildFile);

		assertThat(registry.containsAlias("libs")).isTrue();
		assertThat(registry.pathForAlias("libs")).isEqualTo("gradle/libs.versions.toml");
	}

	@Test
	@EditorFile(name = "settings.gradle.kts", content = """
			dependencyResolutionManagement {
			    versionCatalogs {
			        create("libs") { from(files("gradle/libs.versions.toml")) }
			        create("tools") { from(files("gradle/tools.versions.toml")) }
			    }
			}
			""")
	void multipleCatalogsAreRegistered(PsiFile buildFile) {

		VersionCatalogRegistry registry = KotlinDslSettingsParser.parseRegistry(buildFile);

		assertThat(registry.containsAlias("libs")).isTrue();
		assertThat(registry.pathForAlias("libs")).isEqualTo("gradle/libs.versions.toml");
		assertThat(registry.containsAlias("tools")).isTrue();
		assertThat(registry.pathForAlias("tools")).isEqualTo("gradle/tools.versions.toml");
	}

	@Test
	@EditorFile(name = "settings.gradle.kts", content = """
			dependencyResolutionManagement {
			    versionCatalogs {
			        create("projectLibs") { from(files("gradle/libs.versions.toml")) }
			    }
			    defaultLibrariesExtensionName = "projectLibs"
			}
			""")
	void defaultLibrariesExtensionNameIsRespected(PsiFile buildFile) {

		VersionCatalogRegistry registry = KotlinDslSettingsParser.parseRegistry(buildFile);

		assertThat(registry.defaultAlias()).isEqualTo("projectLibs");
		assertThat(registry.containsAlias("projectLibs")).isTrue();
	}

	@Test
	@EditorFile(name = "settings.gradle.kts", content = """
			rootProject.name = "my-project"
			""")
	void noBlockFallsBackToDefaults(PsiFile buildFile) {

		VersionCatalogRegistry registry = KotlinDslSettingsParser.parseRegistry(buildFile);

		assertThat(registry).isEqualTo(VersionCatalogRegistry.defaults());
	}

	@Test
	@EditorFile(name = "settings.gradle.kts", content = """
			dependencyResolutionManagement {
			    versionCatalogs {
			    }
			}
			""")
	void emptyVersionCatalogsFallsBackToDefaults(PsiFile buildFile) {

		VersionCatalogRegistry registry = KotlinDslSettingsParser.parseRegistry(buildFile);

		assertThat(registry).isEqualTo(VersionCatalogRegistry.defaults());
	}

}
