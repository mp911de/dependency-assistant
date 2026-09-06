/*
 * Copyright 2026-present the original author or authors.
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
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.github.UsesRepositoryAction.VersionText;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileDependencyUpdater;
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
 * Updates GitHub Actions refs while preserving quotes and pinning style.
 * <p>SHA updates include a managed version comment. The first same-line comment
 * is replaced. Content after a second {@code #} is retained.
 *
 * @author Mark Paluch
 * @see UsesRepositoryAction
 */
class UpdateGitHubWorkflowFile implements FileDependencyUpdater {

	private final YAMLElementGenerator factory;

	public UpdateGitHubWorkflowFile(Project project) {
		this.factory = new YAMLElementGenerator(project);
	}

	/**
	 * Apply matching updates to {@code uses:} values and managed version comments.
	 */
	@Override
	public void applyUpdates(PsiFile psiFile, DependencyUpdates updates) {

		SyntaxTraverser.psiTraverser(psiFile).filter(YAMLKeyValue.class)
				.filter(it -> "uses".equals(it.getKeyText()))
				.map(YAMLKeyValue::getValue)
				.filter(YAMLScalar.class)
				.filter(YAMLScalar::isValid)
				.forEach(it -> applyUpdates(it, updates));
	}

	private void applyUpdates(YAMLScalar scalar, DependencyUpdates updates) {

		UsesRepositoryAction ref = GitHubWorkflowParser.parseUses(scalar.getTextValue());
		if (ref == null || !StringUtils.hasText(ref.version())) {
			return;
		}

		ArtifactId artifactId = ref.getArtifactId();

		updates.updateAll(scalar.getContainingFile(), update -> {
			if (!artifactId.equals(update.artifactId()) || !(update.to() instanceof GitVersion gitVersion)) {
				return;
			}

			updateVersionAndComment(scalar, ref.getVersion(gitVersion));
		});
	}

	/**
	 * Apply an update at the given {@code uses:} scalar.
	 */
	public void applyUpdate(YAMLScalar scalar, DependencyUpdate update) {

		UsesRepositoryAction ref = GitHubWorkflowParser.parseUses(scalar.getTextValue());
		if (ref == null || !StringUtils.hasText(ref.version())) {
			return;
		}

		if (!(update.to() instanceof GitVersion gitVersion)) {
			return;
		}

		updateVersionAndComment(scalar, ref.getVersion(gitVersion));
	}

	/**
	 * Replace a {@code uses:} ref and its managed comment.
	 * @return the replacement scalar for further PSI operations, or {@literal null}
	 * if no writable {@code uses:} site with a ref separator is found.
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
