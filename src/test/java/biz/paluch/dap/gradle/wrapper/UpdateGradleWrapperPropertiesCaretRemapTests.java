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

package biz.paluch.dap.gradle.wrapper;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionCaretRemap;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests asserting the {@link VersionCaretRemap} returned by
 * {@link UpdateGradleWrapperProperties#applyUpdate} lands the caret behind the
 * new version digits, after the SHA post-process.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateGradleWrapperPropertiesCaretRemapTests {

	private static final ArtifactId GRADLE = ArtifactId.of("org.gradle", "gradle");

	@BeforeEach
	void setUp(Project project) {
		GradleWrapperFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void distributionUrlRemapLandsBehindDigits(PsiFile file) {

		PropertyImpl property = distributionUrl(file);
		int caret = versionCaret(file, "8.14.3");

		VersionCaretRemap remap = applyUpdate(file, property, "9.5.1");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(file.getText().substring(0, remap.translate(caret))).endsWith("gradle-9.5.1");
		assertThat(file.getText().substring(remap.translate(caret))).startsWith("-bin.zip");
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void fallsBackToFirstOccurrenceWhenCaretOutside(PsiFile file) {

		PropertyImpl property = distributionUrl(file);

		VersionCaretRemap remap = applyUpdate(file, property, "9.5.1");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(file.getText().substring(0, remap.translate(0))).endsWith("gradle-9.5.1");
		assertThat(file.getText().substring(remap.translate(0))).startsWith("-bin.zip");
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionSha256Sum=old
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void remapStaysCorrectWhenShaCommentOutShiftsTheUrlLine(PsiFile file) {

		PropertyImpl property = distributionUrl(file);
		int caret = versionCaret(file, "8.14.3");

		VersionCaretRemap remap = applyUpdate(file, property, "9.6.0-rc-1");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(file.getText().substring(0, remap.translate(caret))).endsWith("gradle-9.6.0-rc-1");
		assertThat(file.getText().substring(remap.translate(caret))).startsWith("-bin.zip");
		assertThat(file.getText()).contains("# distributionSha256Sum=old");
	}

	private static PropertyImpl distributionUrl(PsiFile file) {
		for (PropertyImpl property : PsiTreeUtil.findChildrenOfType(file, PropertyImpl.class)) {
			if ("distributionUrl".equals(property.getUnescapedKey())) {
				return property;
			}
		}
		throw new IllegalStateException("No distributionUrl property found");
	}

	private static int versionCaret(PsiFile file, String version) {
		String text = file.getText();
		int marker = text.indexOf("gradle-" + version + "-");
		return marker + "gradle-".length() + version.length();
	}

	private static VersionCaretRemap applyUpdate(PsiFile file, PropertyImpl property, String toVersion) {

		DependencyUpdate update = DependencyUpdate.create(GRADLE, ArtifactVersion.of(toVersion));
		return WriteCommandAction.writeCommandAction(file.getProject())
				.compute(() -> UpdateGradleWrapperProperties.applyUpdate(property, update));
	}

}
