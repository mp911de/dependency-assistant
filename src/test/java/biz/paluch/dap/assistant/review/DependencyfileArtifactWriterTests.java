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

package biz.paluch.dap.assistant.review;

import java.util.List;

import biz.paluch.dap.assistant.review.DependencyfileArtifactWriter.ArtifactEntry;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link DependencyfileArtifactWriter}
 * insertion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class DependencyfileArtifactWriterTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.example:a-lib": { "name": "A" },
			    "org.example:z-lib": { "name": "Z" }
			  }
			}
			""")
	void insertsEntryInSortedPosition(PsiFile file) {

		insert(file, new ArtifactEntry("org.example:m-lib", "M"));

		assertThat(artifactKeys(file)).containsExactly("org.example:a-lib", "org.example:m-lib", "org.example:z-lib");
		assertThat(file).containsText("\"name\": \"M\"");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.example:a-lib": { "name": "A" }
			  }
			}
			""")
	void appendsEntryWhenKeyIsGreatest(PsiFile file) {

		insert(file, new ArtifactEntry("org.example:z-lib", "Z"));

		assertThat(artifactKeys(file)).containsExactly("org.example:a-lib", "org.example:z-lib");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {}
			}
			""")
	void insertsIntoEmptyArtifactsObject(PsiFile file) {

		insert(file, new ArtifactEntry("org.example:a-lib", "A"));

		assertThat(artifactKeys(file)).containsExactly("org.example:a-lib");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			}
			""")
	void createsArtifactsObjectWhenMissing(PsiFile file) {

		insert(file, new ArtifactEntry("org.example:a-lib", "A"));

		assertThat(artifactKeys(file)).containsExactly("org.example:a-lib");
		assertThat(file).containsText("\"artifacts\"");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.example:a-lib": { "name": "A" }
			  }
			}
			""")
	void returnsNullAndKeepsFileWhenEntryAlreadyPresent(PsiFile file) {

		TextRange range = insert(file, new ArtifactEntry("org.example:a-lib", "A"));

		assertThat(range).isNull();
		assertThat(artifactKeys(file)).containsExactly("org.example:a-lib");
	}

	@Test
	@EditorFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.example:m-lib": { "name": "M" }
			  }
			}
			""")
	void insertsEachEntrySortedAndSelectsFirstInserted(PsiFile file) {

		TextRange range = insert(file, new ArtifactEntry("org.example:b-lib", "B"),
				new ArtifactEntry("org.example:z-lib", "Z"));

		assertThat(artifactKeys(file)).containsExactly("org.example:b-lib", "org.example:m-lib", "org.example:z-lib");
		assertThat(range).isNotNull();
		assertThat(file.getText().substring(range.getStartOffset(), range.getEndOffset())).isEqualTo("B");
	}

	private TextRange insert(PsiFile file, ArtifactEntry... entries) {
		return WriteCommandAction.writeCommandAction(fixture.getProject())
				.compute(
						() -> DependencyfileArtifactWriter.insertEntries(fixture.getProject(), file, List.of(entries)));
	}

	private static List<String> artifactKeys(PsiFile file) {

		JsonObject root = (JsonObject) ((JsonFile) file).getTopLevelValue();
		JsonProperty artifacts = root.findProperty("artifacts");
		JsonObject object = (JsonObject) artifacts.getValue();
		return object.getPropertyList().stream().map(JsonProperty::getName).toList();
	}

}
