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

package biz.paluch.dap.maven;

import java.util.Map;

import biz.paluch.dap.DependencyScanEntry;
import biz.paluch.dap.IntrospectedDependencies;
import biz.paluch.dap.ProjectStateUpdater;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.maven.MavenProjectContext.MavenContextImpl;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.idea.maven.model.MavenId;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link MavenAssistant} as a
 * {@link biz.paluch.dap.DependencySource} routed through the shared
 * {@link biz.paluch.dap.ProjectStateUpdater}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class MavenAssistantStateTests {

	private static final ArtifactId COMMONS_LANG = ArtifactId.of("org.apache.commons", "commons-lang3");

	@BeforeEach
	void setUp(Project project) {
		MavenFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<properties>
					<commons.version>3.19.0</commons.version>
				</properties>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>${commons.version}</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void collectGathersDeclarationsAndPropertyValues(Project project, PsiFile pomFile) {

		MavenProjectContext context = inject(pomFile);
		MavenAssistant assistant = new MavenAssistant();
		DependencyScanEntry entry = DependencyScanEntry.of(pomFile, context);

		DependencyCollector collector = new DependencyCollector();
		assistant.collect(entry, collector);

		assertThat(collector.getPropertyValues()).containsEntry("commons.version", "3.19.0");
		assertThat(collector.getUsages())
				.anyMatch(it -> it.getArtifactId().equals(COMMONS_LANG));
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>${commons.version}</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void completePromotesDeclarationWhenAnotherCollectorDefinesPropertyValue(Project project, PsiFile pomFile) {

		MavenProjectContext context = inject(pomFile);
		MavenAssistant assistant = new MavenAssistant();
		DependencyScanEntry entry = DependencyScanEntry.of(pomFile, context);

		MavenIntrospectedDependencies introspected = (MavenIntrospectedDependencies) assistant.introspect(project);
		DependencyCollector parent = new DependencyCollector();
		assistant.collect(entry, parent, introspected);

		DependencyCollector child = new DependencyCollector();
		child.addPropertyValues(Map.of("commons.version", "3.19.0"));
		introspected.register(child);

		introspected.complete(child);

		Dependency usage = child.getUsage(COMMONS_LANG);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion().toString()).isEqualTo("3.19.0");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>3.19.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void invalidateFileStoresDependenciesViaProjectStateUpdater(Project project, PsiFile pomFile) {

		MavenProjectContext context = inject(pomFile);
		MavenAssistant assistant = new MavenAssistant();

		new ProjectStateUpdater(project).invalidateFile(assistant, pomFile);

		ProjectState state = StateService.getInstance(project).getProjectState(context.getProjectId());
		assertThat(state.hasDependencies()).isTrue();
		Dependency usage = state.findDependency(COMMONS_LANG);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion().toString()).isEqualTo("3.19.0");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = "")
	void introspectReturnsMavenIntrospectedDependencies(Project project) {

		MavenAssistant assistant = new MavenAssistant();

		IntrospectedDependencies introspected = assistant.introspect(project);

		assertThat(introspected).isInstanceOf(MavenIntrospectedDependencies.class);
	}

	@Test
	@ProjectFile(name = "unrelated.xml", content = "<root/>")
	void createEntryReturnsNullForUnsupportedFile(Project project, PsiFile unrelated) {

		MavenAssistant assistant = new MavenAssistant();

		DependencyScanEntry entry = assistant.createEntry(project, unrelated);

		assertThat(entry).isNull();
	}

	@Test
	void enumerateReturnsEmptyWhenProjectHasNoMavenizedProject(Project project) {

		MavenAssistant assistant = new MavenAssistant();

		assertThat(assistant.enumerate(project)).isEmpty();
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<properties>
					<commons.version>3.19.0</commons.version>
				</properties>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>${commons.version}</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void projectStateCachePopulatesAfterCompletionViaSetDependencies(Project project, PsiFile pomFile) {

		MavenProjectContext context = inject(pomFile);
		MavenAssistant assistant = new MavenAssistant();

		new ProjectStateUpdater(project).invalidateFile(assistant, pomFile);

		assertThat(StateService.getInstance(project).getCache().getProject(context.getProjectId()).getProperties())
				.anyMatch(it -> "commons.version".equals(it.name()));
	}

	private static MavenProjectContext inject(PsiFile file) {

		MavenProject mavenProject = new MavenProject(file.getVirtualFile());
		mavenProject.updateMavenId(new MavenId("com.example", "demo", ""));
		MavenProjectsManager manager = MavenProjectsManager.getInstance(file.getProject());
		PsiManager psiManager = PsiManager.getInstance(file.getProject());
		MavenContextImpl context = new MavenContextImpl(file.getProject(), manager, psiManager, mavenProject);
		file.putUserData(MavenProjectContext.KEY, context);
		return context;
	}

}
