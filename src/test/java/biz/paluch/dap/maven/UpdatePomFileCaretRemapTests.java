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
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests asserting the {@link VersionCaretRemap} returned by
 * {@link UpdatePomFile#applyUpdate} lands the caret behind the new version
 * digits.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdatePomFileCaretRemapTests {

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>3.19.0</version>
					</dependency>
				</dependencies>
			</project>
			""")
	void inlineVersionRemapLandsBehindDigits(PsiFile pom) {

		XmlTag versionTag = dependencyVersionTag(pom, "3.19.0");
		int caret = caretInside(versionTag);

		VersionCaretRemap remap = applyUpdate(pom, versionTag,
				ArtifactId.of("org.apache.commons", "commons-lang3"), "3.20.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(pom, remap.translate(caret))).endsWith(">3.20.0");
		assertThat(behindCaret(pom, remap.translate(caret))).startsWith("</version>");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<?xml version="1.0" encoding="UTF-8"?>
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<properties>
					<spring.version>6.1.0</spring.version>
				</properties>
			</project>
			""")
	void versionPropertyRemapLandsBehindDigits(PsiFile pom) {

		XmlTag propertyTag = propertyTag(pom, "spring.version", "6.1.0");
		int caret = caretInside(propertyTag);

		VersionCaretRemap remap = applyUpdate(pom, propertyTag,
				ArtifactId.of("org.springframework", "spring-core"), "6.2.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(pom, remap.translate(caret))).endsWith(">6.2.0");
		assertThat(behindCaret(pom, remap.translate(caret))).startsWith("</spring.version>");
	}

	private static XmlTag dependencyVersionTag(PsiFile pom, String version) {
		for (XmlTag tag : PsiTreeUtil.findChildrenOfType(pom, XmlTag.class)) {
			if ("version".equals(tag.getName()) && version.equals(tag.getValue().getText())) {
				return tag;
			}
		}
		throw new IllegalStateException("No <version>%s</version> found".formatted(version));
	}

	private static XmlTag propertyTag(PsiFile pom, String name, String value) {
		for (XmlTag tag : PsiTreeUtil.findChildrenOfType(pom, XmlTag.class)) {
			if (name.equals(tag.getName()) && value.equals(tag.getValue().getText())) {
				return tag;
			}
		}
		throw new IllegalStateException("No <%s>%s</%s> found".formatted(name, value, name));
	}

	private static int caretInside(XmlTag versionTag) {
		return versionTag.getValue().getTextRange().getStartOffset() + 1;
	}

	private static String behindCaret(PsiFile file, int offset) {
		return file.getText().substring(offset);
	}

	private static String beforeCaret(PsiFile file, int offset) {
		return file.getText().substring(0, offset);
	}

	private static VersionCaretRemap applyUpdate(PsiFile pom, XmlTag versionTag, ArtifactId artifactId,
			String toVersion) {

		DependencyUpdate update = DependencyUpdate.create(artifactId, ArtifactVersion.of(toVersion));
		return WriteCommandAction.writeCommandAction(pom.getProject())
				.compute(() -> new UpdatePomFile(PropertyResolver.empty()).applyUpdate(versionTag, update));
	}

}
