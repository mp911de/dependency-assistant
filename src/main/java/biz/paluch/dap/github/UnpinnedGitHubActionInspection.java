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

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.assistant.ArtifactReferenceContextVisitor;
import biz.paluch.dap.github.UsesRepositoryAction.VersionText;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ModCommand;
import com.intellij.modcommand.ModCommandQuickFix;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * {@link LocalInspectionTool} that flags a GitHub Actions {@code uses:}
 * reference pinned to a mutable symbolic ref (a tag or version) when an
 * immutable commit SHA is already known for the canonical Git ref resolution
 * target.
 *
 * <p>Pinning third-party actions to a tag is a supply-chain risk: a tag can be
 * moved to point at different code after review. The inspection reports a
 * version-style ref only when the project's release cache already holds a SHA
 * for the canonical Git ref resolution target, so every finding is directly
 * fixable without a network lookup. For example, {@code @v4} pins to an exact
 * {@code v4} tag when one exists, otherwise to the newest stable SHA-backed
 * {@code v4.x} release.
 *
 * <p>Already-SHA refs (including short SHAs) and branch refs are not flagged:
 * the former are already immutable, the latter never resolve to a cached
 * release SHA. Findings depend on cached release metadata; refreshing the
 * release cache may surface additional refs.
 *
 * @author Mark Paluch
 * @see GitHubArtifactReferenceResolver
 * @see RefStyle
 */
public class UnpinnedGitHubActionInspection extends LocalInspectionTool {

	@Override
	public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {

		PsiFile file = holder.getFile();
		Project project = file.getProject();
		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(project, file);
		if (context.isAbsent()) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		return new ArtifactReferenceContextVisitor(context) {

			@Override
			protected void visitArtifactReference(PsiElement element, ArtifactReferenceContext referenceContext) {

				ArtifactDeclaration declaration = referenceContext.getDeclaration();
				PsiElement versionLiteral = declaration.getVersionLiteral();
				if (!(versionLiteral instanceof YAMLScalar scalar)) {
					return;
				}

				UsesRepositoryAction action = GitHubArtifactReferenceResolver.findUsesRepository(scalar);
				if (action == null) {
					return;
				}

				String rawRef = action.version();
				if (rawRef == null || RefStyle.from(rawRef) != RefStyle.VERSION) {
					return;
				}

				GitVersion pinTarget = findPinTarget(referenceContext, rawRef);
				if (pinTarget == null) {
					return;
				}

				TextRange refRange = GitHubUtils.getVersionRange(scalar);
				TextRange rangeInElement = refRange.shiftLeft(scalar.getTextRange().getStartOffset());

				holder.registerProblem(scalar, rangeInElement,
						MessageBundle.message("inspection.github.unpinned-action.display-name"),
						new PinToShaQuickFix(pinTarget));
			}

			private @Nullable GitVersion findPinTarget(ArtifactReferenceContext referenceContext, String ref) {

				GitVersion gitVersion = GitVersionResolver.resolveVersion(ref, referenceContext.getReleases());
				return gitVersion != null && gitVersion.hasSha() ? gitVersion : null;
			}

		};
	}

	/**
	 * Quick fix that rewrites a mutable symbolic {@code uses:} ref to the immutable
	 * commit SHA and appends the resolved version as a managed comment.
	 */
	static class PinToShaQuickFix extends ModCommandQuickFix {

		private final GitVersion pinTarget;

		PinToShaQuickFix(GitVersion pinTarget) {
			this.pinTarget = pinTarget;
		}

		@Override
		public ModCommand perform(Project project, ProblemDescriptor descriptor) {

			PsiElement element = descriptor.getPsiElement();
			return ModCommand.psiUpdate(element, (updated, updater) -> {
				if (updated instanceof YAMLScalar scalar) {
					YAMLScalar pinned = new UpdateGitHubWorkflowFile(project).updateVersionAndComment(scalar,
							VersionText.create(pinTarget));
					if (pinned != null) {
						updater.moveCaretTo(pinned.getTextRange().getEndOffset());
					}
				}
			});
		}

		@Override
		public String getName() {
			return MessageBundle.message("inspection.github.unpinned-action.fix", pinTarget.getShortSha());
		}

		@Override
		public @IntentionFamilyName String getFamilyName() {
			return MessageBundle.message("inspection.github.unpinned-action.fix.family");
		}

	}

}
