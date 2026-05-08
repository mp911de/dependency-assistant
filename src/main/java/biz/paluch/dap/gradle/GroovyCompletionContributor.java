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

import biz.paluch.dap.assistant.ReleasesCompletionProvider;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.patterns.GroovyPatterns;

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

			if (CATALOG_ACCESSOR.accepts(parameters.getPosition())) {
				return result.withPrefixMatcher("");
			}

			GrLiteral literal = PsiTreeUtil.getParentOfType(parameters.getPosition(), GrLiteral.class, false);
			if (literal == null) {
				return result;
			}

			return result.withPrefixMatcher(getVersionPrefix(parameters, literal));
		}

	};

	private static final PatternCondition<PsiFile> IS_GRADLE_GROOVY_DSL_FILE = new PatternCondition<>(
			"isGradleGroovyDslFile") {

		@Override
		public boolean accepts(PsiFile psiFile, ProcessingContext processingContext) {
			return GradleUtils.isGroovyDsl(psiFile);
		}

	};

	private static final PatternCondition<GrLiteral> DIRECT_DEPENDENCY_NOTATION = new PatternCondition<>(
			"directDependencyNotation") {

		@Override
		public boolean accepts(GrLiteral literal, ProcessingContext context) {
			return GradleParser.isStringLiteral(literal) && GradleParser.isDirectDependencyNotationLiteral(literal);
		}

	};

	private static final PatternCondition<GrLiteral> VERSION_NAMED_ARGUMENT_LITERAL = new PatternCondition<>(
			"versionNamedArgumentLiteral") {

		@Override
		public boolean accepts(GrLiteral literal, ProcessingContext context) {
			return GradleParser.isStringLiteral(literal) && GradleParser.isVersionNamedArgumentLiteral(literal);
		}

	};

	private static final PatternCondition<GrReferenceExpression> VERSION_NAMED_ARGUMENT_REFERENCE = new PatternCondition<>(
			"versionNamedArgumentReference") {

		@Override
		public boolean accepts(GrReferenceExpression reference, ProcessingContext context) {
			return GradleParser.isVersionNamedArgumentReference(reference);
		}

	};

	private static final PatternCondition<GrLiteral> PREFER_VERSION_BLOCK_LITERAL = new PatternCondition<>(
			"preferVersionBlockLiteral") {

		@Override
		public boolean accepts(GrLiteral literal, ProcessingContext context) {
			return GradleParser.isStringLiteral(literal)
					&& GradleParser.isVersionBlockLiteral(literal, GradleVersionConstraint.PREFER);
		}

	};

	private static final PatternCondition<GrLiteral> STRICTLY_VERSION_BLOCK_LITERAL = new PatternCondition<>(
			"strictlyVersionBlockLiteral") {

		@Override
		public boolean accepts(GrLiteral literal, ProcessingContext context) {
			return GradleParser.isStringLiteral(literal)
					&& GradleParser.isVersionBlockLiteral(literal, GradleVersionConstraint.STRICTLY);
		}

	};

	private static final PatternCondition<GrReferenceExpression> PREFER_VERSION_BLOCK_REFERENCE = new PatternCondition<>(
			"preferVersionBlockReference") {

		@Override
		public boolean accepts(GrReferenceExpression reference, ProcessingContext context) {
			return GradleParser.isVersionBlockReference(reference, GradleVersionConstraint.PREFER);
		}

	};

	private static final PatternCondition<GrReferenceExpression> STRICTLY_VERSION_BLOCK_REFERENCE = new PatternCondition<>(
			"strictlyVersionBlockReference") {

		@Override
		public boolean accepts(GrReferenceExpression reference, ProcessingContext context) {
			return GradleParser.isVersionBlockReference(reference, GradleVersionConstraint.STRICTLY);
		}

	};

	private static final PatternCondition<GrLiteral> PLUGIN_VERSION_LITERAL = new PatternCondition<>(
			"pluginVersionLiteral") {

		@Override
		public boolean accepts(GrLiteral literal, ProcessingContext context) {
			return GradleParser.isStringLiteral(literal) && GradleParser.isPluginVersionLiteral(literal);
		}

	};

	private static final PatternCondition<GrLiteral> BACKING_VERSION_PROPERTY_LITERAL = new PatternCondition<>(
			"backingVersionPropertyLiteral") {

		@Override
		public boolean accepts(GrLiteral literal, ProcessingContext context) {
			return GradleParser.isStringLiteral(literal) && GradleParser.isBackingVersionPropertyLiteral(literal);
		}

	};

	private static final PatternCondition<GrReferenceExpression> CATALOG_ACCESSOR_REFERENCE = new PatternCondition<>(
			"catalogAccessorReference") {

		@Override
		public boolean accepts(GrReferenceExpression reference, ProcessingContext context) {
			return GradleParser.isCatalogAccessorReference(reference);
		}

	};

	private static final PsiElementPattern.Capture<PsiElement> INLINE_DEPENDENCY_NOTATION = insideGradleGroovyFile(
			GroovyPatterns.groovyLiteralExpression().with(DIRECT_DEPENDENCY_NOTATION));

	private static final PsiElementPattern.Capture<PsiElement> MAP_LITERAL_VERSION = insideGradleGroovyFile(
			GroovyPatterns.groovyLiteralExpression().with(VERSION_NAMED_ARGUMENT_LITERAL));

	private static final PsiElementPattern.Capture<PsiElement> MAP_PROPERTY_VERSION = insideGradleGroovyFile(
			PlatformPatterns.psiElement(GrReferenceExpression.class).with(VERSION_NAMED_ARGUMENT_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_PREFER_LITERAL_VERSION = insideGradleGroovyFile(
			GroovyPatterns.groovyLiteralExpression().with(PREFER_VERSION_BLOCK_LITERAL));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_STRICTLY_LITERAL_VERSION = insideGradleGroovyFile(
			GroovyPatterns.groovyLiteralExpression().with(STRICTLY_VERSION_BLOCK_LITERAL));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_PREFER_PROPERTY_VERSION = insideGradleGroovyFile(
			PlatformPatterns.psiElement(GrReferenceExpression.class).with(PREFER_VERSION_BLOCK_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> VERSION_BLOCK_STRICTLY_PROPERTY_VERSION = insideGradleGroovyFile(
			PlatformPatterns.psiElement(GrReferenceExpression.class).with(STRICTLY_VERSION_BLOCK_REFERENCE));

	private static final PsiElementPattern.Capture<PsiElement> PLUGIN_DSL_VERSION = insideGradleGroovyFile(
			GroovyPatterns.groovyLiteralExpression().with(PLUGIN_VERSION_LITERAL));

	private static final PsiElementPattern.Capture<PsiElement> BACKING_VERSION_PROPERTY = insideGradleGroovyFile(
			GroovyPatterns.groovyLiteralExpression().with(BACKING_VERSION_PROPERTY_LITERAL));

	private static final PsiElementPattern.Capture<PsiElement> CATALOG_ACCESSOR = insideGradleGroovyFile(
			PlatformPatterns.psiElement(GrReferenceExpression.class).with(CATALOG_ACCESSOR_REFERENCE));

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

		// Groovy catalog accessor references such as implementation libs.some.alias.
		extend(CompletionType.BASIC, CATALOG_ACCESSOR, provider);
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

	private static boolean isSupportedCompletionSite(PsiElement position) {

		return INLINE_DEPENDENCY_NOTATION.accepts(position)
				|| MAP_LITERAL_VERSION.accepts(position)
				|| MAP_PROPERTY_VERSION.accepts(position)
				|| VERSION_BLOCK_PREFER_LITERAL_VERSION.accepts(position)
				|| VERSION_BLOCK_STRICTLY_LITERAL_VERSION.accepts(position)
				|| VERSION_BLOCK_PREFER_PROPERTY_VERSION.accepts(position)
				|| VERSION_BLOCK_STRICTLY_PROPERTY_VERSION.accepts(position)
				|| PLUGIN_DSL_VERSION.accepts(position)
				|| BACKING_VERSION_PROPERTY.accepts(position)
				|| CATALOG_ACCESSOR.accepts(position);
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
		int dummy = text.indexOf("IntellijIdeaRulezzz");
		if (dummy == -1) {
			dummy = text.indexOf("intellijIdeaRulezzz");
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
