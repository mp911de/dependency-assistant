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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.support.BuildActionDelegate;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Platform-harness tests for {@link UpdatePackageJsonFile}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UpdatePackageJsonFileTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "1.6.8"
			  }
			}
			""")
	void updatesExactVersion(PsiFile packageJson) {

		applyUpdate(packageJson, ArtifactId.of("axios", "axios"), ArtifactVersion.of("1.7.0"));

		assertThat(packageJson.getText()).contains("\"axios\": \"1.7.0\"");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "^3.1.2"
			  }
			}
			""")
	void preservesCaretModifier(PsiFile packageJson) {

		applyUpdate(packageJson, ArtifactId.of("axios", "axios"), ArtifactVersion.of("3.4.0"));

		assertThat(packageJson.getText()).contains("\"axios\": \"^3.4.0\"");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "1.0.0 - 2.9999.9999"
			  }
			}
			""")
	void updatesHyphenRangeUpperBound(PsiFile packageJson) {

		applyUpdate(packageJson, ArtifactId.of("axios", "axios"), ArtifactVersion.of("3.0.0"));

		assertThat(packageJson.getText()).contains("\"axios\": \"1.0.0 - 3.0.0\"");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": ">=1.0.2 <2.1.2"
			  }
			}
			""")
	void updatesComparatorPairUpperBound(PsiFile packageJson) {

		applyUpdate(packageJson, ArtifactId.of("axios", "axios"), ArtifactVersion.of("2.4.0"));

		assertThat(packageJson.getText()).contains("\"axios\": \">=1.0.2 <2.4.0\"");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "react": "2.x"
			  }
			}
			""")
	void doesNotRewritePrefixRange(PsiFile packageJson) {

		applyUpdate(packageJson, ArtifactId.of("react", "react"), ArtifactVersion.of("3.0.0"));

		assertThat(packageJson.getText()).contains("\"react\": \"2.x\"");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "alias": "npm:@ankurk91/bootstrap-vue@^3.0.2"
			  }
			}
			""")
	void updatesAliasInner(PsiFile packageJson) {

		applyUpdate(packageJson, ArtifactId.of("alias", "alias"), ArtifactVersion.of("3.1.0"));

		assertThat(packageJson.getText()).contains("\"alias\": \"npm:@ankurk91/bootstrap-vue@^3.1.0\"");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "cli": "git+ssh://git@github.com:npm/cli.git#v1.0.27"
			  }
			}
			""")
	void updatesGitTagCommittish(PsiFile packageJson) {

		GitVersion target = GitVersion.of("d1185ce59f7757407fe6a5febb1e03e3dba2a530", ArtifactVersion.of("v1.0.28"));
		applyUpdate(packageJson, ArtifactId.of("cli", "cli"), target);

		assertThat(packageJson.getText()).contains("#v1.0.28");
		assertThat(packageJson.getText()).doesNotContain("#v1.0.27");
		// JSON does not permit comments; the rendered string must not append " #"
		// metadata.
		assertThat(packageJson.getText()).doesNotContain("# v1.0");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "cli": "git+ssh://git@github.com:npm/cli.git#abcdef0"
			  }
			}
			""")
	void updatesGitShaCommittishToTruncatedSha(PsiFile packageJson) {

		GitVersion target = GitVersion.of("d1185ce59f7757407fe6a5febb1e03e3dba2a530", ArtifactVersion.of("v1.0.28"));
		applyUpdate(packageJson, ArtifactId.of("cli", "cli"), target);

		assertThat(packageJson.getText()).contains("#d1185ce");
		assertThat(packageJson.getText()).doesNotContain("#abcdef0");
		assertThat(packageJson.getText()).doesNotContain("# ");
	}

	private void applyUpdate(PsiFile file, ArtifactId id, ArtifactVersion targetVersion) {

		DependencyUpdate update = new DependencyUpdate(id, targetVersion,
				List.of(DeclarationSource.dependency()),
				List.of(VersionSource.declared(targetVersion.toString())));

		UpdatePackageJsonFile updater = new UpdatePackageJsonFile(file.getProject());
		new BuildActionDelegate(file.getProject(), updater::applyUpdates, file.getVirtualFile())
				.updateBuildFile(List.of(update));
	}

}
