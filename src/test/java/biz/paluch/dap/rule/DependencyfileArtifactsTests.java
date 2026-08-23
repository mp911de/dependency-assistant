/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.rule;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.rule.DependencyfileArtifacts.ArtifactEntry;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Tests for {@link DependencyfileArtifacts}: entry computation (pure) and
 * {@code name} updates (PSI-level).
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class DependencyfileArtifactsTests {

	private static final ArtifactId CORE = ArtifactId.of("org.springframework", "spring-core");

	private static final ArtifactId CONTEXT = ArtifactId.of("org.springframework", "spring-context");

	private static final ArtifactId JACKSON = ArtifactId.of("com.fasterxml.jackson.core", "jackson-databind");

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	void entriesUseOneWildcardForSharedPrefix() {
		assertThat(DependencyfileArtifacts.entries(List.of(CORE, CONTEXT), "Spring"))
				.containsExactly(new ArtifactEntry("org.springframework:spring-*", "Spring"));
	}

	@Test
	void entriesFallBackToOnePerCoordinate() {
		assertThat(DependencyfileArtifacts.entries(List.of(CORE, JACKSON), "Mixed")).containsExactly(
				new ArtifactEntry("org.springframework:spring-core", "Mixed"),
				new ArtifactEntry("com.fasterxml.jackson.core:jackson-databind", "Mixed"));
	}

	@Test
	void singleCoordinateNeverUsesWildcard() {
		assertThat(DependencyfileArtifacts.entries(List.of(CORE), "Spring Core"))
				.containsExactly(new ArtifactEntry("org.springframework:spring-core", "Spring Core"));
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:spring-core": { "name": "Spring Core" }
			  }
			}
			""")
	void replacesExistingName(PsiFile file) {

		boolean changed = setNames(file, new ArtifactEntry("org.springframework:spring-core", "Spring"));

		assertThat(changed).isTrue();
		assertThat(file).containsText("\"name\": \"Spring\"").doesNotContainText("Spring Core");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:spring-core": { "generation": "6.x" }
			  }
			}
			""")
	void addsNameToEntryWithoutOne(PsiFile file) {

		setNames(file, new ArtifactEntry("org.springframework:spring-core", "Spring"));

		assertThat(file).containsText("\"name\": \"Spring\"").containsText("\"generation\": \"6.x\"");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:spring-core": { "name": "Spring" }
			  }
			}
			""")
	void leavesFileAloneWhenNameAlreadyMatches(PsiFile file) {

		String before = file.getText();
		boolean changed = setNames(file, new ArtifactEntry("org.springframework:spring-core", "Spring"));

		assertThat(changed).isFalse();
		assertThat(file).containsText(before);
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:spring-core": { "name": "Spring Core" }
			  }
			}
			""")
	void insertsMissingEntry(PsiFile file) {

		setNames(file, new ArtifactEntry("com.fasterxml.jackson.core:jackson-databind", "Jackson"));

		assertThat(file).containsText("\"com.fasterxml.jackson.core:jackson-databind\"")
				.containsText("\"name\": \"Jackson\"").containsText("\"name\": \"Spring Core\"");
	}

	private boolean setNames(PsiFile file, ArtifactEntry... entries) {
		return WriteCommandAction.writeCommandAction(fixture.getProject())
				.compute(() -> DependencyfileArtifacts.setNames(fixture.getProject(), file, List.of(entries)));
	}

}
