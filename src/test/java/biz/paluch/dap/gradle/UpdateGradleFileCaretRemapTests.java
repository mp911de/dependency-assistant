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
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionCaretRemap;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.junit.jupiter.api.Test;
import org.toml.lang.psi.TomlLiteral;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests asserting the {@link VersionCaretRemap} returned by
 * {@link UpdateGradleFile#applyUpdate} lands the caret behind the new version
 * digits for each supported Gradle declaration shape.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateGradleFileCaretRemapTests {

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			springVersion=3.5.0
			lombokVersion=1.18.36
			""")
	void gradlePropertiesRemapLandsBehindDigits(PsiFile file) {

		PropertyValueImpl value = propertyValue(file, "3.5.0");
		int caret = caretInside(value);

		VersionCaretRemap remap = applyUpdate(file, value, "org.springframework", "spring-core", "3.6.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(file, remap.translate(caret))).endsWith("springVersion=3.6.0");
		assertThat(behindCaret(file, remap.translate(caret))).startsWith("\nlombokVersion");
	}

	@Test
	@ProjectFile(name = "gradle.properties", content = """
			springVersion=3.5.0
			lombokVersion=1.18.36
			""")
	void gradlePropertyImplRemapLandsBehindDigits(PsiFile file) {

		PropertyImpl property = property(file, "springVersion");
		int caret = file.getText().indexOf("3.5.0") + 1;

		VersionCaretRemap remap = applyUpdate(file, property, "org.springframework", "spring-core", "3.6.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(file, remap.translate(caret))).endsWith("springVersion=3.6.0");
		assertThat(behindCaret(file, remap.translate(caret))).startsWith("\nlombokVersion");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			dependencies {
			    implementation 'org.apache.commons:commons-lang3:3.19.0'
			}
			""")
	void groovyInlineVersionRemapLandsBehindDigits(PsiFile file) {

		GrLiteral literal = groovyLiteral(file, "'org.apache.commons:commons-lang3:3.19.0'");
		int caret = caretInside(literal);

		VersionCaretRemap remap = applyUpdate(file, literal, "org.apache.commons", "commons-lang3", "3.20.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(file, remap.translate(caret))).endsWith("commons-lang3:3.20.0");
		assertThat(behindCaret(file, remap.translate(caret))).startsWith("'");
	}

	@Test
	@ProjectFile(name = "build.gradle", content = """
			ext {
			    springVersion = '3.5.0'
			}

			dependencies {
			    implementation "org.springframework:spring-core:${springVersion}"
			}
			""")
	void groovyExtPropertyRemapLandsBehindDigits(PsiFile file) {

		GrLiteral literal = groovyLiteral(file, "'3.5.0'");
		int caret = caretInside(literal);

		VersionCaretRemap remap = applyUpdate(file, literal,
				propertyUpdate("org.springframework", "spring-core", "springVersion", "3.6.0"));

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(file, remap.translate(caret))).endsWith("springVersion = '3.6.0");
		assertThat(behindCaret(file, remap.translate(caret))).startsWith("'");
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			dependencies {
			    implementation("org.apache.commons:commons-lang3:3.19.0")
			}
			""")
	void kotlinInlineVersionRemapLandsBehindDigits(PsiFile file) {

		KtStringTemplateExpression template = kotlinLiteral(file, "\"org.apache.commons:commons-lang3:3.19.0\"");
		int caret = caretInside(template);

		VersionCaretRemap remap = applyUpdate(file, template, "org.apache.commons", "commons-lang3", "3.20.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(file, remap.translate(caret))).endsWith("commons-lang3:3.20.0");
		assertThat(behindCaret(file, remap.translate(caret))).startsWith("\"");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[versions]
			spring-boot = "3.5.0"
			commons-lang = "3.17.0"
			""")
	void catalogVersionsRemapLandsBehindDigits(PsiFile file) {

		TomlLiteral literal = tomlLiteral(file, "\"3.5.0\"");
		int caret = caretInside(literal);

		VersionCaretRemap remap = applyUpdate(file, literal, "org.springframework.boot", "spring-boot-starter",
				"3.6.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(file, remap.translate(caret))).endsWith("spring-boot = \"3.6.0");
		assertThat(behindCaret(file, remap.translate(caret))).startsWith("\"");
	}

	@Test
	@ProjectFile(name = "gradle/libs.versions.toml", content = """
			[libraries]
			spring-boot-starter = "org.springframework.boot:spring-boot-starter:3.5.0"
			""")
	void catalogInlineGavRemapLandsBehindDigits(PsiFile file) {

		TomlLiteral literal = tomlLiteral(file, "\"org.springframework.boot:spring-boot-starter:3.5.0\"");
		int caret = caretInside(literal);

		VersionCaretRemap remap = applyUpdate(file, literal, "org.springframework.boot", "spring-boot-starter",
				"3.6.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(file, remap.translate(caret))).endsWith("spring-boot-starter:3.6.0");
		assertThat(behindCaret(file, remap.translate(caret))).startsWith("\"");
	}

	private static TomlLiteral tomlLiteral(PsiFile file, String text) {
		for (TomlLiteral literal : PsiTreeUtil.findChildrenOfType(file, TomlLiteral.class)) {
			if (text.equals(literal.getText())) {
				return literal;
			}
		}
		throw new IllegalStateException("No TOML literal '%s' found".formatted(text));
	}

	@Test
	@ProjectFile(name = "build.gradle.kts", content = """
			val springVersion = "3.5.0"

			dependencies {
			    implementation("org.springframework:spring-core:$springVersion")
			}
			""")
	void kotlinInterpolatedVersionRemapLandsInOwningLiteral(PsiFile file) {

		KtStringTemplateExpression template = kotlinLiteral(file, "\"3.5.0\"");
		int caret = caretInside(template);

		VersionCaretRemap remap = applyUpdate(file, template, "org.springframework", "spring-core", "3.6.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(file, remap.translate(caret))).endsWith("val springVersion = \"3.6.0");
		assertThat(behindCaret(file, remap.translate(caret))).startsWith("\"");
	}

	private static KtStringTemplateExpression kotlinLiteral(PsiFile file, String text) {
		for (KtStringTemplateExpression template : PsiTreeUtil.findChildrenOfType(file,
				KtStringTemplateExpression.class)) {
			if (text.equals(template.getText())) {
				return template;
			}
		}
		throw new IllegalStateException("No Kotlin literal '%s' found".formatted(text));
	}

	private static GrLiteral groovyLiteral(PsiFile file, String text) {
		for (GrLiteral literal : PsiTreeUtil.findChildrenOfType(file, GrLiteral.class)) {
			if (text.equals(literal.getText())) {
				return literal;
			}
		}
		throw new IllegalStateException("No Groovy literal '%s' found".formatted(text));
	}

	private static PropertyValueImpl propertyValue(PsiFile file, String value) {
		for (PropertyValueImpl element : PsiTreeUtil.findChildrenOfType(file, PropertyValueImpl.class)) {
			if (value.equals(element.getText())) {
				return element;
			}
		}
		throw new IllegalStateException("No property value '%s' found".formatted(value));
	}

	private static PropertyImpl property(PsiFile file, String key) {
		for (PropertyImpl property : PsiTreeUtil.findChildrenOfType(file, PropertyImpl.class)) {
			if (key.equals(property.getUnescapedKey())) {
				return property;
			}
		}
		throw new IllegalStateException("No property '%s' found".formatted(key));
	}

	private static int caretInside(PsiElement element) {
		return element.getTextRange().getStartOffset() + 1;
	}

	private static String beforeCaret(PsiFile file, int offset) {
		return file.getText().substring(0, offset);
	}

	private static String behindCaret(PsiFile file, int offset) {
		return file.getText().substring(offset);
	}

	private static VersionCaretRemap applyUpdate(PsiFile file, PsiElement literal, String groupId, String artifactId,
			String toVersion) {

		return applyUpdate(file, literal, DependencyUpdate.create(ArtifactId.of(groupId, artifactId),
				ArtifactVersion.of(toVersion)));
	}

	private static VersionCaretRemap applyUpdate(PsiFile file, PsiElement literal, DependencyUpdate update) {
		return WriteCommandAction.writeCommandAction(file.getProject())
				.compute(() -> new UpdateGradleFile(file.getProject()).applyUpdate(literal, update));
	}

	private static DependencyUpdate propertyUpdate(String groupId, String artifactId, String propertyName,
			String toVersion) {

		Dependency dependency = new Dependency(ArtifactId.of(groupId, artifactId), ArtifactVersion.of(toVersion));
		dependency.addVersionSource(VersionSource.property(propertyName));
		return DependencyUpdate.from(dependency, ArtifactVersion.of(toVersion));
	}

}
