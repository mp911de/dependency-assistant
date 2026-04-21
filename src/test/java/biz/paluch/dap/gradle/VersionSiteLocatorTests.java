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
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.toml.lang.psi.TomlLiteral;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link GroovyVersionSiteLocator} and
 * {@link KotlinVersionSiteLocator}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class VersionSiteLocatorTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.0'
			}
			""")
	void groovyLiteralVersionLocatesDependencySite(PsiFile buildFile) {

		GroovyVersionSiteLocator locator = new GroovyVersionSiteLocator(GradlePropertyResolver.create(buildFile),
				VersionCatalogRegistry.from(buildFile));
		GrLiteral literal = SyntaxTraverser.psiTraverser(buildFile)
				.filter(GrLiteral.class)
				.filter(it -> "'org.junit:junit-bom:6.0.0'".equals(it.getText()))
				.first();

		GradleLookupSite site = locator.locate(literal);

		assertThat(site).isInstanceOf(GradleLookupSite.GradleVersionSite.class);
		GradleLookupSite.GradleVersionSite versionSite = (GradleLookupSite.GradleVersionSite) site;
		assertThat(versionSite.dependency().getId()).isEqualTo(ArtifactId.of("org.junit", "junit-bom"));
		assertThat(versionSite.declarationElement())
				.isInstanceOf(org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall.class);
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext.junit = '6.0.0'
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junit
			}
			""")
	void groovyVariableReferenceLocatesPropertySiteWithArtifactContext(PsiFile buildFile) {

		GroovyVersionSiteLocator locator = new GroovyVersionSiteLocator(GradlePropertyResolver.create(buildFile),
				VersionCatalogRegistry.from(buildFile));
		GrReferenceExpression versionReference = SyntaxTraverser.psiTraverser(buildFile)
				.filter(GrReferenceExpression.class)
				.filter(it -> "junit".equals(it.getReferenceName()) && it
						.getParent() instanceof org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument)
				.first();

		GradleLookupSite site = locator.locate(versionReference);

		assertThat(site).isInstanceOf(GradlePropertySite.class);
		GradlePropertySite propertySite = (GradlePropertySite) site;
		assertThat(propertySite.propertyName()).isEqualTo("junit");
		assertThat(propertySite.artifactId()).isEqualTo(ArtifactId.of("org.junit", "junit-bom"));
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.slf4j:slf4j-api") {
			        version {
			            prefer("1.7.25")
			        }
			    }
			}
			""")
	void kotlinVersionBlockDeclarationLocatesDeclarationSite(PsiFile buildFile) {

		KotlinVersionSiteLocator locator = new KotlinVersionSiteLocator(GradlePropertyResolver.create(buildFile));
		KtCallElement call = SyntaxTraverser.psiTraverser(buildFile)
				.filter(KtCallElement.class)
				.filter(it -> "implementation".equals(KotlinDslUtils.getKotlinCallName(it)))
				.first();

		GradleDeclarationSite site = locator.locateDeclaration(call);

		assertThat(site).isNotNull();
		assertThat(site.artifactId()).isEqualTo(ArtifactId.of("org.slf4j", "slf4j-api"));
		assertThat(site.versionSource()).isEqualTo(VersionSource.declared("1.7.25"));
		assertThat(site.versionElement().getText()).isEqualTo("\"1.7.25\"");
	}

	@Test
	@EditorFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			spring-boot = "3.5.0"
			""")
	void tomlVersionsLiteralLocatesPropertySite(PsiFile tomlFile) {

		TomlVersionSiteLocator locator = new TomlVersionSiteLocator();
		TomlLiteral literal = SyntaxTraverser.psiTraverser(tomlFile)
				.filter(TomlLiteral.class)
				.filter(it -> "\"3.5.0\"".equals(it.getText()))
				.first();

		GradleLookupSite site = locator.locate(literal);

		assertThat(site).isInstanceOf(GradlePropertySite.class);
		GradlePropertySite propertySite = (GradlePropertySite) site;
		assertThat(propertySite.propertyName()).isEqualTo("spring-boot");
		assertThat(propertySite.version()).isEqualTo("3.5.0");
	}

}
