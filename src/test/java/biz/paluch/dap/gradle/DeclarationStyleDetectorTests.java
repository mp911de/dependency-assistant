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

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.gradle.DeclarationStyle.Kind;
import com.intellij.psi.PsiElement;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for the declaration-style detection exposed by
 * {@link DeclarationStyleDetector}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class DeclarationStyleDetectorTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.<caret>0'
			}
			""")
	void groovyInlineNotation() {

		DeclarationStyle site = detect(GroovyDeclarationStyleDetector.getInstance());

		assertThat(site.kind()).isEqualTo(Kind.INLINE_NOTATION);
		assertThat(site.owningCall()).containsText("implementation");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: '6.0.<caret>0'
			}
			""")
	void groovyNamedArgument() {
		assertThat(detect(GroovyDeclarationStyleDetector.getInstance()).kind()).isEqualTo(Kind.NAMED_ARGUMENT);
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version { prefer '6.0.<caret>0' }
			    }
			}
			""")
	void groovyVersionBlockPrefer() {
		assertThat(detect(GroovyDeclarationStyleDetector.getInstance()).kind()).isEqualTo(Kind.VERSION_BLOCK_PREFER);
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			plugins {
			    id 'org.springframework.boot' version '4.0.<caret>3'
			}
			""")
	void groovyPluginVersion() {

		DeclarationStyle site = detect(GroovyDeclarationStyleDetector.getInstance());

		assertThat(site.kind()).isEqualTo(Kind.PLUGIN_VERSION);
		assertThat(site.owningCall()).containsText("id 'org.springframework.boot'");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext.junit = '6.0.<caret>0'
			dependencies {
			    implementation "org.junit:junit-bom:$junit"
			}
			""")
	void groovyBackingProperty() {

		DeclarationStyle site = detect(GroovyDeclarationStyleDetector.getInstance());

		assertThat(site.kind()).isEqualTo(Kind.BACKING_PROPERTY);
		assertThat(site.owningCall()).isNull();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation project(':some<caret>thing')
			}
			""")
	void groovyNonVersionStringIsAbsent() {

		DeclarationStyle site = GroovyDeclarationStyleDetector.getInstance()
				.detect(fixture.getFile().findElementAt(fixture.getCaretOffset()));

		assertThat(site.isAbsent()).isTrue();
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom:6.0.<caret>0")
			}
			""")
	void kotlinInlineNotation() {

		DeclarationStyle site = detect(KotlinDeclarationStyleDetector.getInstance());

		assertThat(site.kind()).isEqualTo(Kind.INLINE_NOTATION);
		assertThat(site.owningCall()).containsText("implementation");
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation(group = "org.junit", name = "junit-bom", version = "6.0.<caret>0")
			}
			""")
	void kotlinNamedArgument() {
		assertThat(detect(KotlinDeclarationStyleDetector.getInstance()).kind()).isEqualTo(Kind.NAMED_ARGUMENT);
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.junit:junit-bom") {
			        version { strictly("6.0.<caret>0") }
			    }
			}
			""")
	void kotlinVersionBlockStrictly() {
		assertThat(detect(KotlinDeclarationStyleDetector.getInstance()).kind()).isEqualTo(Kind.VERSION_BLOCK_STRICTLY);
	}

	@Test
	@EditorFile(name = "build.gradle.kts", content = """
			plugins {
			    id("org.springframework.boot") version "4.0.<caret>3"
			}
			""")
	void kotlinPluginVersion() {

		DeclarationStyle site = detect(KotlinDeclarationStyleDetector.getInstance());

		assertThat(site.kind()).isEqualTo(Kind.PLUGIN_VERSION);
		assertThat(site.owningCall()).containsText("org.springframework.boot");
	}

	private DeclarationStyle detect(DeclarationStyleDetector detector) {

		PsiElement element = fixture.getFile().findElementAt(fixture.getCaretOffset());
		assertThat(element).isNotNull();

		DeclarationStyle site = detector.detect(element);
		assertThat(site.isPresent()).isTrue();
		return site;
	}

}
