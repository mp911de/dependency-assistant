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

package biz.paluch.dap.npm;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionCaretRemap;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests asserting the {@link VersionCaretRemap} returned by
 * {@link UpdatePackageJsonFile#applyUpdate} lands the caret behind the new
 * version digits, after any range/alias operator and before the closing quote.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdatePackageJsonFileCaretRemapTests {

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "1.6.8"
			  }
			}
			""")
	void exactVersionRemapLandsBehindDigits(PsiFile packageJson) {

		JsonStringLiteral literal = stringLiteral(packageJson, "\"1.6.8\"");
		int caret = caretInside(literal);

		VersionCaretRemap remap = applyUpdate(packageJson, literal, "axios", "1.7.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(packageJson, remap.translate(caret))).endsWith("\"axios\": \"1.7.0");
		assertThat(behindCaret(packageJson, remap.translate(caret))).startsWith("\"");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "^3.1.2"
			  }
			}
			""")
	void caretModifierRemapLandsBehindDigitsAfterOperator(PsiFile packageJson) {

		JsonStringLiteral literal = stringLiteral(packageJson, "\"^3.1.2\"");
		int caret = caretInside(literal);

		VersionCaretRemap remap = applyUpdate(packageJson, literal, "axios", "3.4.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(packageJson, remap.translate(caret))).endsWith("\"axios\": \"^3.4.0");
		assertThat(behindCaret(packageJson, remap.translate(caret))).startsWith("\"");
	}

	private static JsonStringLiteral stringLiteral(PsiFile file, String text) {
		for (JsonStringLiteral literal : PsiTreeUtil.findChildrenOfType(file, JsonStringLiteral.class)) {
			if (text.equals(literal.getText())) {
				return literal;
			}
		}
		throw new IllegalStateException("No JSON string literal '%s' found".formatted(text));
	}

	private static int caretInside(JsonStringLiteral literal) {
		return literal.getTextRange().getStartOffset() + 1;
	}

	private static String beforeCaret(PsiFile file, int offset) {
		return file.getText().substring(0, offset);
	}

	private static String behindCaret(PsiFile file, int offset) {
		return file.getText().substring(offset);
	}

	private static VersionCaretRemap applyUpdate(PsiFile file, JsonStringLiteral literal, String id, String toVersion) {

		DependencyUpdate update = DependencyUpdate.create(ArtifactId.of(id, id), ArtifactVersion.of(toVersion));
		return WriteCommandAction.writeCommandAction(file.getProject())
				.compute(() -> new UpdatePackageJsonFile(file.getProject()).applyUpdate(literal, update));
	}

}
