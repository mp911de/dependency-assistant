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
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for Groovy DSL completion.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GroovyDslCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		GradleFixtures.setup(fixture.getProject());
	}

	// -------------------------------------------------------------------------
	// GAV style
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.<caret>'
			}
			""")
	void invokesAutoPopupForInlineGavVersion() {

		assertThat(invokeAutoPopup('3')).isTrue();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation platform('org.junit:junit-bom:6.0.<caret>')
			}
			""")
	void invokesAutoPopupForPlatformGavVersion() {
		assertThat(invokeAutoPopup('3')).isTrue();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.junit:junit-bom<caret>'
			}
			""")
	void invokesAutoPopupForCompactNotationColon() {

		assertThat(invokeAutoPopup(':')).isTrue();
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			println 'org.junit:junit-bom<caret>'
			""")
	void doesNotInvokeAutoPopupForUnrelatedColon() {

		assertThat(invokeAutoPopup(':')).isFalse();
	}

	// -------------------------------------------------------------------------
	// Plugins
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			plugins {
			    id 'org.springframework.boot' version '4.0.<caret>'
			}
			""")
	void completesPluginDslVersion(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("4.0.5");
	}

	// -------------------------------------------------------------------------
	// Map style
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: '6.0.<caret>'
			}
			""")
	void invokesAutoPopupForMapNotationLiteralVersion() {

		assertThat(invokeAutoPopup('3')).isTrue();
	}

	@Disabled("TODO")
	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: '6.0.<caret>'
			}
			""")
	void invokesAutoPopupForRegisteredVersionLiteral(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("6.0.3");
		// TODO .doesNotContain("6.1.0-M1");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(buildFile).containsText("version: '6.0.<caret>'");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			def junit = '6.0.<caret>'
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junit
			}
			""")
	void completesMapNotationPropertyBackedVersion(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("6.0.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(buildFile)
				.containsText("junit = '6.0.3'");
	}

	// -------------------------------------------------------------------------
	// Version block
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            prefer '6.0.<caret>'
			        }
			    }
			}
			""")
	void invokesAutoPopupForVersionBlockPreferLiteral() {

		assertThat(invokeAutoPopup('3')).isTrue();
	}

	@Disabled("TODO")
	@Test
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '6.0.<caret>'
			        }
			    }
			}
			""")
	void invokesAutoPopupForVersionBlockStrictlyLiteral(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("6.0.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(buildFile).containsText("strictly '6.0.3'");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			def junit = '6.0.<caret>'
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            prefer junit
			        }
			    }
			}
			""")
	void completesVersionBlockPropertyBackedPrefer(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			def junit = '6.0.<caret>'
			dependencies {
			    implementation('org.junit:junit-bom') {
			        version {
			            strictly '[5.0, 6.1['
			            prefer junit
			        }
			    }
			}
			""")
	void completesVersionVariable(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("6.0.3");
	}

	// -------------------------------------------------------------------------
	// Properties
	// -------------------------------------------------------------------------

	@Test
	@EditorFile(name = "build.gradle", content = """
			def message = '6.0.<caret>'
			dependencies {
			    implementation 'org.junit:junit-bom:6.0.0'
			}
			""")
	void doesNotCompleteOrdinaryStringLiteral(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).doesNotContain("6.0.3");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			def junit = '6.0<caret>'
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junit
			}
			""")
	void invokesAutoPopupForBackingVersionProperty(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.type('.');
		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("6.0.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(buildFile).containsText("junit = '6.0.3'");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			def message = '6.0.<caret>'
			println message
			""")
	void doesNotInvokeAutoPopupForUnrelatedVersionLiteral() {

		assertThat(invokeAutoPopup('3')).isFalse();
	}

	@EditorFile(name = "build.gradle", content = """
			ext {
			    junit = '<caret>'
			}
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junit
			}
			""")
	@Test
	void completesExtBlockProperty(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("6.0.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(buildFile).containsText("junit = '6.1.0-M1'");
	}

	@EditorFile(name = "build.gradle", content = """
			ext.junit = '<caret>'
			dependencies {
			    implementation group: 'org.junit', name: 'junit-bom', version: junit
			}
			""")
	@Test
	void completesExtDotProperty(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).contains("6.0.3", "6.1.0-M1");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(buildFile).containsText("ext.junit = '6.1.0-M1'");
	}

	@Test
	@EditorFile(name = "build.gradle", content = """
			ext {
			    set('junit', "<caret>")
			}
			dependencyManagement {
			    imports {
			        mavenBom "org.junit:junit-bom:${junit}"
			    }
			}
			""")
	void completesExtSetProperty(PsiFile buildFile) {

		GradleFixtures.analyze(buildFile);

		fixture.type('6');
		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings()).contains("6.0.3");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(buildFile).containsText("set('junit', \"6.1.0-M1\")");
	}

	// -------------------------------------------------------------------------
	// Version Catalog
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[libraries]
			junit = { module = "org.junit:junit-bom", version = "6.0.0" }
			""")
	@EditorFile(name = "build.gradle", content = """
			dependencies {
			    implementation libs.junit<caret>
			}
			""")
	void doesNotCompleteCatalogAccessorReference(PsiFile toml, @ProjectFile("build.gradle") PsiFile buildFile) {

		GradleFixtures.analyze(toml, buildFile);

		fixture.completeBasic();

		assertThat(fixture.getLookupElementStrings()).isEmpty();
	}

	private boolean invokeAutoPopup(char typeChar) {

		PsiElement position = fixture.getFile().findElementAt(fixture.getCaretOffset() - 1);
		return new GroovyCompletionContributor().invokeAutoPopup(position, typeChar);
	}

}
