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

package biz.paluch.dap.maven;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionCaretRemap;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests asserting the {@link VersionCaretRemap} returned by
 * {@link UpdateExtensionsFile#applyUpdate} lands the caret behind the new
 * version digits.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateExtensionsFileCaretRemapTests {

	@Test
	@ProjectFile(name = "extensions.xml", content = """
				<extensions>
					<extension>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>3.19.0</version>
					</extension>
				</extensions>
			""")
	void inlineVersionRemapLandsBehindDigits(PsiFile file) {

		XmlTag versionTag = versionTag(file, "3.19.0");
		int caret = versionTag.getValue().getTextRange().getStartOffset() + 1;

		DependencyUpdate update = DependencyUpdate.create(ArtifactId.of("org.apache.commons", "commons-lang3"),
				ArtifactVersion.of("3.20.0"));
		VersionCaretRemap remap = WriteCommandAction.writeCommandAction(file.getProject())
				.compute(() -> new UpdateExtensionsFile().applyUpdate(versionTag, update));

		assertThat(remap.canTranslate()).isTrue();
		assertThat(file.getText().substring(0, remap.translate(caret))).endsWith(">3.20.0");
		assertThat(file.getText().substring(remap.translate(caret))).startsWith("</version>");
	}

	private static XmlTag versionTag(PsiFile file, String version) {
		for (XmlTag tag : PsiTreeUtil.findChildrenOfType(file, XmlTag.class)) {
			if ("version".equals(tag.getName()) && version.equals(tag.getValue().getText())) {
				return tag;
			}
		}
		throw new IllegalStateException("No <version>%s</version> found".formatted(version));
	}

}
