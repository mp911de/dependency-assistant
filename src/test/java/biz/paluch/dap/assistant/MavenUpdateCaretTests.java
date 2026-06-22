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

package biz.paluch.dap.assistant;

import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.maven.MavenFixtures;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Facade-based caret placement tests for the Maven update path.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenUpdateCaretTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>6.0.<caret>0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void modCommandLandsCaretBehindInlineVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		new UpdateCaretTester(fixture).applyBestUpgradeViaModCommand();

		assertThat(pomFile).caretBetween("6.0.3", "</version>");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>6.0.<caret>0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void quickFixLandsCaretBehindInlineVersion(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(pomFile).caretBetween("6.0.3", "</version>");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<properties>
					<junit.version>6.0.<caret>0</junit.version>
				</properties>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>${junit.version}</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void quickFixLandsCaretBehindVersionProperty(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(pomFile).caretBetween("6.0.3", "</junit.version>");
	}

	@Test
	@EditorFile(name = ".mvn/extensions.xml", content = """
			<extensions>
				<extension>
					<groupId>org.junit</groupId>
					<artifactId>junit-bom</artifactId>
					<version>6.0.<caret>0</version>
				</extension>
			</extensions>
			""")
	void quickFixLandsCaretBehindExtensionVersion(PsiFile extensionsFile) {

		MavenFixtures.analyze(extensionsFile);

		new UpdateCaretTester(fixture).applyBestUpgradeViaQuickFix();

		assertThat(extensionsFile).caretBetween("6.0.3", "</version>");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.<caret>example</groupId>
				<artifactId>demo</artifactId>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>5.14.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void batchApplyAllMovesNoCaret(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);

		new UpdateCaretTester(fixture).applyAllUpgrades(UpgradeStrategy.MAJOR);

		assertThat(pomFile).containsText("<version>6.0.3</version>").caretBetween("com.", "example");
	}

}
