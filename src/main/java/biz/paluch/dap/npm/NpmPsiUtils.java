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

package biz.paluch.dap.npm;

import biz.paluch.dap.util.PsiVisitors;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Shared helpers that translate an NPM dependency PSI element into the text
 * range covered by its underlying {@link NpmVersionExpression} variant.
 *
 * <p>The IDE annotator and line marker compute their highlight using the
 * variant's own {@link NpmVersionExpression#replaceableRange(String)} so that
 * {@code Exact}, {@code RangeUpper}, {@code Alias}, {@code Prefix}, and
 * {@code Git} entries each expose the same sub-range that the updater rewrites.
 *
 * @author Mark Paluch
 */
final class NpmPsiUtils {

	private NpmPsiUtils() {
	}

	/**
	 * Return the text range that covers the variant-defined replaceable range of
	 * the NPM dependency value containing the given element. Returns the element's
	 * own range when no enclosing dependency value can be located, so the IDE
	 * extensions degrade gracefully outside dependency contexts.
	 */
	static TextRange getVersionRange(PsiElement element) {

		JsonStringLiteral literal = findDependencyLiteral(element);
		if (literal == null) {
			return element.getTextRange();
		}

		String rawValue = literal.getValue();
		NpmVersionExpression expression = NpmPackageParser.parse(rawValue);
		if (expression == null) {
			return literal.getTextRange();
		}

		TextRange replaceable = expression.replaceableRange(rawValue);
		// JSON string literals include the surrounding double quotes; offset by one.
		int literalStart = literal.getTextRange().getStartOffset() + 1;
		return new TextRange(literalStart + replaceable.getStartOffset(), literalStart + replaceable.getEndOffset());
	}

	/**
	 * Return whether the given JSON string literal is properly terminated with an
	 * unescaped double-quote character. An unterminated literal arises when the
	 * user is mid-typing and the parser has not yet observed the closing quote.
	 * @param literal the literal to inspect; must not be {@literal null}.
	 * @return {@literal true} if the literal text ends with an unescaped {@code "};
	 * {@literal false} otherwise.
	 */
	static boolean isClosed(JsonStringLiteral literal) {
		String text = literal.getText();
		return text.length() >= 2 && text.charAt(text.length() - 1) == '"'
				&& (text.length() < 3 || text.charAt(text.length() - 2) != '\\');
	}

	static @Nullable JsonStringLiteral findDependencyLiteral(PsiElement element) {

		PsiElement unleaf = PsiVisitors.unleaf(element);

		JsonStringLiteral literal = unleaf instanceof JsonStringLiteral self ? self
				: PsiTreeUtil.getParentOfType(unleaf, JsonStringLiteral.class, true);
		if (literal == null || literal.isPropertyName()) {
			return null;
		}

		if (!(literal.getParent() instanceof JsonProperty entry)) {
			return null;
		}

		if (!(entry.getParent() instanceof JsonObject siblings)
				|| !(siblings.getParent() instanceof JsonProperty parentProperty)) {
			return null;
		}

		String parentName = parentProperty.getName();
		return ("dependencies".equals(parentName) || "devDependencies".equals(parentName)) ? literal : null;
	}

}
