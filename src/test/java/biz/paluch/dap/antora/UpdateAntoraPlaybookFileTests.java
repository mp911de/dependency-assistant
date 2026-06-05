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

package biz.paluch.dap.antora;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.BuildActionDelegate;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for {@link UpdateAntoraPlaybookFile}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateAntoraPlaybookFileTests {

	@BeforeEach
	void setUp(Project project) {
		AntoraFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip
			""")
	void updatesVersionSegment(PsiFile playbookFile) {

		applyUpdate(playbookFile, GitVersion.of(ArtifactVersion.of("v0.4.26")));

		assertThat(playbookFile.getText())
				.contains("https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip");
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: 'https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip'
			""")
	void preservesSingleQuotes(PsiFile playbookFile) {

		applyUpdate(playbookFile, GitVersion.of(ArtifactVersion.of("v0.4.26")));

		assertThat(playbookFile.getText())
				.contains("'https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip'");
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: "https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip"
			""")
	void preservesDoubleQuotes(PsiFile playbookFile) {

		applyUpdate(playbookFile, GitVersion.of(ArtifactVersion.of("v0.4.26")));

		assertThat(playbookFile.getText())
				.contains("\"https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip\"");
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip # keep me
			""")
	void preservesTrailingComment(PsiFile playbookFile) {

		applyUpdate(playbookFile, GitVersion.of(ArtifactVersion.of("v0.4.26")));

		assertThat(playbookFile.getText())
				.contains("https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip")
				.contains("# keep me");
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			site:
			  title: Spring Docs
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip
			    snapshot: true
			content:
			  sources:
			    - url: https://example.com/sources.git
			""")
	void preservesUnrelatedContent(PsiFile playbookFile) {

		String originalText = playbookFile.getText();

		applyUpdate(playbookFile, GitVersion.of(ArtifactVersion.of("v0.4.26")));

		String updatedText = playbookFile.getText();

		assertThat(updatedText).contains("title: Spring Docs");
		assertThat(updatedText).contains("snapshot: true");
		assertThat(updatedText).contains("https://example.com/sources.git");
		assertThat(updatedText).isEqualTo(originalText.replace("/v0.4.25/", "/v0.4.26/"));
	}

	private void applyUpdate(PsiFile file, GitVersion targetVersion) {

		ArtifactId id = GitArtifactId.of("github.com", "spring-io", "antora-ui-spring");

		Dependency dep = new Dependency(id, targetVersion);
		dep.addDeclarationSource(DeclarationSource.dependency());
		dep.addVersionSource(VersionSource.declared("v0.4.25"));

		DependencyUpdate update = new DependencyUpdate(id, targetVersion, dep.getDeclarationSources(),
				dep.getVersionSources());

		UpdateAntoraPlaybookFile updater = new UpdateAntoraPlaybookFile(file.getProject());

		new BuildActionDelegate(file.getProject(), updater::applyUpdates)
				.updateBuildFile(file.getVirtualFile(), List.of(update));
	}

}
