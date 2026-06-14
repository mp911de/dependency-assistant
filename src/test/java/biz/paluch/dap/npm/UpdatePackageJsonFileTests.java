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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.fixtures.BuildFileUpdates;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Platform-harness tests for {@link UpdatePackageJsonFile}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdatePackageJsonFileTests {

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "1.6.8"
			  }
			}
			""")
	void updatesExactVersion(PsiFile packageJson) {

		applyUpdate(packageJson, "axios", "1.7.0");

		assertThat(packageJson).containsText("\"axios\": \"1.7.0\"");
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

		applyUpdate(packageJson, "axios", "3.4.0");

		assertThat(packageJson).containsText("\"axios\": \"^3.4.0\"");
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

		applyUpdate(packageJson, "axios", "3.0.0");

		assertThat(packageJson).containsText("\"axios\": \"1.0.0 - 3.0.0\"");
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

		applyUpdate(packageJson, "axios", "2.4.0");

		assertThat(packageJson).containsText("\"axios\": \">=1.0.2 <2.4.0\"");
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

		applyUpdate(packageJson, "react", "3.0.0");

		assertThat(packageJson).containsText("\"react\": \"2.x\"");
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

		applyUpdate(packageJson, "alias", "3.1.0");

		assertThat(packageJson).containsText("\"alias\": \"npm:@ankurk91/bootstrap-vue@^3.1.0\"");
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
		applyUpdate(packageJson, "cli", target);

		assertThat(packageJson).containsText("#v1.0.28")
				.doesNotContainText("#v1.0.27")
				.doesNotContainText("# ");
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

		assertThat(packageJson).containsText("#d1185ce")
				.doesNotContainText("#abcdef0")
				.doesNotContainText("# ");
	}

	private void applyUpdate(PsiFile file, String id, String targetVersion) {
		applyUpdate(file, id, ArtifactVersion.of(targetVersion));
	}

	private void applyUpdate(PsiFile file, String id, ArtifactVersion targetVersion) {
		applyUpdate(file, ArtifactId.of(id, id), targetVersion);
	}

	private void applyUpdate(PsiFile file, ArtifactId id, ArtifactVersion targetVersion) {

		DependencyUpdate update = DependencyUpdate.create(id, targetVersion);
		UpdatePackageJsonFile updater = new UpdatePackageJsonFile(file.getProject());

		BuildFileUpdates.applyUpdate(file, update, updater::applyUpdates);
	}

}
