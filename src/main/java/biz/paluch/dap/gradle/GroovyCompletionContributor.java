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
import biz.paluch.dap.assistant.ReleaseCompletionProvider;
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
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * Completion contributor for Gradle Groovy DSL.
 *
 * @author Mark Paluch
 */
public class GroovyCompletionContributor extends CompletionContributor {

	private static final ReleaseCompletionProvider provider = new ReleaseCompletionProvider() {

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters,
				CompletionResultSet result) {

			if (parameters.getInvocationCount() > 1) {
				return result.withPrefixMatcher("");
			}

			DeclarationStyle site = GroovyDeclarationStyleDetector.getInstance().detect(parameters.getPosition());
			if (site.isAbsent()) {
				return result;
			}

			PsiElement versionElement = site.versionElement();
			if (!(versionElement instanceof GrLiteral) && site.kind() != Kind.COMMAND_PLATFORM) {
				return result;
			}

			return result.withPrefixMatcher(GradleCompletionSupport.getVersionPrefix(parameters, versionElement));
		}

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
				PsiElement element, ArtifactRelease option) {

			DeclarationStyle site = GroovyDeclarationStyleDetector.getInstance().detect(element);
			if (site.isAbsent() || (site.kind() != Kind.INLINE_NOTATION && site.kind() != Kind.COMMAND_PLATFORM)) {
				return builder;
			}

			return builder.withInsertHandler((context, lookupElement) -> GradleCompletionSupport
					.trimInsertedVersionSuffix(context, context.getCompletionChar() == Lookup.REPLACE_SELECT_CHAR));
		}

	};

	private static final PatternCondition<PsiFile> IS_GRADLE_GROOVY_DSL_FILE = PatternConditions.conditional(
			"isGradleGroovyDslFile", GradleUtils::isGroovyDsl);

	// Any element that classifies as a Gradle version position: inline notation,
	// command-platform string, map version argument, version-block constraint,
	// plugin version, or a backing version property. Completion only applies to a
	// constant version literal, so interpolated GString literals are excluded.
	private static final PatternCondition<PsiElement> VERSION_POSITION_CONDITION = PatternConditions.conditional(
			"versionPosition", GroovyCompletionContributor::isVersionCompletionPosition);

	private static final PsiElementPattern.Capture<PsiElement> VERSION_POSITION = PlatformPatterns.psiElement()
			.with(VERSION_POSITION_CONDITION)
			.inside(PlatformPatterns.psiFile().with(IS_GRADLE_GROOVY_DSL_FILE));

	public GroovyCompletionContributor() {
		extend(CompletionType.BASIC, VERSION_POSITION, provider);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {

		if (typeChar == ':') {
			DeclarationStyle site = GroovyDeclarationStyleDetector.getInstance().detect(position);
			return site.isPresent() && site.kind() == Kind.INLINE_NOTATION
					&& GradleUtils.isGroovyDsl(position.getContainingFile())
					&& isCompactNotationBeforeVersion(position);
		}

		return ReleaseCompletionProvider.isVersionCharacter(typeChar) && isSupportedCompletionSite(position);
	}

	public static boolean isSupportedCompletionSite(PsiElement position) {
		return VERSION_POSITION.accepts(position);
	}

	private static boolean isVersionCompletionPosition(PsiElement position) {

		DeclarationStyle site = GroovyDeclarationStyleDetector.getInstance().detect(position);
		return site.isPresent()
				&& (!(site.versionElement() instanceof GrLiteral literal)
						|| GroovyDeclarationStyleDetector.getInstance().isConstantStringLiteral(literal));
	}

	private static boolean isCompactNotationBeforeVersion(PsiElement position) {

		GrLiteral literal = PsiTreeUtil.getParentOfType(position, GrLiteral.class, false);
		if (literal == null) {
			return false;
		}

		String existingText = GroovyDslUtils.getText(literal);
		if (!StringUtils.hasText(existingText) || StringUtil.countChars(existingText, ':') != 1) {
			return false;
		}

		String[] parts = existingText.split(":", -1);
		return parts.length == 2 && StringUtils.hasText(parts[0]) && StringUtils.hasText(parts[1])
				&& !existingText.contains(" ");
	}

}
