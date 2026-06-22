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

package biz.paluch.dap.maven.wrapper;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.assistant.BuildActionDelegate;
import biz.paluch.dap.assistant.UpdateCaretTester;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Facade-based caret placement tests for the Maven wrapper
 * {@code distributionUrl} update path, whose value carries the version twice.
 * The caret follows the occurrence under the pre-edit caret and falls back to
 * the first occurrence when the caret is outside any version segment.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenWrapperUpdateCaretTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenWrapperFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.<caret>6/apache-maven-3.9.6-bin.zip
			""")
	void caretOnPathOccurrenceLandsBehindFirstNewSegment(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(file).caretBetween("/apache-maven/3.9.9", "/apache-maven-3.9.9-bin.zip");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.<caret>6-bin.zip
			""")
	void caretOnFileOccurrenceLandsBehindSecondNewSegment(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(file).caretBetween("apache-maven-3.9.9", "-bin.zip");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org<caret>/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void caretOffAnyOccurrenceFallsBackToFirstNewSegment(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(file).caretBetween("/apache-maven/3.9.9", "/apache-maven-3.9.9-bin.zip");
	}

	@Test
	@EditorFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			<caret>distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void batchUpdateMovesNoCaret(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		DependencyUpdate update = DependencyUpdate.create(ArtifactId.of("org.apache.maven", "apache-maven"),
				ArtifactVersion.of("3.9.9"));
		new BuildActionDelegate(file.getProject(), UpdateMavenWrapperProperties::applyUpdates)
				.updateBuildFile(file.getVirtualFile(), List.of(update));

		assertThat(file).containsText("/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip")
				.caretBefore("distributionUrl");
	}

}
