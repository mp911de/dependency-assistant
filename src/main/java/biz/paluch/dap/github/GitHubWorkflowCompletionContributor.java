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
import biz.paluch.dap.github.WorkflowUsesReference.VersionText;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.ReleasesSuggestionProvider;
import biz.paluch.dap.support.ReleasesSuggestionProvider.CompletionMetadata;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.icons.AllIcons.Vcs;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.util.ProcessingContext;
import org.jetbrains.yaml.psi.YAMLScalar;
import org.jspecify.annotations.Nullable;

/**
 * Completion contributor for GitHub Actions workflow {@code uses:} refs.
 *
 * <p>Suggests cached release options with SHA-awareness: when the current ref
 * uses SHA style, the inserted text is the full 40-character commit SHA;
 * otherwise, the version string is inserted with the same prefix style as the
 * original ({@code v} prefix preserved or absent).
 *
 * @author Mark Paluch
 */
public class GitHubWorkflowCompletionContributor extends CompletionContributor {

	private static final ExtensionPointName<DependencyAssistant> ASSISTANTS = ExtensionPointName
			.create("biz.paluch.dap.assistant");

	private final ReleasesSuggestionProvider provider = new ReleasesSuggestionProvider(element -> {

		PsiFile file = element.getContainingFile();
		if (!GitHubUtils.isWorkflowFile(file)) {
			return null;
		}

		ProjectDependencyContext context = resolveContext(file);
		if (context == null) {
			return null;
		}

		PsiElement position = element;
		if (position instanceof LeafPsiElement) {
			position = position.getParent();
		}

		VersionUpgradeLookupSupport lookup = context.getLookup(position);
		ArtifactReference artifactReference = lookup.resolveArtifactReference(position);
		if (!artifactReference.isResolved()) {
			return null;
		}

		ArtifactVersion currentVersion = artifactReference.getDeclaration().isVersionDefined()
				? artifactReference.getDeclaration().getVersion()
				: null;

		return new CompletionMetadata(artifactReference.getArtifactId(),
				currentVersion);
	}) {

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters, PsiElement position,
				@Nullable ArtifactVersion currentVersion, ArtifactRelease option, CompletionResultSet result,
				LookupElementBuilder element) {

			YAMLScalar scalar = VersionUpgradeLookupService.findUsesScalar(position);
			if (scalar == null || !(option.getVersion() instanceof GitVersion version)) {
				return element;
			}

			String scalarText = scalar.getTextValue();
			WorkflowUsesReference ref = GitHubWorkflowParser.parseUsesValue(scalarText);
			if (ref == null) {
				return element;
			}

			if (StringUtils.hasText(version.getSha())) {
				element = element.withTypeText(version.getSha().substring(0, 8), Vcs.CommitNode, false);
			}

			return element.withInsertHandler((insertionContext, lookupElement) -> {

				Document document = insertionContext.getDocument();
				VersionText insertText = ref.getVersion(version);
				int startOffset = insertionContext.getStartOffset();
				int replaceStart = findRefStartOffset(document, startOffset);
				String closingQuoteSuffix = getClosingQuoteSuffix(scalar);
				boolean preserveTrailing = !StringUtils.hasText(insertText.comment())
						&& isCompleteRef(ref.rawVersion());

				String text;
				int replaceEnd;
				if (preserveTrailing) {
					text = insertText.text();
					replaceEnd = findScalarValueEnd(document, replaceStart, closingQuoteSuffix);
				} else {
					text = insertText.text() + closingQuoteSuffix;
					if (StringUtils.hasText(insertText.comment())) {
						text += " # " + insertText.comment();
					}
					replaceEnd = getLineEndOffset(document, insertionContext.getTailOffset());
				}

				document.replaceString(replaceStart, replaceEnd, text);
				insertionContext.getEditor().getCaretModel().moveToOffset(replaceStart + text.length());
			});
		}

		private static int findRefStartOffset(Document document, int offset) {
			CharSequence chars = document.getCharsSequence();

			int lineNumber = document.getLineNumber(offset);
			int lineStart = document.getLineStartOffset(lineNumber);
			int at = -1;

			for (int i = offset - 1; i >= lineStart; i--) {
				if (chars.charAt(i) == '@') {
					at = i;
					break;
				}

				// Optional safety: stop if we move before the action name
				if (Character.isWhitespace(chars.charAt(i)) || chars.charAt(i) == '"' || chars.charAt(i) == '\'') {
					break;
				}
			}

			return at >= 0 ? at + 1 : offset;
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

		PsiElement position = parameters.getPosition();
		if (position instanceof LeafPsiElement) {
			position = position.getParent();
		}
		YAMLScalar scalar = VersionUpgradeLookupService.findUsesScalar(position);
		if (scalar == null) {
			return false;
		}

		String text = scalar.getText();
		int atIndex = text.indexOf('@');
		if (atIndex < 0) {
			return false;
		}

		int caretInScalar = parameters.getOffset() - scalar.getTextRange().getStartOffset();
		int end = text.length();
		if (end > 0) {
			char last = text.charAt(end - 1);
			if (last == '"' || last == '\'') {
				end--;
			}
		}
		return caretInScalar > atIndex + 1 && caretInScalar < end;
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
		if (scalar == null) {
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

	private static String refPrefixAtCaret(CompletionParameters parameters) {

		// Compute the prefix relative to the start of the ref portion of the
		// uses: scalar so that the completion engine's prefix-matching does not
		// reject candidates that differ from the existing ref text.
		PsiElement position = parameters.getPosition();
		if (position instanceof LeafPsiElement) {
			position = position.getParent();
		}
		return getPrefix(parameters, position);
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


	private static int getLineEndOffset(Document document, int offset) {

		int boundedOffset = Math.max(0, Math.min(offset, document.getTextLength()));
		return document.getLineEndOffset(document.getLineNumber(boundedOffset));
	}

	private static boolean isCompleteRef(@Nullable String rawVersion) {

		if (rawVersion == null || rawVersion.isEmpty()) {
			return false;
		}
		char last = rawVersion.charAt(rawVersion.length() - 1);
		return last != '.' && last != '-';
	}

	private static int findScalarValueEnd(Document document, int afterAtOffset, String closingQuoteSuffix) {

		CharSequence chars = document.getCharsSequence();
		int lineEnd = getLineEndOffset(document, afterAtOffset);

		if (!closingQuoteSuffix.isEmpty()) {
			char quote = closingQuoteSuffix.charAt(0);
			for (int i = afterAtOffset; i < lineEnd; i++) {
				if (chars.charAt(i) == quote) {
					return i;
				}
			}
			return lineEnd;
		}

		for (int i = afterAtOffset; i < lineEnd; i++) {
			char c = chars.charAt(i);
			if (Character.isWhitespace(c) || c == '#') {
				return i;
			}
		}
		return lineEnd;
	}

	private static String getClosingQuoteSuffix(YAMLScalar scalar) {

		String text = scalar.getText();
		if (text.length() < 2) {
			return "";
		}

		char first = text.charAt(0);
		if (first != '"' && first != '\'') {
			return "";
		}

		return text.charAt(text.length() - 1) == first ? String.valueOf(first) : "";
	}

}
