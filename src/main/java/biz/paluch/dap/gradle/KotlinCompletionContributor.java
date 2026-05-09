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
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;

/**
 * Completion contributor for Gradle Kotlin DSL.
 *
 * @author Mark Paluch
 */
public class KotlinCompletionContributor extends CompletionContributor {

	private static final ReleasesCompletionProvider provider = new ReleasesCompletionProvider() {

		@Override
		protected CompletionResultSet getPrefixMatcher(CompletionParameters parameters,
				CompletionResultSet result) {

			if (parameters.getInvocationCount() > 1) {
				return result.withPrefixMatcher("");
			}

			KtStringTemplateExpression literal = PsiTreeUtil.getParentOfType(parameters.getPosition(),
					KtStringTemplateExpression.class, false);
			if (literal == null) {
				return result;
			}

			return result.withPrefixMatcher(getVersionPrefix(parameters, literal));
		}

	};

	// Gradle Kotlin DSL build scripts such as `build.gradle.kts`.
	private static final PatternCondition<PsiFile> IS_GRADLE_KOTLIN_DSL_FILE = PatternConditions.conditional(
			"isGradleKotlinDslFile",
			psiFile -> GradleUtils.isGradleScript(psiFile) && GradleUtils.isKotlinDsl(psiFile));

	// Inline dependency/platform notation such as
	// `implementation("group:name:version")`.
	private static final PatternCondition<KtStringTemplateExpression> DIRECT_DEPENDENCY_NOTATION = PatternConditions
			.conditional("directDependencyNotation", KotlinDslParser::isDirectDependencyNotationLiteral);

	// Map notation with a literal version such as `version = "1.0.0"`.
	private static final PatternCondition<KtStringTemplateExpression> VERSION_NAMED_ARGUMENT_LITERAL = PatternConditions
			.conditional("versionNamedArgumentLiteral", KotlinDslParser::isVersionNamedArgumentLiteral);

	// Map notation with a property reference such as `version = junit`.
	private static final PatternCondition<KtNameReferenceExpression> VERSION_NAMED_ARGUMENT_REFERENCE = PatternConditions
			.conditional("versionNamedArgumentReference", KotlinDslParser::isVersionNamedArgumentReference);

	// Version blocks with literal preferred versions such as `prefer("1.0.0")`.
	private static final PatternCondition<KtStringTemplateExpression> PREFER_VERSION_BLOCK_LITERAL = PatternConditions
			.conditional("preferVersionBlockLiteral",
					literal -> KotlinDslParser.isVersionBlockLiteral(literal, GradleVersionConstraint.PREFER));

	// Version blocks with literal strict versions such as `strictly("1.0.0")`.
	private static final PatternCondition<KtStringTemplateExpression> STRICTLY_VERSION_BLOCK_LITERAL = PatternConditions
			.conditional("strictlyVersionBlockLiteral",
					literal -> KotlinDslParser.isVersionBlockLiteral(literal, GradleVersionConstraint.STRICTLY));

	// Version blocks with preferred property references such as `prefer(junit)`.
	private static final PatternCondition<KtNameReferenceExpression> PREFER_VERSION_BLOCK_REFERENCE = PatternConditions
			.conditional("preferVersionBlockReference",
					reference -> KotlinDslParser.isVersionBlockReference(reference, GradleVersionConstraint.PREFER));

	// Version blocks with strict property references such as `strictly(junit)`.
	private static final PatternCondition<KtNameReferenceExpression> STRICTLY_VERSION_BLOCK_REFERENCE = PatternConditions
			.conditional("strictlyVersionBlockReference",
					reference -> KotlinDslParser.isVersionBlockReference(reference, GradleVersionConstraint.STRICTLY));

	// Plugin DSL version clauses such as `id("plugin.id") version "1.0.0"`.
	private static final PatternCondition<KtStringTemplateExpression> PLUGIN_VERSION_LITERAL = PatternConditions
			.conditional("pluginVersionLiteral", KotlinDslParser::isPluginVersionLiteral);

	// Backing Kotlin/extra properties such as `val junit = "1.0.0"` or
	// `extra["junit"] = "1.0.0"`.
	private static final PatternCondition<KtStringTemplateExpression> BACKING_VERSION_PROPERTY_LITERAL = PatternConditions
			.conditional("backingVersionPropertyLiteral", KotlinDslParser::isBackingVersionPropertyLiteral);

	private static final PsiElementPattern.Capture<PsiElement> INLINE_DEPENDENCY_NOTATION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtStringTemplateExpression.class).with(DIRECT_DEPENDENCY_NOTATION));

	private static final PsiElementPattern.Capture<PsiElement> MAP_LITERAL_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtStringTemplateExpression.class).with(VERSION_NAMED_ARGUMENT_LITERAL));

	private static final PsiElementPattern.Capture<PsiElement> MAP_PROPERTY_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtNameReferenceExpression.class).with(VERSION_NAMED_ARGUMENT_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_PREFER_LITERAL_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtStringTemplateExpression.class).with(PREFER_VERSION_BLOCK_LITERAL));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_STRICTLY_LITERAL_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtStringTemplateExpression.class).with(STRICTLY_VERSION_BLOCK_LITERAL));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_PREFER_PROPERTY_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtNameReferenceExpression.class).with(PREFER_VERSION_BLOCK_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_STRICTLY_PROPERTY_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtNameReferenceExpression.class).with(STRICTLY_VERSION_BLOCK_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> PLUGIN_DSL_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtStringTemplateExpression.class).with(PLUGIN_VERSION_LITERAL));

	private static final PsiElementPattern.Capture<PsiElement> BACKING_VERSION_PROPERTY = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtStringTemplateExpression.class).with(BACKING_VERSION_PROPERTY_LITERAL));

	public KotlinCompletionContributor() {

		// implementation("group:name:version") and
		// platform/enforcedPlatform/mavenBom string notation.
		extend(CompletionType.BASIC, INLINE_DEPENDENCY_NOTATION, provider);

		// implementation(group = "group", name = "name", version = "version").
		extend(CompletionType.BASIC, MAP_LITERAL_VERSION, provider);

		// implementation(group = "group", name = "name", version = versionProperty).
		extend(CompletionType.BASIC, MAP_PROPERTY_VERSION, provider);

		// implementation("group:name") { version { prefer("version") } }.
		extend(CompletionType.BASIC, VERSION_BLOCK_PREFER_LITERAL_VERSION, provider);

		// implementation("group:name") { version { strictly("version") } }.
		extend(CompletionType.BASIC, VERSION_BLOCK_STRICTLY_LITERAL_VERSION, provider);

		// implementation("group:name") { version { prefer(versionProperty) } }.
		extend(CompletionType.BASIC, VERSION_BLOCK_PREFER_PROPERTY_VERSION, provider);

		// implementation("group:name") { version { strictly(versionProperty) } }.
		extend(CompletionType.BASIC, VERSION_BLOCK_STRICTLY_PROPERTY_VERSION, provider);

		// plugins { id("plugin.id") version "version" }.
		extend(CompletionType.BASIC, PLUGIN_DSL_VERSION, provider);

		// Kotlin local/extra property version literals used as backing version values.
		extend(CompletionType.BASIC, BACKING_VERSION_PROPERTY, provider);
	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {

		if (typeChar == ':') {
			return INLINE_DEPENDENCY_NOTATION.accepts(position) && isCompactNotationBeforeVersion(position);
		}

		return ReleasesCompletionProvider.isVersionCharacter(typeChar) && isSupportedCompletionSite(position);
	}

	private static PsiElementPattern.Capture<PsiElement> insideGradleKotlinFile(
			ElementPattern<? extends PsiElement> pattern) {

		return PlatformPatterns.psiElement()
				.inside(pattern)
				.inside(PlatformPatterns.psiFile().with(IS_GRADLE_KOTLIN_DSL_FILE));
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

	private static String getVersionPrefix(CompletionParameters parameters, KtStringTemplateExpression literal) {

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
		if (KotlinDslParser.isDirectDependencyNotationLiteral(literal)) {
			prefixStart = text.lastIndexOf(':', prefixEnd - 1) + 1;
		} else {
			prefixStart = getStringContentStart(text);
		}

		if (prefixStart < 0 || prefixStart > prefixEnd) {
			return "";
		}

		return text.substring(prefixStart, prefixEnd);
	}

	private static int getStringContentStart(String text) {
		return text.startsWith("\"\"\"") ? 3 : text.startsWith("\"") ? 1 : 0;
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
