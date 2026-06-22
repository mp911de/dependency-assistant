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

package biz.paluch.dap.antora;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.VersionCaretRemap;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level tests asserting the {@link VersionCaretRemap} returned by
 * {@link UpdateAntoraPlaybookFile#applyUpdate} lands the caret behind the
 * version segment of the release URL path.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateAntoraPlaybookFileCaretRemapTests {

	@BeforeEach
	void setUp(Project project) {
		AntoraFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "antora-playbook.yml", content = """
			ui:
			  bundle:
			    url: https://github.com/spring-io/antora-ui-spring/releases/download/v0.4.25/ui-bundle.zip
			""")
	void releaseUrlRemapLandsBehindVersionSegment(PsiFile playbookFile) {

		YAMLScalar scalar = bundleUrlScalar(playbookFile);
		int caret = caretInside(scalar);

		VersionCaretRemap remap = applyUpdate(playbookFile, scalar, "v0.4.26");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(playbookFile, remap.translate(caret))).endsWith("/releases/download/v0.4.26");
		assertThat(behindCaret(playbookFile, remap.translate(caret))).startsWith("/ui-bundle.zip");
	}

	private static YAMLScalar bundleUrlScalar(PsiFile file) {
		for (YAMLKeyValue keyValue : PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue.class)) {
			if (AntoraPlaybookParser.isBundleUrlKeyValue(keyValue)
					&& keyValue.getValue() instanceof YAMLScalar scalar) {
				return scalar;
			}
		}
		throw new IllegalStateException("No ui.bundle.url scalar found");
	}

	private static int caretInside(YAMLScalar scalar) {
		return scalar.getTextRange().getStartOffset() + 1;
	}

	private static String beforeCaret(PsiFile file, int offset) {
		return file.getText().substring(0, offset);
	}

	private static String behindCaret(PsiFile file, int offset) {
		return file.getText().substring(offset);
	}

	private static VersionCaretRemap applyUpdate(PsiFile file, YAMLScalar scalar, String toTag) {

		GitArtifactId id = GitArtifactId.of("github.com", "spring-io", "antora-ui-spring");
		GitVersion targetVersion = GitVersion.of(ArtifactVersion.of(toTag));

		Dependency dependency = new Dependency(id, targetVersion);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared("v0.4.25"));

		DependencyUpdate update = DependencyUpdate.from(dependency, targetVersion);
		return WriteCommandAction.writeCommandAction(file.getProject())
				.compute(() -> new UpdateAntoraPlaybookFile(file.getProject()).applyUpdate(scalar, update));
	}

}
