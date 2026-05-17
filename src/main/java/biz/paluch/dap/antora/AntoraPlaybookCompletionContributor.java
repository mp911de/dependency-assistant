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

package biz.paluch.dap.antora;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.GitVersion;
import biz.paluch.dap.artifact.RefStyle;
import biz.paluch.dap.assistant.ReleaseCompletionProvider;
import biz.paluch.dap.util.PatternConditions;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.yaml.psi.YAMLScalar;

/**
 * Completion contributor for Antora playbook {@code ui.bundle.url} version
 * segments.
 *
 * <p>
 * Suggests cached release versions when the caret is positioned inside the
 * version slice of the URL between {@code /releases/download/} and the next
 * path separator. Host, owner, repository, and asset segments are left
 * untouched.
 *
 * @author Mark Paluch
 */
public class AntoraPlaybookCompletionContributor extends CompletionContributor {

	private static final ReleaseCompletionProvider PROVIDER = new ReleaseCompletionProvider() {

		@Override
		protected RefStyle getRefStyle(PsiElement element, CompletionMetadata metadata) {
			return RefStyle.VERSION;
		}

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters, CompletionResultSet result) {

			if (parameters.getInvocationCount() > 1 || isCaretInsideVersion(parameters)) {
				return result.withPrefixMatcher("");
			}
			return super.getPrefixMatcher(parameters, result);
		}

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
				PsiElement element, ArtifactRelease option) {

			YAMLScalar scalar = VersionUpgradeLookupService.findBundleUrlScalar(element);
			if (scalar == null) {
				// No bundle URL scalar resolved: suppress the destructive default
				// insert handler so completion does not rewrite the surrounding text.
				return builder.withInsertHandler((ctx, le) -> {
				});
			}

			GitVersion gitVersion = option.getVersion() instanceof GitVersion gv ? gv
					: GitVersion.of(option.getVersion());

			String originalValue = scalar.getTextValue();
			SmartPsiElementPointer<YAMLScalar> pointer = SmartPointerManager.createPointer(scalar);
			Project project = element.getProject();
			return builder.withInsertHandler((insertionContext, lookupElement) -> {

				YAMLScalar toUpdate = pointer.getElement();
				if (toUpdate != null) {
					new UpdateAntoraPlaybookFile(project).updateVersion(toUpdate, originalValue, gitVersion);
				}
			});
		}

	};

	private static final PatternCondition<PsiElement> INSIDE_ANTORA_BUNDLE_URL_VERSION = PatternConditions
			.conditional("insideAntoraBundleUrlVersion",
					AntoraPlaybookCompletionContributor::isInsideBundleUrlVersion);

	private static final PsiElementPattern.Capture<PsiElement> ANTORA_BUNDLE_URL_VERSION_IN_SCALAR = PlatformPatterns
			.psiElement(PsiElement.class)
			.inside(PlatformPatterns.psiElement(YAMLScalar.class))
			.with(INSIDE_ANTORA_BUNDLE_URL_VERSION);

	private static final PsiElementPattern.Capture<PsiElement> ANTORA_BUNDLE_URL_VERSION = PlatformPatterns
			.psiElement()
			.with(INSIDE_ANTORA_BUNDLE_URL_VERSION);

	public AntoraPlaybookCompletionContributor() {
		extend(CompletionType.BASIC, ANTORA_BUNDLE_URL_VERSION_IN_SCALAR, PROVIDER);
		extend(CompletionType.BASIC, ANTORA_BUNDLE_URL_VERSION, PROVIDER);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {
		return ReleaseCompletionProvider.isVersionCharacter(typeChar) && isSupportedCompletionSite(position);
	}

	private static boolean isSupportedCompletionSite(PsiElement position) {
		return ANTORA_BUNDLE_URL_VERSION_IN_SCALAR.accepts(position) || ANTORA_BUNDLE_URL_VERSION.accepts(position);
	}

	private static boolean isCaretInsideVersion(CompletionParameters parameters) {

		YAMLScalar scalar = VersionUpgradeLookupService.findBundleUrlScalar(parameters.getPosition());
		if (scalar == null) {
			return false;
		}

		TextRange versionRange = AntoraUtils.getVersionRange(scalar);
		return versionRange.contains(parameters.getOffset());
	}

	private static boolean isInsideBundleUrlVersion(PsiElement element) {

		YAMLScalar scalar = VersionUpgradeLookupService.findBundleUrlScalar(element);
		if (scalar == null || !scalar.isValid()) {
			return false;
		}

		TextRange versionRange = AntoraUtils.getVersionRange(scalar);
		TextRange elementRange = element.getTextRange();
		return versionRange.contains(elementRange.getStartOffset())
				|| versionRange.intersectsStrict(elementRange);
	}

}
