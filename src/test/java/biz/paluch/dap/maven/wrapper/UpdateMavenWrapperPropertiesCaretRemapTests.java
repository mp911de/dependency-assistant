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

package biz.paluch.dap.maven.wrapper;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionCaretRemap;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests asserting the {@link VersionCaretRemap} returned by
 * {@link UpdateMavenWrapperProperties#applyUpdate} lands the caret behind the
 * new version digits, honoring the occurrence under the pre-edit caret.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UpdateMavenWrapperPropertiesCaretRemapTests {

	private static final ArtifactId MAVEN = ArtifactId.of("org.apache.maven", "apache-maven");

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			distributionSha256Sum=abc123
			""")
	void pathOccurrenceRemapLandsBehindDigits(PsiFile file) {

		PropertyImpl property = distributionUrl(file);
		int caret = pathVersionCaret(file, "3.9.6");

		VersionCaretRemap remap = applyUpdate(file, property, "3.9.9");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(file.getText().substring(0, remap.translate(caret))).endsWith("/3.9.9");
		assertThat(file.getText().substring(remap.translate(caret))).startsWith("/apache-maven-3.9.9-bin.zip");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			distributionSha256Sum=abc123
			""")
	void fileOccurrenceRemapLandsBehindDigits(PsiFile file) {

		PropertyImpl property = distributionUrl(file);
		int caret = fileVersionCaret(file, "3.9.6");

		VersionCaretRemap remap = applyUpdate(file, property, "3.9.9");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(file.getText().substring(0, remap.translate(caret))).endsWith("apache-maven-3.9.9");
		assertThat(file.getText().substring(remap.translate(caret))).startsWith("-bin.zip");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			distributionSha256Sum=abc123
			""")
	void fallsBackToFirstOccurrenceWhenCaretOutside(PsiFile file) {

		PropertyImpl property = distributionUrl(file);

		VersionCaretRemap remap = applyUpdate(file, property, "3.9.9");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(file.getText().substring(0, remap.translate(0))).endsWith("/3.9.9");
		assertThat(file.getText().substring(remap.translate(0))).startsWith("/apache-maven-3.9.9-bin.zip");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionSha256Sum=abc123
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void remapStaysCorrectWhenShaCommentOutShiftsTheUrlLine(PsiFile file) {

		PropertyImpl property = distributionUrl(file);
		int caret = pathVersionCaret(file, "3.9.6");

		VersionCaretRemap remap = applyUpdate(file, property, "3.9.9");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(file.getText().substring(0, remap.translate(caret))).endsWith("/3.9.9");
		assertThat(file.getText().substring(remap.translate(caret))).startsWith("/apache-maven-3.9.9-bin.zip");
		assertThat(file.getText()).contains("# distributionSha256Sum=abc123");
	}

	private static PropertyImpl distributionUrl(PsiFile file) {
		for (PropertyImpl property : PsiTreeUtil.findChildrenOfType(file, PropertyImpl.class)) {
			if ("distributionUrl".equals(property.getUnescapedKey())) {
				return property;
			}
		}
		throw new IllegalStateException("No distributionUrl property found");
	}

	private static int pathVersionCaret(PsiFile file, String version) {
		String text = file.getText();
		int slash = text.indexOf("/" + version + "/");
		return slash + 1 + version.length();
	}

	private static int fileVersionCaret(PsiFile file, String version) {
		String text = file.getText();
		int marker = text.indexOf("apache-maven-" + version + "-bin");
		return marker + "apache-maven-".length() + version.length();
	}

	private static VersionCaretRemap applyUpdate(PsiFile file, PropertyImpl property, String toVersion) {

		DependencyUpdate update = DependencyUpdate.create(MAVEN, ArtifactVersion.of(toVersion));
		return WriteCommandAction.writeCommandAction(file.getProject())
				.compute(() -> UpdateMavenWrapperProperties.applyUpdate(property, update));
	}

}
