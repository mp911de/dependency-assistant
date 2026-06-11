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
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.plugins.gradle.settings.GradleProjectSettings;
import org.jetbrains.plugins.gradle.settings.GradleSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests for {@link GradleUtils#findProjectRoot}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GradleProjectRootTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {

		// register the temp dir as content root so root detection sees a realistic
		// project shape
		VirtualFile tempDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(fixture.getTempDirPath());
		PsiTestUtil.addContentRoot(fixture.getModule(), tempDir);
	}

	@Test
	@ProjectFile(name = "settings.gradle", content = "rootProject.name = 'demo'")
	@ProjectFile(name = "build.gradle", content = "plugins {}")
	void findsSettingsDirectoryNextToBuildFile(@ProjectFile(name = "settings.gradle") PsiFile settings,
			@ProjectFile(name = "build.gradle") PsiFile buildFile) {

		assertThat(GradleUtils.findProjectRoot(buildFile)).isEqualTo(settings.getVirtualFile().getParent());
	}

	@Test
	@ProjectFile(name = "settings.gradle.kts", content = "rootProject.name = \"demo\"")
	@ProjectFile(name = "sub/build.gradle", content = "plugins {}")
	void findsSettingsDirectoryAboveModuleBuildFile(@ProjectFile(name = "settings.gradle.kts") PsiFile settings,
			@ProjectFile(name = "sub/build.gradle") PsiFile buildFile) {

		assertThat(GradleUtils.findProjectRoot(buildFile)).isEqualTo(settings.getVirtualFile().getParent());
	}

	@Test
	@ProjectFile(name = "sub/build.gradle", content = "plugins {}")
	void fallsBackToContainingDirectoryWithoutSettingsFile(PsiFile buildFile) {
		assertThat(GradleUtils.findProjectRoot(buildFile)).isEqualTo(buildFile.getVirtualFile().getParent());
	}

	@Test
	@ProjectFile(name = "settings.gradle", content = "rootProject.name = 'outer'")
	@ProjectFile(name = "linked/sub/build.gradle", content = "plugins {}")
	void ignoresSettingsFileAboveLinkedProjectRoot(@ProjectFile(name = "linked/sub/build.gradle") PsiFile buildFile) {

		VirtualFile linkedRoot = buildFile.getVirtualFile().getParent().getParent();
		GradleProjectSettings settings = new GradleProjectSettings();
		settings.setExternalProjectPath(linkedRoot.getPath());
		GradleSettings.getInstance(fixture.getProject()).linkProject(settings);

		assertThat(GradleUtils.findProjectRoot(buildFile)).isEqualTo(linkedRoot);
	}

}
