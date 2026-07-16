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

import java.util.List;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import biz.paluch.dap.lookup.SiteRole;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for Dependency Site Find in Maven POMs.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class MavenSiteSearchTests {

	private @TestFixture Project project;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
			    <groupId>com.example</groupId>
			    <artifactId>demo</artifactId>
			    <version>1.0.0</version>
			    <properties>
			        <spring.version>6.1.0</spring.version>
			    </properties>
			    <dependencies>
			        <dependency>
			            <groupId>org.springframework</groupId>
			            <artifactId>spring-core</artifactId>
			            <version>${spring.version}</version>
			        </dependency>
			    </dependencies>
			</project>
			""")
	void findsPropertyDefinitionAndUsage(PsiFile pom) {

		MavenFixtures.analyze(pom);

		List<DependencySiteSearchHit> definitions = search(pom, DependencySiteQuery.ofProperty("spring.version"),
				SiteRole.DECLARATION);
		assertThat(definitions).hasSize(1);
		assertThat(definitions).first().satisfies(it -> assertThat(it.element()).containsText("6.1.0"));

		List<DependencySiteSearchHit> usages = search(pom, DependencySiteQuery.ofProperty("spring.version"),
				SiteRole.VERSION_USAGE);
		assertThat(usages).hasSize(1);
		assertThat(usages).first()
				.satisfies(it -> assertThat(it.element()).containsText("spring.version").containsText("spring-core"));
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
			    <groupId>com.example</groupId>
			    <artifactId>demo</artifactId>
			    <version>1.0.0</version>
			    <dependencies>
			        <dependency>
			            <groupId>org.springframework</groupId>
			            <artifactId>spring-core</artifactId>
			            <version>6.1.0</version>
			        </dependency>
			    </dependencies>
			</project>
			""")
	void findsInlineVersionDefinition(PsiFile pom) {

		MavenFixtures.analyze(pom);

		List<DependencySiteSearchHit> definitions = search(pom,
				DependencySiteQuery.ofArtifact("org.springframework", "spring-core"), SiteRole.DECLARATION);

		assertThat(definitions).hasSize(1);
		assertThat(definitions).first().satisfies(it -> assertThat(it.element()).containsText("6.1.0"));
	}

	private List<DependencySiteSearchHit> search(PsiFile file, DependencySiteQuery query, SiteRole role) {
		return resolverFor(file).search(query).stream().filter(finding -> finding.role() == role).toList();
	}

	private MavenArtifactReferenceResolver resolverFor(PsiFile file) {
		MavenProjectContext buildContext = file.getUserData(MavenProjectContext.KEY);
		ProjectState projectState = StateService.getInstance(project).getProjectState(buildContext.getProjectId());
		return new MavenArtifactReferenceResolver(projectState, file, buildContext);
	}

}
