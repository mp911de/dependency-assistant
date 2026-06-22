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

package biz.paluch.dap.github;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
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
 * {@link UpdateGitHubWorkflowFile#applyUpdate} lands the caret behind the
 * rewritten {@code @ref} version and never inside the managed pin comment.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class UpdateGitHubWorkflowFileCaretRemapTests {

	@BeforeEach
	void setUp(Project project) {
		GitHubFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@4.1.0 # here to stay
			""")
	void versionRefRemapLandsBehindRefNotComment(PsiFile workflowFile) {

		YAMLScalar scalar = usesScalar(workflowFile);
		int caret = caretInside(scalar);

		VersionCaretRemap remap = applyUpdate(workflowFile, scalar, "4.1.0", "4.2.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(workflowFile, remap.translate(caret))).endsWith("actions/checkout@4.2.0");
		assertThat(behindCaret(workflowFile, remap.translate(caret))).startsWith(" # here to stay");
	}

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			      - uses: actions/checkout@7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1 # replace
			""")
	void shaRefRemapLandsBehindShaNotManagedComment(PsiFile workflowFile) {

		YAMLScalar scalar = usesScalar(workflowFile);
		int caret = caretInside(scalar);

		VersionCaretRemap remap = applyUpdate(workflowFile, scalar, GitHubFixtures.SHA_V3, "v4.2.0");

		assertThat(remap.canTranslate()).isTrue();
		assertThat(beforeCaret(workflowFile, remap.translate(caret)))
				.endsWith("actions/checkout@" + GitHubFixtures.SHA_V4);
		assertThat(behindCaret(workflowFile, remap.translate(caret))).startsWith(" # v4.2.0");
	}

	private static YAMLScalar usesScalar(PsiFile file) {
		for (YAMLKeyValue keyValue : PsiTreeUtil.findChildrenOfType(file, YAMLKeyValue.class)) {
			if ("uses".equals(keyValue.getKeyText()) && keyValue.getValue() instanceof YAMLScalar scalar) {
				return scalar;
			}
		}
		throw new IllegalStateException("No uses: scalar found");
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

	private static VersionCaretRemap applyUpdate(PsiFile file, YAMLScalar scalar, String fromRef, String toTag) {

		ArtifactId id = ArtifactId.of("actions", "checkout");
		GitVersion targetVersion = GitVersion.of(GitHubFixtures.SHA_V4, ArtifactVersion.of(toTag));

		Dependency dependency = new Dependency(id, targetVersion);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(VersionSource.declared(fromRef));

		DependencyUpdate update = DependencyUpdate.from(dependency, targetVersion);
		return WriteCommandAction.writeCommandAction(file.getProject())
				.compute(() -> new UpdateGitHubWorkflowFile(file.getProject()).applyUpdate(scalar, update));
	}

}
