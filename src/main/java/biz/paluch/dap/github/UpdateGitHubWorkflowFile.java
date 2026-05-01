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
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.github.WorkflowUsesReference.VersionText;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.PsiParserFacade;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.yaml.YAMLFileType;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

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
 * <p>An existing unrelated trailing comment is preserved behind the managed
 * version comment.
 *
 * @author Mark Paluch
 */
class UpdateGitHubWorkflowFile {

	private static final Pattern MANAGED_COMMENT = Pattern.compile("#\\s*v?\\d[\\w.-]*\\s*");

	private static final Pattern REPLACE_COMMENT = Pattern.compile("#\\s*replace(?:\\s+me)?\\s*");


	/**
	 * Apply all updates to the given workflow file.
	 * @param psiFile the workflow PSI file.
	 * @param updates the updates to apply.
	 */
	void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {

		SyntaxTraverser.psiTraverser(psiFile).filter(YAMLKeyValue.class)
				.filter(it -> "uses".equals(it.getKeyText()))
				.map(YAMLKeyValue::getValue)
				.filter(YAMLScalar.class)
				.filter(YAMLScalar::isValid)
				.forEach(it -> {
					applyUpdates(updates, it);
				});
	}

	private void applyUpdates(List<DependencyUpdate> updates, YAMLScalar scalar) {

		for (DependencyUpdate update : updates) {

			ArtifactId targetId = update.coordinate();
			ArtifactVersion targetVersion = update.version();
			WorkflowUsesReference ref = GitHubWorkflowParser.parseUsesValue(scalar.getTextValue());

			if (ref == null || !ref.toArtifactId().equals(targetId)
					|| !(targetVersion instanceof GitVersion gitVersion)) {
				continue;
			}

			applyUpdate(scalar, ref, gitVersion);
			return;
		}
	}

	private static void applyUpdate(YAMLScalar scalar, WorkflowUsesReference ref, GitVersion gitVersion) {

		if (!StringUtils.hasText(ref.rawVersion())) {
			return;
		}

		VersionText newVersion = ref.getVersion(gitVersion);
		YAMLScalar updated = updateRef(scalar, newVersion.text());
		if (updated == null) {
			return;
		}

		WorkflowRefStyle style = WorkflowRefStyle.from(ref.rawVersion());
		if (style != WorkflowRefStyle.SHA) {
			return;
		}

		ensureVersionComment(updated, managedCommentVersion(gitVersion));
	}

	private static String managedCommentVersion(GitVersion gitVersion) {

		String version = gitVersion.getVersion().toString();
		return version.startsWith("v") || version.startsWith("V") ? version : "v" + version;
	}

	static @Nullable YAMLScalar updateRef(YAMLScalar scalar, String newRef) {

		String value = scalar.getTextValue();
		int refSeparator = value.indexOf('@');
		if (refSeparator < 0) {
			return null;
		}

		if (!(scalar.getParent() instanceof YAMLKeyValue keyValue)) {
			return null;
		}

		YAMLKeyValue newKeyValue = createKeyValue(scalar, value.substring(0, refSeparator + 1) + newRef);
		if (newKeyValue == null) {
			return null;
		}

		PsiElement updated = keyValue.replace(newKeyValue);
		if (updated instanceof YAMLKeyValue updatedKeyValue
				&& updatedKeyValue.getValue() instanceof YAMLScalar updatedScalar) {
			return updatedScalar;
		}

		return null;
	}

	static void ensureVersionComment(YAMLScalar scalar, String commentText) {

		if (!scalar.isValid() || !StringUtils.hasText(commentText)) {
			return;
		}

		String managedComment = "# " + commentText;
		PsiComment comment = findTrailingComment(scalar);
		if (comment == null) {
			addTrailingComment(scalar, managedComment);
			return;
		}

		normalizeWhitespaceBefore(comment);
		PsiComment replacement = createComment(scalar, mergeComment(comment.getText(), managedComment));
		if (replacement != null) {
			comment.replace(replacement);
		}
	}

	static int getValueEndOffset(YAMLScalar scalar) {

		String text = scalar.getText();
		int endOffsetInScalar = text.length();
		if (text.length() >= 2) {
			char first = text.charAt(0);
			if ((first == '"' || first == '\'') && text.charAt(text.length() - 1) == first) {
				endOffsetInScalar--;
			}
		}

		return scalar.getTextRange().getStartOffset() + endOffsetInScalar;
	}

	private static @Nullable YAMLKeyValue createKeyValue(YAMLScalar original, String value) {

		PsiFile file = PsiFileFactory.getInstance(original.getProject())
				.createFileFromText("dependency-assistant.yml", YAMLFileType.YML,
						"uses: " + renderScalar(original, value));
		return SyntaxTraverser.psiTraverser(file)
				.filter(YAMLKeyValue.class)
				.first();
	}

	private static String renderScalar(YAMLScalar original, String value) {

		String text = original.getText();
		if (text.length() < 2) {
			return value;
		}

		char quote = text.charAt(0);
		if (quote == '\'') {
			return "'" + value.replace("'", "''") + "'";
		}
		if (quote == '"') {
			return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
		}

		return value;
	}

	private static void addTrailingComment(YAMLScalar scalar, String commentText) {

		PsiElement anchor = getTrailingAnchor(scalar);
		PsiElement parent = anchor.getParent();
		if (parent == null) {
			return;
		}

		PsiComment comment = createComment(scalar, commentText);
		if (comment == null) {
			return;
		}

		PsiParserFacade parserFacade = PsiParserFacade.getInstance(scalar.getProject());
		PsiElement whitespace = parent.addAfter(parserFacade.createWhiteSpaceFromText(" "), anchor);
		parent.addAfter(comment, whitespace);
	}

	private static void normalizeWhitespaceBefore(PsiComment comment) {

		PsiElement previous = comment.getPrevSibling();
		if (!(previous instanceof PsiWhiteSpace whitespace) || whitespace.getText().contains("\n")) {
			return;
		}

		PsiParserFacade parserFacade = PsiParserFacade.getInstance(comment.getProject());
		whitespace.replace(parserFacade.createWhiteSpaceFromText(" "));
	}

	private static String mergeComment(String existingComment, String managedComment) {

		String trimmed = existingComment.trim();
		int nextComment = trimmed.indexOf('#', 1);
		String firstComment = nextComment >= 0 ? trimmed.substring(0, nextComment).trim() : trimmed;
		String remaining = nextComment >= 0 ? " " + trimmed.substring(nextComment).trim() : "";

		if (MANAGED_COMMENT.matcher(firstComment).matches() || REPLACE_COMMENT.matcher(firstComment).matches()) {
			return managedComment + remaining;
		}

		return managedComment + " " + trimmed;
	}

	private static @Nullable PsiComment createComment(PsiElement context, String text) {
		String commentText = text.trim();
		if (!commentText.startsWith("#")) {
			commentText = "# " + commentText;
		}

		PsiFile file = PsiFileFactory.getInstance(context.getProject())
				.createFileFromText("dependency-assistant-comment.yml", YAMLFileType.YML, commentText);
		return SyntaxTraverser.psiTraverser(file)
				.filter(PsiComment.class)
				.first();
	}

	private static @Nullable PsiComment findTrailingComment(YAMLScalar scalar) {

		PsiElement sibling = getTrailingAnchor(scalar).getNextSibling();
		while (sibling != null) {
			if (sibling instanceof PsiWhiteSpace whitespace) {
				if (whitespace.getText().contains("\n")) {
					return null;
				}
				sibling = sibling.getNextSibling();
				continue;
			}
			if (sibling instanceof PsiComment comment) {
				return comment;
			}
			return null;
		}

		return null;
	}

	private static PsiElement getTrailingAnchor(YAMLScalar scalar) {
		return scalar.getParent() instanceof YAMLKeyValue keyValue ? keyValue : scalar;
	}

}
