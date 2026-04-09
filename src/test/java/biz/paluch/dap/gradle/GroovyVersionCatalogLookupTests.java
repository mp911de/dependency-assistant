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
package biz.paluch.dap.gradle;

import static org.assertj.core.api.Assertions.*;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.toml.lang.psi.TomlLiteral;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.junit5.RunInEdt;

/**
 * Groovy DSL version-catalog accessors via {@link VersionUpgradeLookupService}.
 *
 * @author Mark Paluch
 */
@RunInEdt(writeIntent = true)
class GroovyVersionCatalogLookupTests {

	private CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() throws Exception {
		TestFixtureBuilder<IdeaProjectTestFixture> builder = IdeaTestFixtureFactory.getFixtureFactory()
				.createLightFixtureBuilder(new LightProjectDescriptor(), getClass().getSimpleName());
		fixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(builder.getFixture());
		fixture.setUp();
	}

	@AfterEach
	void tearDown() throws Exception {
		fixture.tearDown();
		fixture = null;
	}

	@Test
	void versionCatalogPluginAliasResolvesVersionsTableLiteral() {

		fixture.addFileToProject("gradle/libs.versions.toml",
				"""
						[versions]
						spring-dependency-management = "1.1.7"

						[plugins]
						spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
						""");
		PsiFile build = fixture.configureByText("build.gradle", """
				plugins {
				    alias(libs.plugins.spring.dependency.management)
				}
				""");

		GrReferenceExpression at = PsiTreeUtil.collectElementsOfType(build, GrReferenceExpression.class).stream()
				.filter(r -> "management".equals(r.getReferenceName())).findFirst().orElseThrow();

		VersionUpgradeLookupService service = new VersionUpgradeLookupService(build.getProject(), build);
		var ar = service.resolveGroovyArtifactReference(at);

		assertThat(ar.isResolved()).isTrue();
		assertThat(ar.getArtifactId().groupId()).isEqualTo("io.spring.dependency-management");
		assertThat(ar.getArtifactId().artifactId()).isEqualTo("io.spring.dependency-management");
		assertThat(ar.getDeclaration().getVersion().toString()).isEqualTo("1.1.7");
		assertThat(ar.getDeclaration().getVersionLiteral()).isInstanceOf(TomlLiteral.class);
		assertThat(TomlParser.getText((TomlLiteral) ar.getDeclaration().getVersionLiteral())).isEqualTo("1.1.7");
		assertThat(ar.getDeclaration().getDeclarationElement().getText()).startsWith("alias(");
	}

	@Test
	void versionCatalogLibraryAccessorUsesInlineTomlVersionLiteral() {

		fixture.addFileToProject("gradle/libs.versions.toml", """
				[libraries]
				commons-lang3 = { module = "org.apache.commons:commons-lang3", version = "3.19.0" }
				""");
		PsiFile build = fixture.configureByText("build.gradle", """
				dependencies {
				    implementation libs.commons.lang3
				}
				""");

		GrReferenceExpression at = PsiTreeUtil.collectElementsOfType(build, GrReferenceExpression.class).stream()
				.filter(r -> "lang3".equals(r.getReferenceName())).findFirst().orElseThrow();

		VersionUpgradeLookupService service = new VersionUpgradeLookupService(build.getProject(), build);
		var ar = service.resolveGroovyArtifactReference(at);

		assertThat(ar.isResolved()).isTrue();
		assertThat(ar.getArtifactId().groupId()).isEqualTo("org.apache.commons");
		assertThat(ar.getArtifactId().artifactId()).isEqualTo("commons-lang3");
		assertThat(ar.getDeclaration().getVersion().toString()).isEqualTo("3.19.0");
		assertThat(ar.getDeclaration().getVersionLiteral()).isInstanceOf(TomlLiteral.class);
		assertThat(TomlParser.getText(ar.getDeclaration().getVersionLiteral())).isEqualTo("3.19.0");
	}

	@Test
	void versionCatalogAccessorMarksInnerSegmentsRedundantForHighlight() {

		fixture.addFileToProject("gradle/libs.versions.toml",
				"""
						[versions]
						spring-dependency-management = "1.1.7"

						[plugins]
						spring-dependency-management = { id = "io.spring.dependency-management", version.ref = "spring-dependency-management" }
						""");
		PsiFile build = fixture.configureByText("build.gradle", """
				plugins {
				    alias(libs.plugins.spring.dependency.management)
				}
				""");

		VersionUpgradeLookupService service = new VersionUpgradeLookupService(build.getProject(), build);

		int nonRedundantResolved = 0;
		for (PsiElement e : PsiTreeUtil.collectElementsOfType(build, PsiElement.class)) {
			if (GroovyDslUtils.isRedundantGroovyCatalogHighlightAnchor(e)) {
				continue;
			}
			if (service.resolveGroovyArtifactReference(e).isResolved()) {
				nonRedundantResolved++;
			}
		}

		assertThat(nonRedundantResolved).isEqualTo(1);
	}
}
