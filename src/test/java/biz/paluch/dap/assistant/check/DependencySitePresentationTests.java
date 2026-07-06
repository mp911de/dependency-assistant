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

package biz.paluch.dap.assistant.check;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.lookup.DependencySiteSearchHit;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link DependencySitePresentation}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class DependencySitePresentationTests {

	private @TestFixture Project project;

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v6
			      - name: Setup Java
			        uses: actions/setup-java@v4
			""")
	void previewForFirstStepShowsOnlyThatStep(PsiFile workflow) {

		DependencySitePresentation presentation = present(firstUsesScalar(workflow));

		assertThat(presentation.previewText()).isEqualTo("- uses: actions/checkout@v6");
		assertThat(presentation.previewText()).doesNotContain("setup-java").doesNotContain("name:");
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v6
			""")
	void describesLocationLabelAndFileTypeOfTheBackingFile(PsiFile workflow) {

		DependencySitePresentation presentation = present(firstUsesScalar(workflow));

		assertThat(presentation.location()).endsWith(".github/workflows/ci.yml:4");
		assertThat(presentation.label()).isEqualTo("actions/checkout@v6");
		assertThat(presentation.fileType()).isInstanceOf(YAMLFileType.class);
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@v6
			""")
	void labelTruncatesLongTextToASnippet(PsiFile workflow) {

		DependencySiteSearchHit hit = DependencySiteSearchHit.declaration(firstUsesScalar(workflow), "x".repeat(80));

		assertThat(DependencySitePresentation.from(hit, project).label()).hasSize(60).endsWith("...");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
			    <dependencies>
			        <dependency>
			            <groupId>org.springframework</groupId>
			            <artifactId>spring-core</artifactId>
			            <version>6.1.0</version>
			        </dependency>
			    </dependencies>
			</project>
			""")
	void previewForMavenVersionShowsTheVersionTagDedented(PsiFile pom) {

		XmlTag version = PsiTreeUtil.findChildrenOfType(pom, XmlTag.class).stream()
				.filter(tag -> "version".equals(tag.getName())).findFirst().orElseThrow();
		DependencySitePresentation presentation = DependencySitePresentation
				.from(DependencySiteSearchHit.declaration(version, "6.1.0"), project);

		assertThat(presentation.previewText()).isEqualTo("<version>6.1.0</version>");
		assertThat(presentation.label()).isEqualTo("6.1.0");
	}

	private DependencySitePresentation present(PsiElement element) {
		return DependencySitePresentation.from(DependencySiteSearchHit.declaration(element), project);
	}

	private static YAMLScalar firstUsesScalar(PsiFile workflow) {
		return PsiTreeUtil.findChildrenOfType(workflow, YAMLKeyValue.class).stream()
				.filter(keyValue -> "uses".equals(keyValue.getKeyText()))
				.map(YAMLKeyValue::getValue).filter(YAMLScalar.class::isInstance).map(YAMLScalar.class::cast)
				.findFirst().orElseThrow();
	}

}
