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

package biz.paluch.dap.assistant.documentation;

import biz.paluch.dap.assistant.documentation.ReleaseLookupDocumentationProvider.ReleaseDocumentationTarget;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.maven.MavenFixtures;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.platform.backend.documentation.DocumentationTarget;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link ReleaseLookupDocumentationProvider}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class ReleaseLookupDocumentationProviderTests {

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
						<version>6.0.<caret>2</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void documentsHighlightedNewerRelease(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		fixture.completeBasic();

		assertThat(documentation(pomFile, "6.0.3"))
				.contains("org.junit:junit-bom 6.0.3")
				.contains("Released")
				.contains("Patch upgrade, released 1 month <i>after</i> the current version")
				.contains("6.0.2");
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<dependencies>
					<dependency>
						<groupId>org.junit</groupId>
						<artifactId>junit-bom</artifactId>
						<version>6.0.<caret>2</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void documentsHighlightedCurrentRelease(PsiFile pomFile) {

		MavenFixtures.analyze(pomFile);
		fixture.completeBasic();

		assertThat(documentation(pomFile, "6.0.2")).contains("Currently declared version");
	}

	private String documentation(PsiFile file, String lookupString) {

		DocumentationTarget target = new ReleaseLookupDocumentationProvider().documentationTarget(file,
				lookupElement(lookupString), fixture.getCaretOffset());

		assertThat(target).isInstanceOf(ReleaseDocumentationTarget.class);
		return ((ReleaseDocumentationTarget) target).buildHtmlBody();
	}

	private LookupElement lookupElement(String lookupString) {

		for (LookupElement element : fixture.getLookupElements()) {
			if (element.getLookupString().equals(lookupString)) {
				return element;
			}
		}
		throw new IllegalArgumentException("No lookup element " + lookupString);
	}

}
