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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.toml.lang.psi.TomlLiteral;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for Gradle artifact-reference location.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GradleArtifactReferenceLocatorTests {

	@BeforeEach
	void setUp(Project project) {
		GradleFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.springframework:spring-core:6.2.0'
			}
			""")
	void resolvesGroovyDirectCoordinate(PsiFile file) {

		ArtifactDeclaration declaration = locateGroovyLiteral(file,
				"'org.springframework:spring-core:6.2.0'").getDeclaration();

		assertThat(declaration.getArtifactId()).isEqualTo(ArtifactId.of("org.springframework", "spring-core"));
		assertThat(declaration.getVersion().toString()).isEqualTo("6.2.0");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.springframework', name: 'spring-core', version: '6.2.0'
			}
			""")
	void resolvesGroovyMapNotation(PsiFile file) {

		ArtifactDeclaration declaration = locateGroovyLiteral(file, "'6.2.0'").getDeclaration();

		assertThat(declaration.getArtifactId()).isEqualTo(ArtifactId.of("org.springframework", "spring-core"));
		assertThat(declaration.getVersionLiteral().getText()).isEqualTo("'6.2.0'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id 'org.springframework.boot' version '3.3.2'
			}
			""")
	void resolvesGroovyPluginVersion(PsiFile file) {

		ArtifactDeclaration declaration = locateGroovyLiteral(file, "'3.3.2'").getDeclaration();

		assertThat(declaration.getDeclarationSource()).isEqualTo(DeclarationSource.plugin());
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation platform 'org.springframework.boot:spring-boot-dependencies:3.3.2'
			}
			""")
	void resolvesGroovyCommandPlatformAsBom(PsiFile file) {

		ArtifactDeclaration declaration = locateGroovyCommandPlatform(file).getDeclaration();

		assertThat(declaration.getArtifactId())
				.isEqualTo(ArtifactId.of("org.springframework.boot", "spring-boot-dependencies"));
		assertThat(declaration.getDeclarationSource()).isEqualTo(DeclarationSource.bom());
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.springframework:spring-core:6.2.0")
			}
			""")
	void resolvesKotlinDirectCoordinate(PsiFile file) {

		ArtifactDeclaration declaration = locateKotlinLiteral(file,
				"\"org.springframework:spring-core:6.2.0\"").getDeclaration();

		assertThat(declaration.getArtifactId()).isEqualTo(ArtifactId.of("org.springframework", "spring-core"));
		assertThat(declaration.getVersion().toString()).isEqualTo("6.2.0");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = "val springVersion = \"6.2.0\"")
	void unresolvedKotlinPropertyHasNoArtifactWithoutProjectState(PsiFile file) {
		assertThat(locateKotlinLiteral(file, "\"6.2.0\"").isResolved()).isFalse();
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[versions]
			spring = "6.2.0"
			""")
	void unresolvedTomlVersionHasNoArtifactWithoutProjectState(PsiFile file) {
		assertThat(locateTomlLiteral(file, "\"6.2.0\"").isResolved()).isFalse();
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[libraries]
			junit = { module = "junit", version = "4.13.2" }
			""")
	void rejectsTomlModuleWithoutGroup(PsiFile file) {
		assertThat(locateTomlLiteral(file, "\"4.13.2\"").isResolved()).isFalse();
	}

	private ArtifactReference locateGroovyLiteral(PsiFile file, String literalText) {

		GrLiteral literal = PsiTreeUtil.findChildrenOfType(file, GrLiteral.class).stream()
				.filter(it -> literalText.equals(it.getText())).findFirst()
				.orElseThrow(() -> new IllegalStateException("No Groovy literal '" + literalText + "'"));

		return new GroovyArtifactReferenceLocator(GradlePropertyResolver.create(file)).locate(literal);
	}

	private ArtifactReference locateGroovyCommandPlatform(PsiFile file) {

		for (GrMethodCall call : PsiTreeUtil.findChildrenOfType(file, GrMethodCall.class)) {
			PsiElement element = GroovyDeclarationStyleDetector.getInstance().findCommandPlatformString(call);
			if (element instanceof GroovyPsiElement groovyElement) {
				return new GroovyArtifactReferenceLocator(GradlePropertyResolver.create(file)).locate(groovyElement);
			}
		}

		throw new IllegalStateException("No Groovy command platform element");
	}

	private ArtifactReference locateKotlinLiteral(PsiFile file, String literalText) {

		KtStringTemplateExpression template = PsiTreeUtil
				.findChildrenOfType(file, KtStringTemplateExpression.class).stream()
				.filter(it -> literalText.equals(it.getText())).findFirst()
				.orElseThrow(() -> new IllegalStateException("No Kotlin literal '" + literalText + "'"));
		KtElement element = PsiTreeUtil.getChildOfType(template, KtLiteralStringTemplateEntry.class);
		if (element == null) {
			element = template;
		}

		return new KotlinArtifactReferenceLocator(GradlePropertyResolver.create(file),
				VersionCatalogRegistry.defaults(), null)
						.locate(element);
	}

	private ArtifactReference locateTomlLiteral(PsiFile file, String literalText) {

		TomlLiteral literal = PsiTreeUtil.findChildrenOfType(file, TomlLiteral.class).stream()
				.filter(it -> literalText.equals(it.getText())).findFirst()
				.orElseThrow(() -> new IllegalStateException("No TOML literal '" + literalText + "'"));

		return new TomlArtifactReferenceLocator(null).locate(literal);
	}

}
