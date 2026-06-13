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

package biz.paluch.dap.npm;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Tests for {@link NpmProjectContext#getProjectVersion()}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class NpmProjectContextTests {

	@BeforeEach
	void setUp(Project project) {
		NpmFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "version": "1.2.3",
			  "dependencies": {}
			}
			""")
	void readsProjectVersionFromPackageJson(PsiFile packageJson) {
		assertThat(context(packageJson).getProjectVersion()).isEqualTo(Versioned.of(ArtifactVersion.of("1.2.3")));
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "version": "1.2.3-alpha.1",
			  "dependencies": {}
			}
			""")
	void readsPrereleaseProjectVersionFromPackageJson(PsiFile packageJson) {
		assertThat(context(packageJson).getProjectVersion())
				.isEqualTo(Versioned.of(ArtifactVersion.of("1.2.3-alpha.1")));
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {}
			}
			""")
	void returnsUnversionedWhenNoVersionDeclared(PsiFile packageJson) {
		assertThat(context(packageJson).getProjectVersion()).isEqualTo(Versioned.unversioned());
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "version": 1,
			  "dependencies": {}
			}
			""")
	void returnsUnversionedWhenVersionIsNotString(PsiFile packageJson) {
		assertThat(context(packageJson).getProjectVersion()).isEqualTo(Versioned.unversioned());
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "version": "   ",
			  "dependencies": {}
			}
			""")
	void returnsUnversionedWhenVersionIsBlank(PsiFile packageJson) {
		assertThat(context(packageJson).getProjectVersion()).isEqualTo(Versioned.unversioned());
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "version": "not a version",
			  "dependencies": {}
			}
			""")
	void returnsUnversionedWhenVersionCannotBeParsed(PsiFile packageJson) {
		assertThat(context(packageJson).getProjectVersion()).isEqualTo(Versioned.unversioned());
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "version": "2.1.0",
			  "dependencies": {
			    "axios": "1.6.8"
			  }
			}
			""")
	void assistantContextUsesPackageJsonProjectVersion(Project project, PsiFile packageJson) {

		ProjectDependencyContext context = new NpmAssistant().createContext(project, packageJson);

		assertThat(context.getProjectVersion()).isEqualTo(Versioned.of(ArtifactVersion.of("2.1.0")));
	}

	private NpmProjectContext context(PsiFile packageJson) {
		return NpmProjectContext.of(packageJson);
	}

}
