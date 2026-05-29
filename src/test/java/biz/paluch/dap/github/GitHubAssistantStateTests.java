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

import biz.paluch.dap.DependencyScanEntry;
import biz.paluch.dap.GitRefIntrospectedDependencies;
import biz.paluch.dap.IntrospectedDependencies;
import biz.paluch.dap.ProjectStateUpdater;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link GitHubAssistant} as a {@link biz.paluch.dap.DependencySource} routed through the shared
 * {@link biz.paluch.dap.ProjectStateUpdater}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GitHubAssistantStateTests {

	@BeforeEach
	void setUp(Project project) {
		GitHubFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.2.0
			""")
	void invalidateFilePopulatesStateThroughSharedUpdater(Project project, PsiFile workflowFile) {

		GitHubAssistant assistant = new GitHubAssistant();
		new ProjectStateUpdater(project).invalidateFile(assistant, workflowFile);

		DependencyScanEntry entry = assistant.createEntry(project, workflowFile);
		assertThat(entry).isNotNull();
		ProjectState state = StateService.getInstance(project).getProjectState(entry.context().getProjectId());
		assertThat(state.hasDependencies()).isTrue();
		Dependency usage = state.findDependency(GitHubFixtures.CHECKOUT);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion().toString()).isEqualTo("v4.2.0");
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.2.0
			""")
	void collectGathersDeclarationsForAnchorFile(Project project, PsiFile workflowFile) {

		GitHubAssistant assistant = new GitHubAssistant();
		DependencyScanEntry entry = assistant.createEntry(project, workflowFile);
		assertThat(entry).isNotNull();

		DependencyCollector collector = new DependencyCollector();
		assistant.collect(entry, collector);

		assertThat(collector.getDeclarations())
				.anyMatch(it -> it.getArtifactId().equals(GitHubFixtures.CHECKOUT));
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.2.0
			""")
	void introspectReturnsCacheAwareCompletion(Project project, PsiFile workflowFile) {

		GitHubAssistant assistant = new GitHubAssistant();
		IntrospectedDependencies introspected = assistant.introspect(project);

		assertThat(introspected).isInstanceOf(GitRefIntrospectedDependencies.class);
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v4.2.0
			""")
	void getAllDependenciesAttachesGitHubReleaseSources(Project project, PsiFile workflowFile) {

		DependencyCollector aggregate = new GitHubAssistant().getAllDependencies(project, new EmptyProgressIndicator());

		assertThat(aggregate.getReleaseSources()).isNotEmpty();
		assertThat(aggregate.getReleaseSources()).allMatch(it -> it instanceof GitHubReleaseSource);
	}

}
