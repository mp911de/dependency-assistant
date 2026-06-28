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

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.maven.MavenFixtures;
import biz.paluch.dap.support.UpgradeStrategy;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for {@link ApplyAllUpgradesIntention}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class ApplyAllUpgradesIntentionTests {

	static final String POM = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>io.vavr</groupId>
						<artifactId>vavr</artifactId>
						<version>0.11.0</version>
					</dependency>
					<dependency>
						<groupId>io.vavr</groupId>
						<artifactId>vavr-match</artifactId>
						<version>0.11.0</version>
					</dependency>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>5.14.0</version>
					</dependency>
				</dependencies>
			</project>
			""";

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "pom.xml", content = POM)
	void appliesAllUpgradesOfSameStrategy(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		fixture.doHighlighting();
		invoke(pomFile, UpgradeStrategy.MAJOR);

		assertThat(pomFile).containsText("1.0.1") // vavr
				.containsText("<version>1.0.0</version>") // vavr-match
				.containsText("6.0.3") // junit-bom
				.doesNotContainText("0.11.0").doesNotContainText("5.14.0");
	}

	@Test
	@EditorFile(name = "pom.xml", content = POM)
	void skipsDeclarationsWithoutUpgradeForStrategy(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		fixture.doHighlighting();
		invoke(pomFile, UpgradeStrategy.PATCH);

		// no 0.11.x patch release cached for vavr/vavr-match
		assertThat(pomFile).containsText("5.14.3").containsText("0.11.0").doesNotContainText("5.14.0");
	}

	@Test
	@EditorFile(name = "pom.xml", content = POM)
	void namesStrategyInPresentation(PsiFile pomFile) {
		assertThat(intention(pomFile, UpgradeStrategy.MAJOR).getText()).isEqualTo("Apply all 'Major upgrades' in file");
	}

	private void invoke(PsiFile file, UpgradeStrategy strategy) {

		ApplyAllUpgradesIntention intention = intention(file, strategy);
		WriteCommandAction.runWriteCommandAction(fixture.getProject(),
				() -> intention.invoke(fixture.getProject(), fixture.getEditor(), file));
	}

	private ApplyAllUpgradesIntention intention(PsiFile file, UpgradeStrategy strategy) {
		return new ApplyAllUpgradesIntention(DependencyAssistantDispatcher.findFirstContext(file), strategy);
	}

}
