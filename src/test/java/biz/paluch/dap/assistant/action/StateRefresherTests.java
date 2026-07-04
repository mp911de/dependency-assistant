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

package biz.paluch.dap.assistant.action;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.maven.MavenFixtures;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for {@link StateRefresher}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class StateRefresherTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>hello.world</groupId>
						<artifactId>drift-bom</artifactId>
						<version>6.0.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void refreshRecollectsStateFromUnsavedEdit(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		replaceText(pomFile, "6.0.0", "6.0.3");

		StateRefresher.getInstance(fixture.getProject()).refreshNow(List.of(pomFile.getVirtualFile()));

		Dependency dependency = StateService.getInstance(fixture.getProject())
				.getProjectState(ProjectId.of("com.example", "demo"))
				.findDependency(ArtifactId.of("hello.world", "drift-bom"));

		assertThat(dependency).isNotNull();
		assertThat(dependency.getCurrentVersion()).isEqualTo(ArtifactVersion.of("6.0.3"));
	}

	private void replaceText(PsiFile file, String oldText, String newText) {

		Project project = fixture.getProject();
		PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
		WriteCommandAction.runWriteCommandAction(project, () -> {

			Document document = documentManager.getDocument(file);
			int offset = document.getText().indexOf(oldText);
			document.replaceString(offset, offset + oldText.length(), newText);
			documentManager.commitDocument(document);
		});
	}

}
