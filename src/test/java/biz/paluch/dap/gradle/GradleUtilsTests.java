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

import com.intellij.mock.MockVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GradleUtils}.
 *
 * @author Mark Paluch
 */
class GradleUtilsTests {

	@Test
	void recognizesCustomNamedGroovyBuildScript() {

		MockVirtualFile file = MockVirtualFile.file("spring-security-config.gradle");

		assertThat(GradleUtils.isGradleScript(file)).isTrue();
		assertThat(GradleUtils.isGradleFile(file)).isTrue();
		assertThat(GradleUtils.isGroovyDsl(file)).isTrue();
		assertThat(GradleUtils.isKotlinDsl(file)).isFalse();
	}

	@Test
	void recognizesCustomNamedKotlinBuildScript() {

		MockVirtualFile file = MockVirtualFile.file("spring-security-config.gradle.kts");

		assertThat(GradleUtils.isGradleScript(file)).isTrue();
		assertThat(GradleUtils.isKotlinDsl(file)).isTrue();
		assertThat(GradleUtils.isGroovyDsl(file)).isFalse();
	}

	@Test
	void recognizesGroovySettingsScript() {

		MockVirtualFile file = MockVirtualFile.file("settings.gradle");

		assertThat(GradleUtils.isGradleScript(file)).isTrue();
		assertThat(GradleUtils.isGroovyDsl(file)).isTrue();
	}

	@Test
	void recognizesKotlinSettingsScript() {

		MockVirtualFile file = MockVirtualFile.file("settings.gradle.kts");

		assertThat(GradleUtils.isGradleScript(file)).isTrue();
		assertThat(GradleUtils.isKotlinDsl(file)).isTrue();
	}

	@Test
	void rejectsGradleCacheDirectory() {

		MockVirtualFile directory = MockVirtualFile.dir(".gradle");

		assertThat(GradleUtils.isGradleScript(directory)).isFalse();
	}

	@Test
	void rejectsScriptSuffixWithoutBaseName() {

		assertThat(GradleUtils.isGradleScript(MockVirtualFile.file(".gradle"))).isFalse();
		assertThat(GradleUtils.isGradleScript(MockVirtualFile.file(".gradle.kts"))).isFalse();
	}

	@Test
	void gradlePropertiesIsGradleFileButNotScript() {

		MockVirtualFile file = MockVirtualFile.file("gradle.properties");

		assertThat(GradleUtils.isGradleScript(file)).isFalse();
		assertThat(GradleUtils.isGradleFile(file)).isTrue();
	}

	@Test
	void versionCatalogIsGradleFileButNotScript() {

		MockVirtualFile file = MockVirtualFile.file("libs.versions.toml");

		assertThat(GradleUtils.isGradleScript(file)).isFalse();
		assertThat(GradleUtils.isGradleFile(file)).isTrue();
	}

	@Test
	void findsGradleScriptsSortedByName() {

		MockVirtualFile directory = MockVirtualFile.dir("project",
				MockVirtualFile.file("spring-security-config.gradle"),
				MockVirtualFile.file("build.gradle"),
				MockVirtualFile.file("gradle.properties"),
				MockVirtualFile.dir(".gradle"));

		assertThat(GradleUtils.findGradleScripts(directory)).extracting(VirtualFile::getName)
				.containsExactly("build.gradle", "spring-security-config.gradle");
	}

}
