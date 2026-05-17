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

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.assistant.ReleaseCompletionProvider;
import biz.paluch.dap.github.UsesRepositoryAction.VersionText;
import biz.paluch.dap.util.PatternConditions;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.yaml.psi.YAMLKeyValue;
import org.jetbrains.yaml.psi.YAMLScalar;

/**
 * Completion contributor for GitHub Actions {@code uses:} refs.
 *
 * <p>
 * Suggests cached release options with SHA-awareness: when the current ref
 * uses SHA style and the release has SHA metadata, the inserted text is the
 * release commit SHA, shortened to the current SHA prefix length if the
 * workflow already uses an abbreviated SHA. Otherwise, the release version
 * string is inserted.
 *
 * @author Mark Paluch
 */
public class GitHubWorkflowCompletionContributor extends CompletionContributor {

	private static final String ANTORA_PLAYBOOK_FILE_NAME = "antora-playbook.yml";

	private static final ReleaseCompletionProvider PROVIDER = new ReleaseCompletionProvider() {

		@Override
		protected RefStyle getRefStyle(PsiElement element, CompletionMetadata metadata) {

			UsesRepositoryAction action = VersionUpgradeLookupService.findUsesRepository(element);
			return action != null ? action.getStyle() : super.getRefStyle(element, metadata);
		}

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {
			if (parameters.getInvocationCount() > 1 || isCaretInsideRef(parameters)) {
				return result.withPrefixMatcher("");
			}

			return super.getPrefixMatcher(parameters, result);
		}

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
				PsiElement element, ArtifactRelease option) {

			YAMLScalar scalar = VersionUpgradeLookupService.findUsesScalar(element);
			UsesRepositoryAction action = VersionUpgradeLookupService.findUsesRepository(element);
			if (scalar == null || action == null || !(option.getVersion() instanceof GitVersion version)) {
				return builder;
			}

			SmartPsiElementPointer<YAMLScalar> pointer = SmartPointerManager.createPointer(scalar);
			Project project = element.getProject();
			return builder.withInsertHandler((insertionContext, lookupElement) -> {

				YAMLScalar toUpdate = pointer.getElement();
				VersionText text = action.getVersion(version);
				new UpdateGitHubWorkflowFile(project).updateVersionAndComment(toUpdate, text);
			});
		}
	};

	// Version/ref completion after the `@` separator in `owner/repository@ref`.
	private static final PatternCondition<PsiElement> AFTER_GITHUB_WORKFLOW_USES_REF_SEPARATOR = PatternConditions
			.conditional("afterGitHubWorkflowUsesRefSeparator",
					GitHubWorkflowCompletionContributor::isAfterRefSeparatorInUsesScalar);

	// YAML key-value declarations such as `uses: actions/checkout@v4`.
	private static final PsiElementPattern.Capture<YAMLKeyValue> GITHUB_WORKFLOW_USES_KEY_VALUE = PlatformPatterns
			.psiElement(YAMLKeyValue.class).with(PatternConditions
					.conditional("isGitHubWorkflowUsesKeyValue",
							GitHubWorkflowCompletionContributor::isUsesKeyValue));

	private static final PsiElementPattern.Capture<PsiElement> GITHUB_WORKFLOW_USES_REF_IN_SCALAR = PlatformPatterns
			.psiElement(PsiElement.class)
			.inside(PlatformPatterns.psiElement(YAMLScalar.class).withParent(GITHUB_WORKFLOW_USES_KEY_VALUE))
			.with(AFTER_GITHUB_WORKFLOW_USES_REF_SEPARATOR);

	private static final PsiElementPattern.Capture<PsiElement> GITHUB_WORKFLOW_USES_REF = PlatformPatterns
			.psiElement()
			.with(AFTER_GITHUB_WORKFLOW_USES_REF_SEPARATOR);


	public GitHubWorkflowCompletionContributor() {
		extend(CompletionType.BASIC, GITHUB_WORKFLOW_USES_REF_IN_SCALAR, PROVIDER);
		extend(CompletionType.BASIC, GITHUB_WORKFLOW_USES_REF, PROVIDER);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {

		if (isAntoraPlaybook(position)) {
			return false;
		}

		if (typeChar == '@') {
			return VersionUpgradeLookupService.findUsesScalar(position) != null;
		}

		return ReleaseCompletionProvider.isVersionCharacter(typeChar) && isSupportedCompletionSite(position);
	}

	private static boolean isSupportedCompletionSite(PsiElement position) {
		return GITHUB_WORKFLOW_USES_REF_IN_SCALAR.accepts(position) || GITHUB_WORKFLOW_USES_REF.accepts(position);
	}

	private static boolean isCaretInsideRef(CompletionParameters parameters) {

		YAMLScalar scalar = VersionUpgradeLookupService.findUsesScalar(parameters.getPosition());
		if (scalar == null) {
			return false;
		}

		TextRange versionRange = GitHubUtils.getVersionRange(scalar);
		return versionRange.contains(parameters.getOffset());
	}

	private static boolean isUsesKeyValue(YAMLKeyValue keyValue) {

		if (isAntoraPlaybook(keyValue)) {
			return false;
		}
		return "uses".equals(keyValue.getKeyText());
	}

	private static boolean isAfterRefSeparatorInUsesScalar(PsiElement element) {

		YAMLScalar scalar = VersionUpgradeLookupService.findUsesScalar(element);
		if (scalar == null || !scalar.isValid()) {
			return false;
		}

		if (isAntoraPlaybook(scalar)) {
			return false;
		}

		String text = scalar.getText();
		int offset = getCompletionOffsetInScalar(scalar, element);
		return offset > 0 && text.lastIndexOf('@', offset - 1) >= 0;
	}

	private static int getCompletionOffsetInScalar(YAMLScalar scalar, PsiElement element) {

		String text = scalar.getText();
		int dummyOffset = text.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
		if (dummyOffset >= 0) {
			return dummyOffset;
		}

		int offset = element.getTextRange().getStartOffset() - scalar.getTextRange().getStartOffset();
		return Math.max(0, Math.min(offset, text.length()));
	}

	private static boolean isAntoraPlaybook(PsiElement element) {

		PsiFile containingFile = element.getContainingFile();
		return containingFile != null && ANTORA_PLAYBOOK_FILE_NAME.equals(containingFile.getName());
	}

}
