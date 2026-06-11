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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.gradle.GradleVersionSite.Absent;
import biz.paluch.dap.gradle.GradleVersionSite.BackingProperty;
import biz.paluch.dap.gradle.GradleVersionSite.DirectCoordinate;
import biz.paluch.dap.gradle.GradleVersionSite.MapLiteralVersion;
import biz.paluch.dap.gradle.GradleVersionSite.TomlCatalogAlias;
import biz.paluch.dap.support.DependencySite;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtElement;
import org.jetbrains.kotlin.psi.KtLiteralStringTemplateEntry;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.toml.lang.psi.TomlLiteral;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for {@link GradleVersionSite} implementations.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GradleVersionSiteTests {

	@BeforeEach
	void setUp(Project project) {
		GradleFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "build.gradle", content = "ext.springVersion = '6.2.0'")
	void backingPropertyDoesNotImplementDependencySite(PsiFile file) {

		PsiElement stub = file;
		BackingProperty site = new BackingProperty("springVersion", "6.2.0", stub, stub);

		assertThat((Object) site).isNotInstanceOf(DependencySite.class);
	}

	@Test
	@ProjectFile(name = "build.gradle", content = "ext.springVersion = '6.2.0'")
	void tomlCatalogAliasDoesNotImplementDependencySite(PsiFile file) {

		TomlCatalogAlias site = new TomlCatalogAlias(TomlReference.libs("spring.core"), file);

		assertThat((Object) site).isNotInstanceOf(DependencySite.class);
	}

	// -------------------------------------------------------------------------
	// Locator round-trip
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.springframework:spring-core:6.2.0'
			}
			""")
	void groovyDirectCoordinateIsClassified(PsiFile file) {

		GradleVersionSite site = locateGroovyLiteral(file, "'org.springframework:spring-core:6.2.0'");

		assertThat(site).isInstanceOf(DirectCoordinate.class);
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.springframework', name: 'spring-core', version: '6.2.0'
			}
			""")
	void groovyMapLiteralVersionIsClassified(PsiFile file) {

		GradleVersionSite site = locateGroovyLiteral(file, "'6.2.0'");

		assertThat(site).isInstanceOf(MapLiteralVersion.class);
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext.springVersion = '6.2.0'
			""")
	void groovyExtPropertyProducesBackingProperty(PsiFile file) {

		GradleVersionSite site = locateGroovyLiteral(file, "'6.2.0'");

		assertThat(site).isInstanceOf(BackingProperty.class);
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			plugins {
			    id 'org.springframework.boot' version '3.3.2'
			}
			""")
	void groovyPluginVersionIsClassified(PsiFile file) {

		GradleVersionSite site = locateGroovyLiteral(file, "'3.3.2'");

		assertThat(site.getDeclarationSource()).isEqualTo(DeclarationSource.plugin());
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.springframework:spring-core:6.2.0")
			}
			""")
	void kotlinDirectCoordinateIsClassified(PsiFile file) {

		GradleVersionSite site = locateKotlinLiteral(file, "\"org.springframework:spring-core:6.2.0\"");

		assertThat(site).isInstanceOf(DirectCoordinate.class);
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val springVersion = "6.2.0"
			""")
	void kotlinValPropertyProducesBackingProperty(PsiFile file) {

		GradleVersionSite site = locateKotlinLiteral(file, "\"6.2.0\"");

		assertThat(site).isInstanceOf(BackingProperty.class);
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[versions]
			spring = "6.2.0"
			""")
	void tomlVersionEntryProducesBackingProperty(PsiFile file) {

		GradleVersionSite site = locateTomlLiteral(file, "\"6.2.0\"");

		assertThat(site).isInstanceOf(BackingProperty.class);
	}

	@Test
	@ProjectFile(name = "libs.versions.toml", content = """
			[libraries]
			junit = { module = "junit", version = "4.13.2" }
			""")
	void tomlModuleWithoutGroupYieldsAbsentSite(PsiFile file) {

		GradleVersionSite site = locateTomlLiteral(file, "\"4.13.2\"");

		assertThat(site).isInstanceOf(Absent.class);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	private GradleVersionSite locateGroovyLiteral(PsiFile file, String literalText) {

		GrLiteral literal = PsiTreeUtil.findChildrenOfType(file, GrLiteral.class).stream()
				.filter(it -> literalText.equals(it.getText())).findFirst()
				.orElseThrow(() -> new IllegalStateException("No Groovy literal '" + literalText + "'"));

		return new GroovyVersionSiteLocator(GradlePropertyResolver.create(file)).locate(literal);
	}

	private GradleVersionSite locateKotlinLiteral(PsiFile file, String literalText) {

		KtStringTemplateExpression template = PsiTreeUtil
				.findChildrenOfType(file, KtStringTemplateExpression.class).stream()
				.filter(it -> literalText.equals(it.getText())).findFirst()
				.orElseThrow(() -> new IllegalStateException("No Kotlin literal '" + literalText + "'"));
		KtElement element = PsiTreeUtil.getChildOfType(template, KtLiteralStringTemplateEntry.class);
		if (element == null) {
			element = template;
		}

		return new KotlinVersionSiteLocator(GradlePropertyResolver.create(file), null).locate(element);
	}

	private GradleVersionSite locateTomlLiteral(PsiFile file, String literalText) {

		TomlLiteral literal = PsiTreeUtil.findChildrenOfType(file, TomlLiteral.class).stream()
				.filter(it -> literalText.equals(it.getText())).findFirst()
				.orElseThrow(() -> new IllegalStateException("No TOML literal '" + literalText + "'"));

		return new TomlVersionSiteLocator().locate(literal);
	}

}
