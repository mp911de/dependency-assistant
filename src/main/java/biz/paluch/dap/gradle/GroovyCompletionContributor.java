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

import java.util.Locale;
import java.util.function.Predicate;

import biz.paluch.dap.assistant.ReleasesCompletionProvider;
import biz.paluch.dap.util.PatternConditions;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.CompletionUtilCore;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

/**
 * Completion contributor for Gradle Groovy DSL.
 *
 * @author Mark Paluch
 */
public class GroovyCompletionContributor extends CompletionContributor {

	private static final ReleasesCompletionProvider provider = new ReleasesCompletionProvider() {

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters,
				CompletionResultSet result) {

			if (parameters.getInvocationCount() > 1) {
				return result.withPrefixMatcher("");
			}

			GrLiteral literal = PsiTreeUtil.getParentOfType(parameters.getPosition(), GrLiteral.class, false);
			if (literal == null) {
				return result;
			}

			return result.withPrefixMatcher(getVersionPrefix(parameters, literal));
		}

	};

	private static final PatternCondition<PsiFile> IS_GRADLE_GROOVY_DSL_FILE = PatternConditions.conditional(
			"isGradleGroovyDslFile", GradleUtils::isGroovyDsl);

	private static final PatternCondition<PsiElement> DIRECT_DEPENDENCY_NOTATION_POSITION = literalPosition(
			"directDependencyNotationPosition", GradleParser::isDirectDependencyNotationLiteral);

	private static final PatternCondition<PsiElement> VERSION_NAMED_ARGUMENT_LITERAL_POSITION = literalPosition(
			"versionNamedArgumentLiteralPosition", GradleParser::isVersionNamedArgumentLiteral);

	private static final PatternCondition<GrReferenceExpression> VERSION_NAMED_ARGUMENT_REFERENCE = PatternConditions
			.conditional("versionNamedArgumentReference", GradleParser::isVersionNamedArgumentReference);

	private static final PatternCondition<PsiElement> PREFER_VERSION_BLOCK_POSITION = literalPosition(
			"preferVersionBlockPosition",
			literal -> GradleParser.isVersionBlockLiteral(literal, GradleVersionConstraint.PREFER));

	private static final PatternCondition<PsiElement> STRICTLY_VERSION_BLOCK_POSITION = literalPosition(
			"strictlyVersionBlockPosition",
			literal -> GradleParser.isVersionBlockLiteral(literal, GradleVersionConstraint.STRICTLY));

	private static final PatternCondition<GrReferenceExpression> PREFER_VERSION_BLOCK_REFERENCE = PatternConditions
			.conditional("preferVersionBlockReference",
					reference -> GradleParser.isVersionBlockReference(reference, GradleVersionConstraint.PREFER));

	private static final PatternCondition<GrReferenceExpression> STRICTLY_VERSION_BLOCK_REFERENCE = PatternConditions
			.conditional("strictlyVersionBlockReference",
					reference -> GradleParser.isVersionBlockReference(reference, GradleVersionConstraint.STRICTLY));

	private static final PatternCondition<PsiElement> PLUGIN_VERSION_POSITION = literalPosition(
			"pluginVersionPosition", GradleParser::isPluginVersionLiteral);

	private static final PatternCondition<PsiElement> BACKING_VERSION_PROPERTY_POSITION = literalPosition(
			"backingVersionPropertyPosition", GradleParser::isBackingVersionPropertyLiteral);

	private static final PsiElementPattern.Capture<PsiElement> INLINE_DEPENDENCY_NOTATION = inGradleGroovyFile(
			DIRECT_DEPENDENCY_NOTATION_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> MAP_LITERAL_VERSION = inGradleGroovyFile(
			VERSION_NAMED_ARGUMENT_LITERAL_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> MAP_PROPERTY_VERSION = insideGradleGroovyFile(
			PlatformPatterns.psiElement(GrReferenceExpression.class).with(VERSION_NAMED_ARGUMENT_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_PREFER_LITERAL_VERSION = inGradleGroovyFile(
			PREFER_VERSION_BLOCK_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_STRICTLY_LITERAL_VERSION = inGradleGroovyFile(
			STRICTLY_VERSION_BLOCK_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_PREFER_PROPERTY_VERSION = insideGradleGroovyFile(
			PlatformPatterns.psiElement(GrReferenceExpression.class).with(PREFER_VERSION_BLOCK_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_STRICTLY_PROPERTY_VERSION = insideGradleGroovyFile(
			PlatformPatterns.psiElement(GrReferenceExpression.class).with(STRICTLY_VERSION_BLOCK_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> PLUGIN_DSL_VERSION = inGradleGroovyFile(
			PLUGIN_VERSION_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> BACKING_VERSION_PROPERTY = inGradleGroovyFile(
			BACKING_VERSION_PROPERTY_POSITION);

	public GroovyCompletionContributor() {

		// implementation 'group:name:version' and platform/enforcedPlatform string
		// notation.
		extend(CompletionType.BASIC, INLINE_DEPENDENCY_NOTATION, provider);

		// implementation group: 'group', name: 'name', version: 'version'.
		extend(CompletionType.BASIC, MAP_LITERAL_VERSION, provider);

		// implementation group: 'group', name: 'name', version: versionProperty.
		extend(CompletionType.BASIC, MAP_PROPERTY_VERSION, provider);

		// implementation('group:name') { version { prefer 'version' } }.
		extend(CompletionType.BASIC, VERSION_BLOCK_PREFER_LITERAL_VERSION, provider);

		// implementation('group:name') { version { strictly 'version' } }.
		extend(CompletionType.BASIC, VERSION_BLOCK_STRICTLY_LITERAL_VERSION, provider);

		// implementation('group:name') { version { prefer versionProperty } }.
		extend(CompletionType.BASIC, VERSION_BLOCK_PREFER_PROPERTY_VERSION, provider);

		// implementation('group:name') { version { strictly versionProperty } }.
		extend(CompletionType.BASIC, VERSION_BLOCK_STRICTLY_PROPERTY_VERSION, provider);

		// plugins { id 'plugin.id' version 'version' }.
		extend(CompletionType.BASIC, PLUGIN_DSL_VERSION, provider);

		// Groovy local/ext property version literals used as backing version values.
		extend(CompletionType.BASIC, BACKING_VERSION_PROPERTY, provider);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {

		if (typeChar == ':') {
			return INLINE_DEPENDENCY_NOTATION.accepts(position) && isCompactNotationBeforeVersion(position);
		}

		return ReleasesCompletionProvider.isVersionCharacter(typeChar) && isSupportedCompletionSite(position);
	}

	private static PsiElementPattern.Capture<PsiElement> insideGradleGroovyFile(
			ElementPattern<? extends PsiElement> pattern) {

		return PlatformPatterns.psiElement()
				.inside(pattern)
				.inside(PlatformPatterns.psiFile().with(IS_GRADLE_GROOVY_DSL_FILE));
	}

	private static PsiElementPattern.Capture<PsiElement> inGradleGroovyFile(
			PatternCondition<? super PsiElement> condition) {

		return PlatformPatterns.psiElement()
				.with(condition)
				.inside(PlatformPatterns.psiFile().with(IS_GRADLE_GROOVY_DSL_FILE));
	}

	private static PatternCondition<PsiElement> literalPosition(String name,
			Predicate<GrLiteral> predicate) {

		return PatternConditions.conditional(name, position -> {
			GrLiteral literal = PsiTreeUtil.getParentOfType(position, GrLiteral.class, false);
			return literal != null && GradleParser.isStringLiteral(literal) && predicate.test(literal);
		});
	}

	public static boolean isSupportedCompletionSite(PsiElement position) {

		return INLINE_DEPENDENCY_NOTATION.accepts(position)
				|| MAP_LITERAL_VERSION.accepts(position)
				|| MAP_PROPERTY_VERSION.accepts(position)
				|| VERSION_BLOCK_PREFER_LITERAL_VERSION.accepts(position)
				|| VERSION_BLOCK_STRICTLY_LITERAL_VERSION.accepts(position)
				|| VERSION_BLOCK_PREFER_PROPERTY_VERSION.accepts(position)
				|| VERSION_BLOCK_STRICTLY_PROPERTY_VERSION.accepts(position)
				|| PLUGIN_DSL_VERSION.accepts(position)
				|| BACKING_VERSION_PROPERTY.accepts(position);
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

	private static String getVersionPrefix(CompletionParameters parameters, GrLiteral literal) {

		String text = literal.getText();
		int caretInLiteral = parameters.getOffset() - literal.getTextRange().getStartOffset();
		int dummy = text.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED);
		if (dummy == -1) {
			dummy = text.toLowerCase(Locale.ROOT)
					.indexOf(CompletionUtilCore.DUMMY_IDENTIFIER_TRIMMED.toLowerCase(Locale.ROOT));
		}
		int prefixEnd = dummy != -1 ? dummy : caretInLiteral;
		if (prefixEnd < 0 || prefixEnd > text.length()) {
			return "";
		}

		int prefixStart;
		if (GradleParser.isDirectDependencyNotationLiteral(literal)) {
			prefixStart = text.lastIndexOf(':', prefixEnd - 1) + 1;
		} else {
			prefixStart = text.startsWith("'") || text.startsWith("\"") ? 1 : 0;
		}

		if (prefixStart < 0 || prefixStart > prefixEnd) {
			return "";
		}

		return text.substring(prefixStart, prefixEnd);
	}

}
