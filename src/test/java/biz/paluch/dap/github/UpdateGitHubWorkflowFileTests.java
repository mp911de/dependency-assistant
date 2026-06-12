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

package biz.paluch.dap.github;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.BuildActionDelegate;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link UpdateGitHubWorkflowFile}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateGitHubWorkflowFileTests {

	@BeforeEach
	void setUp(Project project) {
		GitHubFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@4.1.0 # here to stay
			""")
	void updatesVersionRefWithoutVPrefix(PsiFile workflowFile) {

		applyUpdate(workflowFile, "actions", "checkout", "4.1.0",
				GitVersion.of(GitHubFixtures.SHA_V4, ArtifactVersion.of("4.2.0")));

		assertThat(workflowFile.getText()).contains("actions/checkout@4.2.0 # here to stay");
		assertThat(workflowFile.getText()).doesNotContain("@v");
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1 # replace # here to stay
			""")
	void updatesShaRefAndAddsVersionComment(PsiFile workflowFile) {

		applyUpdate(workflowFile, "actions", "checkout", GitHubFixtures.SHA_V3,
				GitVersion.of(GitHubFixtures.SHA_V4, ArtifactVersion.of("v4.2.0")));

		assertThat(workflowFile.getText())
				.contains("actions/checkout@" + GitHubFixtures.SHA_V4 + " # v4.2.0 # here to stay");
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - name: Checkout source code
			        uses: actions/checkout@7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1
			        with:
			          repository: 'spring-projects/spring-batch-extensions'
			          ref: 'main'
			""")
	void addsVersionCommentBehindShaRefWhenStepHasNestedMapping(PsiFile workflowFile) {

		applyUpdate(workflowFile, "actions", "checkout", GitHubFixtures.SHA_V3,
				GitVersion.of(GitHubFixtures.SHA_V4, ArtifactVersion.of("v4.2.0")));

		assertThat(workflowFile)
				.containsText("uses: actions/checkout@" + GitHubFixtures.SHA_V4 + " # v4.2.0")
				.containsText("ref: 'main'\n")
				.doesNotContainText("ref: 'main'# v4.2.0");
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - name: Checkout source code
			        uses: actions/checkout@7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1
			        with:
			          ref: 'main' # keep branch
			""")
	void leavesNestedMappingCommentsUnchangedWhenAddingVersionComment(PsiFile workflowFile) {

		applyUpdate(workflowFile, "actions", "checkout", GitHubFixtures.SHA_V3,
				GitVersion.of(GitHubFixtures.SHA_V4, ArtifactVersion.of("v4.2.0")));

		assertThat(workflowFile)
				.containsText("uses: actions/checkout@" + GitHubFixtures.SHA_V4 + " # v4.2.0")
				.containsText("ref: 'main' # keep branch")
				.doesNotContainText("ref: 'main' # v4.2.0");
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1 # v3.6.0 # foo
			""")
	void refreshesExistingManagedComment(PsiFile workflowFile) {

		applyUpdate(workflowFile, "actions", "checkout", GitHubFixtures.SHA_V3,
				GitVersion.of(GitHubFixtures.SHA_V4, ArtifactVersion.of("v4.2.0")));

		assertThat(workflowFile.getText()).contains("actions/checkout@" + GitHubFixtures.SHA_V4 + " # v4.2.0 # foo");
		assertThat(workflowFile.getText()).doesNotContain("v3.6.0");
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1   # custom note
			""")
	void preservesUnmanagedTrailingComment(PsiFile workflowFile) {

		applyUpdate(workflowFile, "actions", "checkout", GitHubFixtures.SHA_V3,
				GitVersion.of(GitHubFixtures.SHA_V4, ArtifactVersion.of("v4.2.0")));

		assertThat(workflowFile.getText())
				.contains("actions/checkout@" + GitHubFixtures.SHA_V4 + "   # v4.2.0");
		assertThat(workflowFile.getText()).doesNotContain("# custom note");
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			name: CI
			on:
			  push:
			    branches: [main]
			jobs:
			  build:
			    runs-on: ubuntu-latest
			    steps:
			      - uses: actions/checkout@v4.1.0
			      - name: Set up JDK
			        uses: actions/setup-java@v3.0.0
			        with:
			          java-version: '21'
			      - run: ./gradlew build
			  publish:
			    needs: build
			    runs-on: ubuntu-latest
			    steps:
			      - run: echo "publish"
			""")
	void preservesUnrelatedYamlContent(PsiFile workflowFile) {

		String originalText = workflowFile.getText();

		applyUpdate(workflowFile, "actions", "checkout", "v4.1.0",
				GitVersion.of(GitHubFixtures.SHA_V4, ArtifactVersion.of("v4.2.0")));

		String updatedText = workflowFile.getText();

		assertThat(updatedText).contains("actions/checkout@v4.2.0");
		assertThat(updatedText).contains("name: CI");
		assertThat(updatedText).contains("on:");
		assertThat(updatedText).contains("branches: [main]");
		assertThat(updatedText).contains("actions/setup-java@v3.0.0");
		assertThat(updatedText).contains("java-version: '21'");
		assertThat(updatedText).contains("./gradlew build");
		assertThat(updatedText).contains("publish:");
		assertThat(updatedText).contains("needs: build");
		assertThat(updatedText).contains("echo \"publish\"");
		assertThat(updatedText).isEqualTo(originalText.replace("@v4.1.0", "@v4.2.0"));
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: "actions/checkout@v4.1.0"
			""")
	void updatesQuotedScalarPreservingQuotes(PsiFile workflowFile) {

		applyUpdate(workflowFile, "actions", "checkout", "v4.1.0",
				GitVersion.of(GitHubFixtures.SHA_V4, ArtifactVersion.of("v4.2.0")));

		assertThat(workflowFile.getText()).contains("\"actions/checkout@v4.2.0\"");
	}

	private void applyUpdate(PsiFile file, String groupId, String artifactId, String fromRef,
			GitVersion targetVersion) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);

		Dependency dependency = new Dependency(id, targetVersion);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared(fromRef));

		DependencyUpdate update = DependencyUpdate.from(dependency, targetVersion);
		UpdateGitHubWorkflowFile updater = new UpdateGitHubWorkflowFile(file.getProject());

		new BuildActionDelegate(file.getProject(), updater::applyUpdates)
				.updateBuildFile(file.getVirtualFile(), List.of(update));
	}

}
