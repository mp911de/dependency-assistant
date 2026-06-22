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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.github.UsesRepositoryAction.VersionText;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.yaml.YamlVersionSite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * PSI updater for GitHub Actions workflow {@code uses:} declarations.
 *
 * <p>This updater applies dependency updates preserving surrounding formatting.
 *
 * The existing pinning style is part of the contract. Version refs are rendered
 * as version refs. SHA-pinned refs are rendered as SHAs when the selected
 * release has SHA metadata, with a managed explanatory version comment. This
 * keeps workflows reproducible while still exposing semantic release
 * information to the user.
 *
 * @author Mark Paluch
 * @see UsesRepositoryAction
 * @see RefStyle
 */
class UpdateGitHubWorkflowFile {

	private final YAMLElementGenerator factory;

	public UpdateGitHubWorkflowFile(Project project) {
		this.factory = new YAMLElementGenerator(project);
	}

	/**
	 * Apply matching GitHub Action updates to the given GitHub Actions YAML file.
	 * <p>Only the {@code uses:} value and its managed version comment are changed.
	 * Declarations without a matching update are left as-is.
	 * @param psiFile the GitHub Actions YAML PSI file
	 * @param updates the dependency updates to apply
	 */
	public void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates) {

		SyntaxTraverser.psiTraverser(psiFile).filter(YAMLKeyValue.class)
				.filter(it -> "uses".equals(it.getKeyText()))
				.map(YAMLKeyValue::getValue)
				.filter(YAMLScalar.class)
				.filter(YAMLScalar::isValid)
				.forEach(it -> applyUpdates(updates, it));
	}

	private void applyUpdates(List<DependencyUpdate> updates, YAMLScalar scalar) {

		UsesRepositoryAction ref = GitHubWorkflowParser.parseUses(scalar.getTextValue());
		if (ref == null || !StringUtils.hasText(ref.version())) {
			return;
		}

		ArtifactId artifactId = ref.toArtifactId();

		for (DependencyUpdate update : updates) {

			if (!artifactId.equals(update.coordinate()) || !(update.version() instanceof GitVersion gitVersion)) {
				continue;
			}

			updateVersionAndComment(scalar, ref.getVersion(gitVersion));
		}
	}

	/**
	 * Apply a single update at the given YAML scalar anchor of a {@code uses:} key.
	 */
	public void applyUpdate(YAMLScalar scalar, DependencyUpdate update) {

		UsesRepositoryAction ref = GitHubWorkflowParser.parseUses(scalar.getTextValue());
		if (ref == null || !StringUtils.hasText(ref.version())) {
			return;
		}

		if (!(update.version() instanceof GitVersion gitVersion)) {
			return;
		}

		updateVersionAndComment(scalar, ref.getVersion(gitVersion));
	}

	/**
	 * Update a workflow {@code uses:} scalar with the given rendered version text.
	 * <p>The method returns the replacement scalar so callers can continue PSI
	 * operations, for example to position the editor caret after completion
	 * insertion. A {@literal null} result indicates that the scalar is not in a
	 * writable {@code uses:} key-value context.
	 * @param scalar the scalar containing a repository-backed {@code uses:} value
	 * @param versionText the ref text and optional managed comment to render
	 * @return the updated scalar, or {@literal null} if no safe update could be
	 * made
	 */
	public @Nullable YAMLScalar updateVersionAndComment(YAMLScalar scalar, VersionText versionText) {

		YamlVersionSite site = YamlVersionSite.locate(scalar, kv -> "uses".equals(kv.getKeyText()));
		if (site == null) {
			return null;
		}

		String value = site.rawValue();
		int refSeparator = value.indexOf('@');
		if (refSeparator < 0) {
			return null;
		}

		YAMLKeyValue replaced = site.replaceRawValue(value.substring(0, refSeparator + 1) + versionText.text(),
				factory);
		YAMLScalar updatedScalar = (YAMLScalar) replaced.getValue();
		if (updatedScalar == null || !versionText.hasComment()) {
			return updatedScalar;
		}

		PsiComment comment = findTrailingComment(updatedScalar);
		if (comment != null) {
			comment.replace(createManagedComment(versionText, comment.getText()));
		} else {
			insertManagedComment(replaced, versionText);
		}

		return updatedScalar;
	}

	/**
	 * Insert the managed version comment behind the given key-value, adding the
	 * separating whitespace and the comment as a single sibling range.
	 */
	private void insertManagedComment(YAMLKeyValue keyValue, VersionText versionText) {

		PsiFile dummy = factory.createDummyYamlWithText("key: value # " + versionText.comment());
		PsiComment comment = PsiTreeUtil.findChildOfType(dummy, PsiComment.class);
		if (comment == null) {
			return;
		}

		PsiElement whitespace = comment.getPrevSibling();
		PsiElement first = whitespace instanceof PsiWhiteSpace ? whitespace : comment;
		keyValue.getParent().addRangeAfter(first, comment, keyValue);
	}

	/**
	 * Render the managed version comment, retaining any second {@code #} segment of
	 * the existing comment as unmanaged trailing content.
	 */
	private PsiElement createManagedComment(VersionText versionText, String existingComment) {

		int trailingComment = existingComment.indexOf('#', 1);
		String trailingContent = trailingComment != -1 ? " " + existingComment.substring(trailingComment) : "";

		return factory.createDummyYamlWithText("# " + versionText.comment() + trailingContent).getFirstChild();
	}

	private static @Nullable PsiComment findTrailingComment(YAMLScalar scalar) {

		int scalarEnd = scalar.getTextRange().getEndOffset();
		CharSequence contents = scalar.getContainingFile().getViewProvider().getContents();
		int newline = StringUtil.indexOf(contents, '\n', scalarEnd);
		int lineEnd = newline == -1 ? contents.length() : newline;

		return SyntaxTraverser.psiTraverser(scalar.getParent().getParent()).filter(PsiComment.class)
				.filter(it -> {
					int commentStart = it.getTextRange().getStartOffset();
					return commentStart >= scalarEnd && commentStart < lineEnd;
				}).first();
	}

}
