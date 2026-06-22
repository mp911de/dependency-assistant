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

package biz.paluch.dap.gradle.wrapper;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.BuildActionDelegate;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for {@link UpdateGradleWrapperProperties}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateGradleWrapperPropertiesTests {

	private ArtifactId GRADLE = ArtifactId.of("org.gradle", "gradle");

	@BeforeEach
	void setUp(Project project) {
		GradleWrapperFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void updatesDistributionUrl(PsiFile file) {

		applyUpdate(file, "9.5.1");

		assertThat(file)
				.containsText("gradle-9.5.1-bin.zip")
				.containsText("distributionUrl=https\\://services.gradle.org/distributions/")
				.doesNotContainText("distributionSha256Sum");
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			distributionSha256Sum=old
			""")
	void updatesDistributionUrlAndSha(PsiFile file) {

		applyUpdate(file, "9.5.1");

		assertThat(file)
				.containsText("gradle-9.5.1-bin.zip")
				.containsText("distributionSha256Sum=sha-9.5.1")
				.doesNotContainText("distributionSha256Sum=old");
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://mirror.example.com/custom/gradle-8.14.3-all.zip
			distributionSha256Sum=old
			""")
	void preservesAllFlavorAndCustomPrefix(PsiFile file) {

		applyUpdate(file, "9.5.1");

		assertThat(file)
				.containsText("https\\://mirror.example.com/custom/gradle-9.5.1-all.zip")
				.containsText("distributionSha256Sum=sha-9.5.1");
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			distributionSha256Sum=old
			""")
	void commentsShaWhenNewVersionShaIsAbsent(PsiFile file) {

		applyUpdate(file, "9.6.0-rc-1");

		assertThat(file)
				.containsText("gradle-9.6.0-rc-1-bin.zip")
				.containsText("# distributionSha256Sum=old");
	}

	private void applyUpdate(PsiFile targetFile, String toVersion) {
		new BuildActionDelegate(targetFile.getProject(),
				UpdateGradleWrapperProperties::applyUpdates)
						.updateBuildFile(targetFile.getVirtualFile(), List.of(update(toVersion)));
	}

	private DependencyUpdate update(String toVersion) {
		return DependencyUpdate.create(GRADLE, ArtifactVersion.of(toVersion));
	}

}
