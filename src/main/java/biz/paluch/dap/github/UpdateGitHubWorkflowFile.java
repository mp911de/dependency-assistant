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
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.github.UsesRepositoryAction.VersionText;
import biz.paluch.dap.support.yaml.YamlVersionSite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.yaml.YAMLElementGenerator;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jetbrains.yaml.psi.impl.YAMLBlockMappingImpl;
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

		if (!(site.keyValue().getParent() instanceof YAMLBlockMappingImpl mapping)) {
			return null;
		}

		String replacementValue = value.substring(0, refSeparator + 1) + versionText.text();
		YAMLKeyValue replaced = site.replaceRawValue(replacementValue, factory);

		if (versionText.hasComment()) {

			String trailingContent = "";
			PsiComment comment = SyntaxTraverser.psiTraverser(mapping).filter(PsiComment.class).first();
			if (comment != null) {

				String comments = comment.getText();
				int firstComment = comments.indexOf("#");

				if (firstComment != -1) {
					int nextComment = comments.indexOf("#", firstComment + 1);
					if (nextComment != -1) {
						trailingContent += " " + comments.substring(nextComment);
					}
				}
			}
			PsiElement newComment = factory.createDummyYamlWithText("# " + versionText.comment() + trailingContent)
					.getFirstChild();
			if (comment != null) {
				comment.replace(newComment);
			} else {
				PsiElement space = mapping.addAfter(factory.createSpace(), replaced);
				mapping.addAfter(newComment, space);
			}
		}

		return (YAMLScalar) replaced.getValue();
	}

}
