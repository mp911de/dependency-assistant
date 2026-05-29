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

package biz.paluch.dap.gradle;

import biz.paluch.dap.DependencyScanEntry;
import biz.paluch.dap.IntrospectedDependencies;
import biz.paluch.dap.ProjectStateUpdater;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.gradle.GradleProjectContext.GradleBuildContextImpl;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link GradleAssistant} as a
 * {@link biz.paluch.dap.DependencySource} routed through the shared
 * {@link biz.paluch.dap.ProjectStateUpdater}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GradleAssistantStateTests {

	private static final ArtifactId SPRING_BOOT = ArtifactId.of("org.springframework.boot",
			"org.springframework.boot");

	@BeforeEach
	void setUp(Project project) {
		GradleFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id 'org.springframework.boot' version '4.0.3'
			}
			""")
	void invalidateFilePopulatesStateThroughSharedUpdater(Project project, PsiFile buildFile) {

		GradleProjectContext context = inject(buildFile);
		GradleAssistant assistant = new GradleAssistant();

		new ProjectStateUpdater(project).invalidateFile(assistant, buildFile);

		ProjectState state = StateService.getInstance(project).getProjectState(context.getProjectId());
		assertThat(state.hasDependencies()).isTrue();
		Dependency usage = state.findDependency(SPRING_BOOT);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion().toString()).isEqualTo("4.0.3");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id 'org.springframework.boot' version '4.0.3'
			}
			""")
	void collectGathersDeclarationsForBuildScript(Project project, PsiFile buildFile) {

		inject(buildFile);
		GradleAssistant assistant = new GradleAssistant();
		DependencyScanEntry entry = assistant.createEntry(project, buildFile);
		assertThat(entry).isNotNull();

		DependencyCollector collector = new DependencyCollector();
		assistant.collect(entry, collector);

		assertThat(collector.getUsages())
				.anyMatch(it -> it.getArtifactId().equals(SPRING_BOOT));
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[versions]
			boot = "4.0.3"

			[libraries]
			boot = { module = "org.springframework.boot:spring-boot", version.ref = "boot" }
			""")
	void collectGathersDeclarationsForVersionCatalog(Project project, PsiFile catalogFile) {

		inject(catalogFile);
		GradleAssistant assistant = new GradleAssistant();
		DependencyScanEntry entry = assistant.createEntry(project, catalogFile);
		assertThat(entry).isNotNull();

		DependencyCollector collector = new DependencyCollector();
		assistant.collect(entry, collector);

		assertThat(collector.getDeclarations())
				.anyMatch(it -> it.getArtifactId().equals(ArtifactId.of("org.springframework.boot", "spring-boot")));
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			springBootVersion=4.0.3
			""")
	void collectGathersDeclarationsForGradleProperties(Project project, PsiFile propertiesFile) {

		inject(propertiesFile);
		GradleAssistant assistant = new GradleAssistant();
		DependencyScanEntry entry = assistant.createEntry(project, propertiesFile);
		assertThat(entry).isNotNull();

		DependencyCollector collector = new DependencyCollector();
		assistant.collect(entry, collector);

		assertThat(collector.getProperties()).contains("springBootVersion");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = "")
	void introspectReturnsEmptyByDefault(Project project) {

		GradleAssistant assistant = new GradleAssistant();

		IntrospectedDependencies introspected = assistant.introspect(project);

		assertThat(introspected).isSameAs(IntrospectedDependencies.empty());
	}

	@Test
	@ProjectFile(name = "unrelated.json", content = "{}")
	void createEntryReturnsNullForUnsupportedFile(Project project, PsiFile unrelated) {

		GradleAssistant assistant = new GradleAssistant();

		DependencyScanEntry entry = assistant.createEntry(project, unrelated);

		assertThat(entry).isNull();
	}

	@Test
	void enumerateReturnsEmptyWhenProjectHasNoLinkedGradleSettings(Project project) {

		GradleAssistant assistant = new GradleAssistant();

		assertThat(assistant.enumerate(project)).isEmpty();
	}

	@Test
	void getAllDependenciesReturnsEmptyCollectorWhenProjectHasNoLinkedGradleSettings(Project project) {

		DependencyCollector aggregate = new GradleAssistant().getAllDependencies(project, new EmptyProgressIndicator());

		assertThat(aggregate.getUsages()).isEmpty();
		assertThat(aggregate.getDeclarations()).isEmpty();
	}

	private static GradleProjectContext inject(PsiFile file) {

		GradleProjectContext context = new GradleBuildContextImpl(file.getProject(), "",
				new ProjectId("demo", "demo", file.getVirtualFile().getPath()));
		file.putUserData(GradleProjectContext.KEY, context);
		return context;
	}

}
