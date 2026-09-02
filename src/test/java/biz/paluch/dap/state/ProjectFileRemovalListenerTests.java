/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.state;

import java.io.IOException;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for {@link ProjectFileRemovalListener}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class ProjectFileRemovalListenerTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	@ProjectFile(name = "module/pom.xml", content = "<project/>")
	void deletingBuildFileEvictsProjectState(PsiFile pom) throws IOException {

		VirtualFile file = pom.getVirtualFile();
		ProjectId identity = ProjectId.of("com.example", "module", file.getPath());
		StateService service = StateService.getInstance(fixture.getProject());
		service.getProjectState(identity).setDependencies(new DependencyCollector(PackageSystem.MAVEN));

		WriteAction.runAndWait(() -> file.delete(this));

		assertThat(service.getProjectState(identity).hasDependencies()).isFalse();
		assertThat(service.getCache().getProjects()).noneMatch(it -> it.matches(identity));
	}

	@Test
	@ProjectFile(name = "module/pom.xml", content = "<project/>")
	void deletingDirectoryEvictsContainedProjectState(PsiFile pom) throws IOException {

		VirtualFile file = pom.getVirtualFile();
		ProjectId identity = ProjectId.of(file);
		StateService service = StateService.getInstance(fixture.getProject());
		service.getProjectState(identity).setDependencies(new DependencyCollector(PackageSystem.MAVEN));

		WriteAction.runAndWait(() -> file.getParent().delete(this));

		assertThat(service.getProjectState(identity).hasDependencies()).isFalse();
		assertThat(service.getCache().getProjects()).noneMatch(it -> it.matches(identity));
	}

	@Test
	@ProjectFile(name = "module/build.gradle", content = "")
	void renamingBuildFileEvictsProjectStateOfFormerPath(PsiFile buildFile) throws IOException {

		VirtualFile file = buildFile.getVirtualFile();
		ProjectId identity = ProjectId.of(file);
		StateService service = StateService.getInstance(fixture.getProject());
		service.getProjectState(identity).setDependencies(new DependencyCollector(PackageSystem.MAVEN));

		WriteAction.runAndWait(() -> file.rename(this, "build.gradle.kts"));

		assertThat(service.getProjectState(identity).hasDependencies()).isFalse();
		assertThat(service.getCache().getProjects()).noneMatch(it -> it.matches(identity));
	}

}
