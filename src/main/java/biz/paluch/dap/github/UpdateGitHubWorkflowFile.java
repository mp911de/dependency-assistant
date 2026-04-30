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

import java.util.List;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.github.WorkflowUsesReference.VersionText;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;

/**
 * Applies selected version updates to a GitHub Actions workflow YAML file.
 *
 * <p>Updates only the ref segment after {@code @} in a {@code uses:}
 * declaration. The previously used style is preserved:
 * <ul>
 * <li>Version-style refs stay version-style.</li>
 * <li>SHA-style refs stay SHA-style and gain (or refresh) a trailing managed
 * version comment matching {@code #\s*v\d[\w.\-]*}.</li>
 * </ul>
 *
 * <p>An existing unrelated trailing comment (one that does not match the
 * managed-comment pattern) is preserved verbatim, including its original
 * whitespace and content.
 *
 * @author Mark Paluch
 */
class UpdateGitHubWorkflowFile {

	private static final Pattern MANAGED_COMMENT = Pattern.compile("\\s*#\\s*v?\\d+(\\.\\d+)*\\s*");

	private final Project project;

	/**
	 * Create an updater for the given project.
	 * @param project the IntelliJ project.
	 */
	UpdateGitHubWorkflowFile(Project project) {
		this.project = project;
	}

	/**
	 * Apply all updates to the given workflow file.
	 * @param psiFile the workflow PSI file.
	 * @param updates the updates to apply.
	 */
	void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {

		PsiDocumentManager documentManager = PsiDocumentManager.getInstance(project);
		Document document = documentManager.getDocument(psiFile);
		if (document == null) {
			return;
		}

		SyntaxTraverser.psiTraverser(psiFile).filter(YAMLKeyValue.class)
				.filter(it -> "uses".equals(it.getKeyText()))
				.map(YAMLKeyValue::getValue)
				.filter(YAMLScalar.class)
				.forEach(scalar -> {
					applyUpdates(document, updates, scalar);
					documentManager.commitDocument(document);
				});
	}

	private void applyUpdates(Document document, List<DependencyUpdate> updates, YAMLScalar scalar) {

		for (DependencyUpdate update : updates) {

			ArtifactId targetId = update.coordinate();
			ArtifactVersion targetVersion = update.version();
			WorkflowUsesReference ref = GitHubWorkflowParser.parseUsesValue(scalar.getTextValue());

			if (ref == null || !ref.toArtifactId().equals(targetId)
					|| !(targetVersion instanceof GitVersion gitVersion)) {
				continue;
			}

			applyUpdate(document, scalar, ref, gitVersion);
		}
	}

	private static void applyUpdate(Document document, YAMLScalar scalar, WorkflowUsesReference ref,
			GitVersion gitVersion) {

		String scalarText = scalar.getText();
		int atInScalar = scalarText.indexOf('@');
		if (atInScalar <= 0 || !StringUtils.hasText(ref.rawVersion())) {
			return;
		}

		TextRange scalarRange = scalar.getTextRange();
		int rawVersionStart = scalarRange.getStartOffset() + atInScalar + 1;
		int rawVersionEnd = rawVersionStart + ref.rawVersion().length();

		VersionText newVersion = ref.getVersion(gitVersion);
		document.replaceString(rawVersionStart, rawVersionEnd, newVersion.text());

		WorkflowRefStyle style = WorkflowRefStyle.from(ref.rawVersion());
		if (style != WorkflowRefStyle.SHA) {
			return;
		}

		int newScalarEnd = scalarRange.getEndOffset() + newVersion.text().length() - ref.rawVersion().length();
		updateManagedComment(document, newScalarEnd, gitVersion);
	}

	private static void updateManagedComment(Document document, int scalarEnd, GitVersion gitVersion) {

		int lineEnd = document.getLineEndOffset(document.getLineNumber(scalarEnd));
		String trailing = document.getText(new TextRange(scalarEnd, lineEnd));
		String managedComment = " # v" + gitVersion.getVersion();

		if (trailing.isEmpty()) {
			document.insertString(scalarEnd, managedComment);
			return;
		}

		if (MANAGED_COMMENT.matcher(trailing).matches()) {
			document.replaceString(scalarEnd, lineEnd, managedComment);
		}
	}

}
