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

package biz.paluch.dap.assistant.action;

import java.util.List;

import biz.paluch.dap.assistant.check.UpgradeScope;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.gradle.GradleFixtures;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link UpgradeScopeResolver}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpgradeScopeResolverTests {

	static final String BUILD_GRADLE = """
			dependencies {
			    implementation 'org.apache.commons:commons-lang3:3.19.0'
			}
			""";

	@Test
	@ProjectFile(name = "selection/build.gradle", content = BUILD_GRADLE)
	void selectionResolvesToSelectedBuildFile(Project project,
			@ProjectFile("selection/build.gradle") PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		UpgradeScope scope = UpgradeScopeResolver.resolve(project,
				new UpgradeRequest(List.of(buildFile.getVirtualFile()), null));

		assertThat(scope.entries()).singleElement()
				.extracting(UpgradeScope.Entry::buildFile).isEqualTo(buildFile);
	}

	@Test
	@ProjectFile(name = "selwins/selected/build.gradle", content = BUILD_GRADLE)
	@ProjectFile(name = "selwins/editor/build.gradle", content = BUILD_GRADLE)
	void selectionWinsOverEditor(Project project, @ProjectFile("selwins/selected/build.gradle") PsiFile selected,
			@ProjectFile("selwins/editor/build.gradle") PsiFile editor) {

		GradleFixtures.analyze(selected);
		GradleFixtures.analyze(editor);

		UpgradeScope scope = UpgradeScopeResolver.resolve(project,
				new UpgradeRequest(List.of(selected.getVirtualFile()), editor));

		assertThat(scope.entries()).singleElement()
				.extracting(UpgradeScope.Entry::buildFile).isEqualTo(selected);
	}

	@Test
	@ProjectFile(name = "editor/build.gradle", content = BUILD_GRADLE)
	void editorUsedWhenNothingSelected(Project project, @ProjectFile("editor/build.gradle") PsiFile editor) {

		GradleFixtures.analyze(editor);

		UpgradeScope scope = UpgradeScopeResolver.resolve(project, new UpgradeRequest(List.of(), editor));

		assertThat(scope.entries()).singleElement()
				.extracting(UpgradeScope.Entry::buildFile).isEqualTo(editor);
	}

	@Test
	@ProjectFile(name = "projectwide/build.gradle", content = BUILD_GRADLE)
	void projectWideWhenNothingSelectedAndNoEditor(Project project) {

		UpgradeScope scope = UpgradeScopeResolver.resolve(project, new UpgradeRequest(List.of(), null));

		assertThat(scope.reason()).isEqualTo(UpgradeScope.Reason.SUCCESS);
	}

	@Test
	@ProjectFile(name = "folder/build.gradle", content = BUILD_GRADLE)
	void folderInSelectionContributesNothing(Project project, @ProjectFile("folder/build.gradle") PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);
		VirtualFile folder = buildFile.getVirtualFile().getParent();

		UpgradeScope scope = UpgradeScopeResolver.resolve(project,
				new UpgradeRequest(List.of(folder, buildFile.getVirtualFile()), null));

		assertThat(scope.entries()).singleElement()
				.extracting(UpgradeScope.Entry::buildFile).isEqualTo(buildFile);
	}

	@Test
	@ProjectFile(name = "onlyfolder/build.gradle", content = BUILD_GRADLE)
	void folderOnlySelectionIsNotFound(Project project, @ProjectFile("onlyfolder/build.gradle") PsiFile buildFile) {

		VirtualFile folder = buildFile.getVirtualFile().getParent();

		UpgradeScope scope = UpgradeScopeResolver.resolve(project, new UpgradeRequest(List.of(folder), null));

		assertThat(scope).isEqualTo(UpgradeScope.notFound(UpgradeScope.Reason.NO_BUILD_FILES));
	}

	@Test
	@ProjectFile(name = "nobuild/data.xml", content = "<data/>")
	void selectionWithoutBuildFilesIsNoBuildFiles(Project project,
			@ProjectFile("nobuild/data.xml") PsiFile notBuildFile) {

		UpgradeScope scope = UpgradeScopeResolver.resolve(project,
				new UpgradeRequest(List.of(notBuildFile.getVirtualFile()), null));

		assertThat(scope).isEqualTo(UpgradeScope.notFound(UpgradeScope.Reason.NO_BUILD_FILES));
	}

	@Test
	@ProjectFile(name = "notimported/build.gradle", content = BUILD_GRADLE)
	void supportedButUnavailableBuildFileIsNotImported(Project project,
			@ProjectFile("notimported/build.gradle") PsiFile buildFile) {

		UpgradeScope scope = UpgradeScopeResolver.resolve(project,
				new UpgradeRequest(List.of(buildFile.getVirtualFile()), null));

		assertThat(scope).isEqualTo(UpgradeScope.notFound(UpgradeScope.Reason.NOT_IMPORTED));
	}

}
