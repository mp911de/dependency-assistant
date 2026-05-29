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

import biz.paluch.dap.DependencyScanEntry;
import biz.paluch.dap.GitRefIntrospectedDependencies;
import biz.paluch.dap.IntrospectedDependencies;
import biz.paluch.dap.ProjectStateUpdater;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.github.GitReleaseSource;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link AntoraAssistant} as a
 * {@link biz.paluch.dap.DependencySource} routed through the shared
 * {@link biz.paluch.dap.ProjectStateUpdater}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class AntoraAssistantStateTests {

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
	void invalidateFilePopulatesStateThroughSharedUpdater(Project project, PsiFile playbookFile) {

		AntoraAssistant assistant = new AntoraAssistant();
		new ProjectStateUpdater(project).invalidateFile(assistant, playbookFile);

		DependencyScanEntry entry = assistant.createEntry(project, playbookFile);
		assertThat(entry).isNotNull();
		ProjectState state = StateService.getInstance(project).getProjectState(entry.context().getProjectId());
		assertThat(state.hasDependencies()).isTrue();
		Dependency usage = state.findDependency(AntoraFixtures.ANTORA_UI);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion().toString()).isEqualTo("v0.4.25");
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip
			""")
	void collectGathersDeclarationsForAnchorFile(Project project, PsiFile playbookFile) {

		AntoraAssistant assistant = new AntoraAssistant();
		DependencyScanEntry entry = assistant.createEntry(project, playbookFile);
		assertThat(entry).isNotNull();

		DependencyCollector collector = new DependencyCollector();
		assistant.collect(entry, collector);

		assertThat(collector.getDeclarations())
				.anyMatch(it -> it.getArtifactId().equals(AntoraFixtures.ANTORA_UI));
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip
			""")
	void introspectReturnsCacheAwareCompletion(Project project, PsiFile playbookFile) {

		AntoraAssistant assistant = new AntoraAssistant();
		IntrospectedDependencies introspected = assistant.introspect(project);

		assertThat(introspected).isInstanceOf(GitRefIntrospectedDependencies.class);
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip
			""")
	void getAllDependenciesAttachesAntoraReleaseSources(Project project, PsiFile playbookFile) {

		DependencyCollector aggregate = new AntoraAssistant().getAllDependencies(project, new EmptyProgressIndicator());

		assertThat(aggregate.getReleaseSources()).isNotEmpty();
		assertThat(aggregate.getReleaseSources()).allMatch(it -> it instanceof GitReleaseSource);
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip
			""")
	void warmCachePromotesDeclarationToUsage(Project project, PsiFile playbookFile) {

		AntoraAssistant assistant = new AntoraAssistant();
		new ProjectStateUpdater(project).invalidateFile(assistant, playbookFile);

		DependencyScanEntry entry = assistant.createEntry(project, playbookFile);
		assertThat(entry).isNotNull();
		ProjectState state = StateService.getInstance(project).getProjectState(entry.context().getProjectId());
		Dependency usage = state.findDependency(AntoraFixtures.ANTORA_UI);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion().toString()).isEqualTo("v0.4.25");
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v9.9.9/ui-bundle.zip
			""")
	void coldCacheLeavesDeclarationUnresolved(Project project, PsiFile playbookFile) {

		AntoraAssistant assistant = new AntoraAssistant();
		new ProjectStateUpdater(project).invalidateFile(assistant, playbookFile);

		DependencyScanEntry entry = assistant.createEntry(project, playbookFile);
		assertThat(entry).isNotNull();
		ProjectState state = StateService.getInstance(project).getProjectState(entry.context().getProjectId());
		assertThat(state.findDependency(AntoraFixtures.ANTORA_UI)).isNull();
	}

}
