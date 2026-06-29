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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.fixtures.BuildFileUpdates;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

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

		applyUpdate(playbookFile, "v0.4.26");

		assertThat(playbookFile)
				.containsText("https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip");
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: 'https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip'
			""")
	void preservesSingleQuotes(PsiFile playbookFile) {

		applyUpdate(playbookFile, "v0.4.26");

		assertThat(playbookFile)
				.containsText(
						"'https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip'");
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: "https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip"
			""")
	void preservesDoubleQuotes(PsiFile playbookFile) {

		applyUpdate(playbookFile, "v0.4.26");

		assertThat(playbookFile)
				.containsText(
						"\"https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip\"");
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip # keep me
			""")
	void preservesTrailingComment(PsiFile playbookFile) {

		applyUpdate(playbookFile, "v0.4.26");

		assertThat(playbookFile)
				.containsText("https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.26/ui-bundle.zip")
				.containsText("# keep me");
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

		applyUpdate(playbookFile, "v0.4.26");

		String updatedText = playbookFile.getText();

		assertThat(updatedText).contains("title: Spring Docs");
		assertThat(updatedText).contains("snapshot: true");
		assertThat(updatedText).contains("https://example.com/sources.git");
		assertThat(updatedText).isEqualTo(originalText.replace("/v0.4.25/", "/v0.4.26/"));
	}

	private void applyUpdate(PsiFile file, String toTag) {

		GitArtifactId id = GitArtifactId.of("github.com", "spring-io", "antora-ui-spring");
		GitVersion targetVersion = GitVersion.of(ArtifactVersion.of(toTag));

		Dependency dependency = new Dependency(id, targetVersion);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared("v0.4.25"));

		DependencyUpdate update = DependencyUpdate.from(dependency, targetVersion);
		UpdateAntoraPlaybookFile updater = new UpdateAntoraPlaybookFile(file.getProject());

		BuildFileUpdates.applyUpdate(file, update, updater::applyUpdates);
	}

}
