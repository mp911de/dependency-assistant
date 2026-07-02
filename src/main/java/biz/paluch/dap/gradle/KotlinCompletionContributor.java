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

package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.assistant.completion.ReleaseCompletionProvider;
import biz.paluch.dap.gradle.DeclarationStyle.Kind;
import biz.paluch.dap.util.PatternConditions;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;

/**
 * Completion contributor for Gradle Kotlin DSL.
 *
 * @author Mark Paluch
 */
public class KotlinCompletionContributor extends CompletionContributor {

	private static final ReleaseCompletionProvider provider = new ReleaseCompletionProvider() {

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters,
				CompletionResultSet result) {

			if (showsFullHistory(parameters)) {
				return result.withPrefixMatcher("");
			}

			DeclarationStyle site = KotlinDeclarationStyleDetector.getInstance().detect(parameters.getPosition());
			if (site.isAbsent() || !(site.versionElement() instanceof KtStringTemplateExpression literal)) {
				return result;
			}

			return result.withPrefixMatcher(GradleCompletionSupport.getVersionPrefix(parameters, literal));
		}

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
				PsiElement element, ArtifactRelease option) {

			DeclarationStyle site = KotlinDeclarationStyleDetector.getInstance().detect(element);
			if (site.isAbsent() || site.kind() != Kind.INLINE_NOTATION) {
				return builder;
			}

			return builder.withInsertHandler((context, lookupElement) -> GradleCompletionSupport
					.trimInsertedVersionSuffix(context, context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR));
		}

	};

	// Gradle Kotlin DSL build scripts such as `build.gradle.kts`.
	private static final PatternCondition<PsiFile> IS_GRADLE_KOTLIN_DSL_FILE = PatternConditions.conditional(
			"isGradleKotlinDslFile",
			psiFile -> GradleUtils.isGradleScript(psiFile) && GradleUtils.isKotlinDsl(psiFile));

	// Any element that classifies as a Gradle version position: inline notation,
	// map version argument, version-block constraint, plugin version, or a backing
	// version property.
	private static final PatternCondition<PsiElement> VERSION_POSITION_CONDITION = PatternConditions.conditional(
			"versionPosition", position -> KotlinDeclarationStyleDetector.getInstance().detect(position).isPresent());

	private static final PsiElementPattern.Capture<PsiElement> VERSION_POSITION = PlatformPatterns.psiElement()
			.with(VERSION_POSITION_CONDITION)
			.inside(PlatformPatterns.psiFile().with(IS_GRADLE_KOTLIN_DSL_FILE));

	public KotlinCompletionContributor() {
		extend(CompletionType.BASIC, VERSION_POSITION, provider);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {

		if (typeChar == ':') {
			DeclarationStyle site = KotlinDeclarationStyleDetector.getInstance().detect(position);
			PsiFile file = position.getContainingFile();
			return site.isPresent() && site.kind() == Kind.INLINE_NOTATION && GradleUtils.isGradleScript(file)
					&& GradleUtils.isKotlinDsl(file) && isCompactNotationBeforeVersion(position);
		}

		return ReleaseCompletionProvider.isVersionCharacter(typeChar) && isSupportedCompletionSite(position);
	}

	public static boolean isSupportedCompletionSite(PsiElement position) {
		return VERSION_POSITION.accepts(position);
	}

	private static boolean isCompactNotationBeforeVersion(PsiElement position) {

		KtStringTemplateExpression literal = PsiTreeUtil.getParentOfType(position, KtStringTemplateExpression.class,
				false);
		if (literal == null) {
			return false;
		}

		String existingText = KtLiterals.from(literal).toString();
		if (!StringUtils.hasText(existingText) || StringUtil.countChars(existingText, ':') != 1) {
			return false;
		}

		String[] parts = existingText.split(":", -1);
		return parts.length == 2 && StringUtils.hasText(parts[0]) && StringUtils.hasText(parts[1])
				&& !containsWhitespace(existingText);
	}

	private static boolean containsWhitespace(String text) {

		for (int i = 0; i < text.length(); i++) {
			if (Character.isWhitespace(text.charAt(i))) {
				return true;
			}
		}

		return false;
	}

}
