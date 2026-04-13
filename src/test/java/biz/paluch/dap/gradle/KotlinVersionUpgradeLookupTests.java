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

import java.util.Collection;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.junit5.RunInEdt;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.toml.lang.psi.TomlLiteral;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for Kotlin DSL via {@link VersionUpgradeLookupService}.
 *
 * @author Mark Paluch
 */
@RunInEdt(writeIntent = true)
class KotlinVersionUpgradeLookupTests {

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
	void findPropertyExpressionMatchesBuildStringAppendValue() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				extra["springModulithVersion"] = buildString {
				        append("2.0.3")
				    }
				""");

		KtLiteralStringTemplateEntry versionEntry = PsiTreeUtil
				.collectElementsOfType(file, KtLiteralStringTemplateEntry.class).stream()
				.filter(e -> "2.0.3".equals(e.getText())).findFirst().orElseThrow();

		KtBinaryExpression propertyExpr = KotlinDslUtils.findPropertyExpression(versionEntry);
		assertThat(propertyExpr).as("extra[…] = buildString { … }").isNotNull();
		assertThat(KotlinDslUtils.findProperty(propertyExpr)).isEqualTo("springModulithVersion");
		assertThat(KotlinDslExtraParser.findExtraPropertyLocation(file, "springModulithVersion")).isNotNull();
	}

	@Test
	void findPropertyExpressionMatchesAlsoReceiverLiteral() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				"2.0.3".also { extra["springModulithVersion"] = it }
				""");

		KtLiteralStringTemplateEntry versionEntry = PsiTreeUtil
				.collectElementsOfType(file, KtLiteralStringTemplateEntry.class).stream()
				.filter(e -> "2.0.3".equals(e.getText())).findFirst().orElseThrow();

		KtBinaryExpression propertyExpr = KotlinDslUtils.findPropertyExpression(versionEntry);
		assertThat(propertyExpr).as("receiver of .also { extra[…] = it }").isNotNull();
		assertThat(KotlinDslUtils.findProperty(propertyExpr)).isEqualTo("springModulithVersion");
		assertThat(KotlinDslExtraParser.findExtraPropertyLocation(file, "springModulithVersion").element())
				.isSameAs(versionEntry.getParent());
	}

	@Test
	void findPropertyExpressionMatchesTripleQuotedAssignment() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				extra["springModulithVersion"] = \"""2.0.3\"""
				""");

		KtLiteralStringTemplateEntry versionEntry = PsiTreeUtil
				.collectElementsOfType(file, KtLiteralStringTemplateEntry.class).stream()
				.filter(e -> "2.0.3".equals(e.getText())).findFirst().orElseThrow();

		KtBinaryExpression propertyExpr = KotlinDslUtils.findPropertyExpression(versionEntry);
		assertThat(propertyExpr).as("extra[…] = \"\"\"…\"\"\"").isNotNull();
		assertThat(KotlinDslUtils.findProperty(propertyExpr)).isEqualTo("springModulithVersion");
		assertThat(KotlinDslExtraParser.findExtraPropertyLocation(file, "springModulithVersion")).isNotNull();
	}

	@Test
	void kotlinAlsoExtraPatternHasSingleNonRedundantLiteralEntryForVersion() {

		PsiFile file = fixture.configureByText("build.gradle.kts", """
				"2.0.3".also { extra["springModulithVersion"] = it }

				dependencyManagement {
				    imports {
				        mavenBom("org.springframework.modulith:spring-modulith-bom:${property("springModulithVersion")}")
				    }
				}
				""");

		VersionUpgradeLookupService service = new VersionUpgradeLookupService(file.getProject(), file);

		Collection<PsiElement> list = PsiTreeUtil.collectElementsOfType(file, PsiElement.class);

		int hits = 0;
		for (PsiElement element : list) {
			if (service.resolveKotlinArtifactReference(element).isResolved()) {
				hits++;
			}
		}

		assertThat(hits).isEqualTo(1);
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
		PsiFile build = fixture.configureByText("build.gradle.kts", """
				plugins {
				    alias(libs.plugins.spring.dependency.management)
				}
				""");

		KtNameReferenceExpression at = PsiTreeUtil.collectElementsOfType(build, KtNameReferenceExpression.class).stream()
				.filter(r -> "management".equals(r.getReferencedName())).findFirst().orElseThrow();

		VersionUpgradeLookupService service = new VersionUpgradeLookupService(build.getProject(), build);
		var ar = service.resolveKotlinArtifactReference(at);

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
		PsiFile build = fixture.configureByText("build.gradle.kts", """
				dependencies {
				    implementation(libs.commons.lang3)
				}
				""");

		KtNameReferenceExpression at = PsiTreeUtil.collectElementsOfType(build, KtNameReferenceExpression.class).stream()
				.filter(r -> "lang3".equals(r.getReferencedName())).findFirst().orElseThrow();

		VersionUpgradeLookupService service = new VersionUpgradeLookupService(build.getProject(), build);
		var ar = service.resolveKotlinArtifactReference(at);

		assertThat(ar.isResolved()).isTrue();
		assertThat(ar.getArtifactId().groupId()).isEqualTo("org.apache.commons");
		assertThat(ar.getArtifactId().artifactId()).isEqualTo("commons-lang3");
		assertThat(ar.getDeclaration().getVersion().toString()).isEqualTo("3.19.0");
		assertThat(ar.getDeclaration().getVersionLiteral()).isInstanceOf(TomlLiteral.class);
		assertThat(TomlParser.getText((TomlLiteral) ar.getDeclaration().getVersionLiteral())).isEqualTo("3.19.0");
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
		PsiFile build = fixture.configureByText("build.gradle.kts", """
				plugins {
				    alias(libs.plugins.spring.dependency.management)
				}
				""");

		VersionUpgradeLookupService service = new VersionUpgradeLookupService(build.getProject(), build);

		int nonRedundantResolved = 0;
		for (PsiElement e : PsiTreeUtil.collectElementsOfType(build, PsiElement.class)) {
			if (KotlinDslUtils.isRedundantKotlinVersionHighlightAnchor(e)) {
				continue;
			}
			if (service.resolveKotlinArtifactReference(e).isResolved()) {
				nonRedundantResolved++;
			}
		}

		assertThat(nonRedundantResolved).isEqualTo(1);
	}
}
