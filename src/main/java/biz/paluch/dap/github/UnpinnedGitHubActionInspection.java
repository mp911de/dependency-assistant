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
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import com.intellij.psi.PsiFile;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * Flags mutable action refs that can be pinned to a cached commit SHA.
 * <p>Tags can move after review. Findings use canonical Git ref resolution and
 * require cached SHA metadata so the fix needs no network access.
 * Already-pinned refs, including abbreviated SHAs, are left unchanged.
 *
 * @author Mark Paluch
 * @see GitVersionResolver
 */
public class UnpinnedGitHubActionInspection extends LocalInspectionTool implements DumbAware {

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

				holder.registerProblem(scalar,
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
	 * Pins the ref and adds the resolved version as a managed comment.
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
			return MessageBundle.message("inspection.github.unpinned-action.fix", pinTarget.getRequiredShortSha());
		}

		@Override
		public @IntentionFamilyName String getFamilyName() {
			return MessageBundle.message("inspection.github.unpinned-action.fix.family");
		}

	}

}
