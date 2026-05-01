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

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.github.UsesRepositoryAction.VersionText;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.ReleasesCompletionProvider;
import biz.paluch.dap.support.ReleasesCompletionProvider.CompletionMetadata;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.PsiVisitors;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons.Vcs;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * Completion contributor for GitHub Actions {@code uses:} refs.
 *
 * <p>Suggests cached release options with SHA-awareness: when the current ref
 * uses SHA style and the release has SHA metadata, the inserted text is the
 * release commit SHA, shortened to the current SHA prefix length if the
 * workflow already uses an abbreviated SHA. Otherwise, the release version
 * string is inserted.
 *
 * @author Mark Paluch
 */
public class GitHubWorkflowCompletionContributor extends CompletionContributor {

	private static final ExtensionPointName<DependencyAssistant> ASSISTANTS = ExtensionPointName
			.create("biz.paluch.dap.assistant");

	private final ReleasesCompletionProvider provider = new ReleasesCompletionProvider(element -> {

		PsiFile file = element.getContainingFile();
		if (!GitHubUtils.isWorkflowFile(file)) {
			return null;
		}

		ProjectDependencyContext context = resolveContext(file);
		if (context == null || context.isAbsent()) {
			return null;
		}

		PsiElement position = PsiVisitors.unleaf(element);

		VersionUpgradeLookupSupport lookup = context.getLookup(position);
		ArtifactReference artifactReference = lookup.resolveArtifactReference(position);
		if (!artifactReference.isResolved()) {
			return null;
		}

		ArtifactVersion currentVersion = artifactReference.getDeclaration().isVersionDefined()
				? artifactReference.getDeclaration().getVersion()
				: null;

		return new CompletionMetadata(artifactReference.getArtifactId(), currentVersion,
				artifactReference.getDeclaration().getVersionLiteral());
	}) {

		@Override
		protected LookupElementBuilder postProcess(LookupElementBuilder element, PsiElement position,
				ArtifactRelease option) {

			YAMLScalar scalar = VersionUpgradeLookupService.findUsesScalar(position);
			if (scalar == null || !(option.getVersion() instanceof GitVersion version)) {
				return element;
			}

			UsesRepositoryAction ref = GitHubWorkflowParser.parseUses(scalar.getTextValue());
			if (ref == null) {
				return element;
			}

			if (StringUtils.hasText(version.getSha())) {
				element = element.withTypeText(version.getShortSha(), Vcs.CommitNode, false);
			}

			return element.withInsertHandler((insertionContext, lookupElement) -> insertVersion(insertionContext,
					scalar, ref, version));
		}

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {

			if (parameters.getInvocationCount() > 1 || isCaretInsideRef(parameters)) {
				return result.withPrefixMatcher("");
			}

			return result.withPrefixMatcher(refPrefixAtCaret(parameters));
		}

	};

	private static boolean isCaretInsideRef(CompletionParameters parameters) {

		YAMLScalar scalar = VersionUpgradeLookupService.findUsesScalar(parameters.getPosition());
		if (scalar == null) {
			return false;
		}

		TextRange versionRange = GitHubUtils.getVersionRange(scalar);
		return versionRange.contains(parameters.getOffset());
	}

	public GitHubWorkflowCompletionContributor() {
		extend(CompletionType.BASIC,
				PlatformPatterns.psiElement()
						.with(new PatternCondition<>("afterGitHubWorkflowUsesRefSeparator") {

							@Override
							public boolean accepts(PsiElement element, ProcessingContext context) {
								return isAfterRefSeparatorInUsesScalar(element);
							}

						}),
				provider);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return typeChar == '@' && VersionUpgradeLookupService.findUsesScalar(position) != null;
	}

	private static boolean isAfterRefSeparatorInUsesScalar(PsiElement element) {

		YAMLScalar scalar = VersionUpgradeLookupService.findUsesScalar(element);
		if (scalar == null || !scalar.isValid()) {
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

	private static @Nullable ProjectDependencyContext resolveContext(PsiFile file) {

		for (DependencyAssistant assistant : ASSISTANTS.getExtensionList()) {
			if (assistant.supports(file)) {
				return assistant.createContext(file.getProject(), file);
			}
		}
		return null;
	}

	private static void insertVersion(InsertionContext context, YAMLScalar scalar, UsesRepositoryAction ref,
			GitVersion version) {

		if (!scalar.isValid()) {
			return;
		}

		VersionText versionText = ref.getVersion(version);
		YAMLScalar updated = new UpdateGitHubWorkflowFile(scalar.getProject()).updateVersionAndComment(scalar,
				versionText);
		if (updated == null) {
			return;
		}

		context.getEditor().getCaretModel().moveToOffset(UpdateGitHubWorkflowFile.getValueEndOffset(updated));
	}

	private static String refPrefixAtCaret(CompletionParameters parameters) {
		return getPrefix(parameters, PsiVisitors.unleaf(parameters.getPosition()));
	}

	private static String getPrefix(CompletionParameters parameters, PsiElement position) {
		YAMLScalar scalar = VersionUpgradeLookupService.findUsesScalar(position);
		if (scalar == null) {
			return "";
		}
		String text = scalar.getText();
		int atIndex = text.indexOf('@');
		if (atIndex < 0) {
			return "";
		}
		int caretInScalar = parameters.getOffset() - scalar.getTextRange().getStartOffset();
		int refStart = atIndex + 1;
		if (caretInScalar < refStart || caretInScalar > text.length()) {
			return "";
		}
		return text.substring(refStart, caretInScalar);
	}

}
