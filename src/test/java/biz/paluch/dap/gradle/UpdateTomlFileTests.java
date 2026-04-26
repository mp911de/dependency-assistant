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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.support.BuildActionDelegate;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link UpdateGradleFile}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UpdateTomlFileTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			spring-boot = "3.5.0"
			commons-lang = "3.17.0"

			[libraries]
			spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version.ref = "spring-boot" }
			""")
	void propertyInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		UpdatedBuildFile updated = applyUpdate(tomlFile, "org.springframework.boot", "spring-boot-starter", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.property("spring-boot"), "3.6.0");

		assertThat(updated).hasProperty("spring-boot", "3.6.0").hasProperty("commons-lang",
				"3.17.0");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			commons-lang = "3.17.0"

			[libraries]
			spring-boot-starter = { module = "org.springframework.boot:spring-boot-starter", version = "3.5.0" }
			""")
	void libraryVersionInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		UpdatedBuildFile updated = applyUpdate(tomlFile, "org.springframework.boot", "spring-boot-starter", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.declared("3.5.0"), "3.6.0");

		assertThat(updated).hasDependency("spring-boot-starter", "3.6.0");
		assertThat(updated).hasProperty("commons-lang", "3.17.0");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """

			[libraries]
			spring-boot-starter = "org.springframework.boot:spring-boot-starter:3.5.0"
			""")
	void libraryLiteralVersionInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		UpdatedBuildFile updated = applyUpdate(tomlFile, "org.springframework.boot", "spring-boot-starter", "3.5.0",
				DeclarationSource.dependency(),
				VersionSource.declared("3.5.0"), "3.6.0");

		assertThat(updated).hasDependency("spring-boot-starter", "3.6.0");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """

			[plugins]
			spring-boot = "org.springframework.boot:4.0.0"
			""")
	void pluginLiteralVersionInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		UpdatedBuildFile updated = applyUpdate(tomlFile, "org.springframework.boot", "org.springframework.boot",
				"3.5.0",
				DeclarationSource.plugin(),
				VersionSource.declared("3.5.0"), "4.0.0");

		assertThat(updated).hasDependency("org.springframework.boot", "4.0.0");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			commons-lang = "3.17.0"

			[plugins]
			spring-dependency-management = { id = "io.spring.dependency-management", version = "1.1.6" }
			""")
	void pluginVersionInTomlVersionCatalogIsUpdated(PsiFile tomlFile) {

		UpdatedBuildFile updated = applyUpdate(tomlFile, "io.spring.dependency-management",
				"io.spring.dependency-management", "1.1.6",
				DeclarationSource.plugin(), VersionSource.declared("1.1.6"), "1.1.7");

		assertThat(updated).hasDependency("io.spring.dependency-management", "1.1.7");
		assertThat(updated).hasProperty("commons-lang", "3.17.0");
	}

	private UpdatedBuildFile applyUpdate(PsiFile targetFile, String groupId, String artifactId, String fromVersion,
			DeclarationSource declarationSource, VersionSource versionSource, String toVersion) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);
		ArtifactVersion current = ArtifactVersion.of(fromVersion);
		ArtifactVersion updateTo = ArtifactVersion.of(toVersion);

		Dependency dep = new Dependency(id, current);
		dep.addDeclarationSource(declarationSource);
		dep.addVersionSource(versionSource);

		DependencyUpdate update = new DependencyUpdate(id, updateTo, dep.getDeclarationSources(),
				dep.getVersionSources());

		new BuildActionDelegate(fixture.getProject(),
				(file, updates) -> new UpdateGradleFile(fixture.getProject()).applyUpdates(targetFile, updates),
				targetFile.getVirtualFile()).updateBuildFile(List.of(update));
		return UpdatedBuildFile.of(targetFile);
	}

}
