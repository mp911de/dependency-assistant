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

import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.assistant.ReleasesCompletionProvider;
import biz.paluch.dap.util.PatternConditions;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
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

			return result.withPrefixMatcher(GradleCompletionSupport.getVersionPrefix(parameters, literal));
		}

		@Override
		protected LookupElementBuilder postProcess(CompletionParameters parameters, LookupElementBuilder builder,
				PsiElement element, ArtifactRelease option) {

			KtStringTemplateExpression literal = getLiteral(element);
			if (literal == null || !KotlinDslParser.isDirectDependencyNotationLiteral(literal)) {
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

	// Inline dependency/platform notation such as
	// `implementation("group:name:version")`.
	private static final PatternCondition<PsiElement> DIRECT_DEPENDENCY_NOTATION_POSITION = literalPosition(
			"directDependencyNotationPosition", KotlinDslParser::isDirectDependencyNotationLiteral);

	// Map notation with a literal version such as `version = "1.0.0"`.
	private static final PatternCondition<PsiElement> VERSION_NAMED_ARGUMENT_LITERAL_POSITION = literalPosition(
			"versionNamedArgumentLiteralPosition", KotlinDslParser::isVersionNamedArgumentLiteral);

	// Map notation with a property reference such as `version = junit`.
	private static final PatternCondition<KtNameReferenceExpression> VERSION_NAMED_ARGUMENT_REFERENCE = PatternConditions
			.conditional("versionNamedArgumentReference", KotlinDslParser::isVersionNamedArgumentReference);

	// Version blocks with literal preferred versions such as `prefer("1.0.0")`.
	private static final PatternCondition<PsiElement> PREFER_VERSION_BLOCK_POSITION = literalPosition(
			"preferVersionBlockPosition",
			literal -> KotlinDslParser.isVersionBlockLiteral(literal, GradleVersionConstraint.PREFER));

	// Version blocks with literal strict versions such as `strictly("1.0.0")`.
	private static final PatternCondition<PsiElement> STRICTLY_VERSION_BLOCK_POSITION = literalPosition(
			"strictlyVersionBlockPosition",
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
	private static final PatternCondition<PsiElement> PLUGIN_VERSION_POSITION = literalPosition(
			"pluginVersionPosition", KotlinDslParser::isPluginVersionLiteral);

	// Backing Kotlin/extra properties such as `val junit = "1.0.0"` or
	// `extra["junit"] = "1.0.0"`.
	private static final PatternCondition<PsiElement> BACKING_VERSION_PROPERTY_POSITION = literalPosition(
			"backingVersionPropertyPosition", KotlinDslParser::isBackingVersionPropertyLiteral);

	private static final PsiElementPattern.Capture<PsiElement> INLINE_DEPENDENCY_NOTATION = inGradleKotlinFile(
			DIRECT_DEPENDENCY_NOTATION_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> MAP_LITERAL_VERSION = inGradleKotlinFile(
			VERSION_NAMED_ARGUMENT_LITERAL_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> MAP_PROPERTY_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtNameReferenceExpression.class).with(VERSION_NAMED_ARGUMENT_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_PREFER_LITERAL_VERSION = inGradleKotlinFile(
			PREFER_VERSION_BLOCK_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_STRICTLY_LITERAL_VERSION = inGradleKotlinFile(
			STRICTLY_VERSION_BLOCK_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_PREFER_PROPERTY_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtNameReferenceExpression.class).with(PREFER_VERSION_BLOCK_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_STRICTLY_PROPERTY_VERSION = insideGradleKotlinFile(
			PlatformPatterns.psiElement(KtNameReferenceExpression.class).with(STRICTLY_VERSION_BLOCK_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> PLUGIN_DSL_VERSION = inGradleKotlinFile(
			PLUGIN_VERSION_POSITION);

	private static final PsiElementPattern.Capture<PsiElement> BACKING_VERSION_PROPERTY = inGradleKotlinFile(
			BACKING_VERSION_PROPERTY_POSITION);

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

	private static PsiElementPattern.Capture<PsiElement> inGradleKotlinFile(
			PatternCondition<? super PsiElement> condition) {

		return PlatformPatterns.psiElement()
				.with(condition)
				.inside(PlatformPatterns.psiFile().with(IS_GRADLE_KOTLIN_DSL_FILE));
	}

	private static PatternCondition<PsiElement> literalPosition(String name,
			Predicate<KtStringTemplateExpression> predicate) {

		return PatternConditions.conditional(name, position -> {
			KtStringTemplateExpression literal = PsiTreeUtil.getParentOfType(position,
					KtStringTemplateExpression.class, false);
			return literal != null && predicate.test(literal);
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

	private static KtStringTemplateExpression getLiteral(PsiElement element) {
		return element instanceof KtStringTemplateExpression literal ? literal
				: PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression.class, false);
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
