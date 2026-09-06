/*
 * Copyright 2026-present the original author or authors.
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

import biz.paluch.dap.util.PsiElements;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Locates NPM dependency literals and their version-bearing ranges.
 * <p>Prefix ranges can be highlighted even though the updater cannot rewrite
 * them.
 *
 * @author Mark Paluch
 */
class NpmPsiUtils {

	private NpmPsiUtils() {
	}

	/**
	 * Return the version-bearing range in absolute file offsets.
	 * <p>Fall back to the enclosing literal or element range when no supported
	 * expression is available.
	 */
	static TextRange getVersionRange(PsiElement element) {

		JsonStringLiteral literal = findDependencyLiteral(element);
		if (literal == null) {
			return element.getTextRange();
		}

		String rawValue = literal.getValue();
		NpmVersionExpression expression = NpmVersionExpression.parse(rawValue);
		if (expression == null) {
			return literal.getTextRange();
		}

		TextRange replaceable = expression.replaceableRange(rawValue);
		// JSON string literals include the surrounding double quotes; offset by one.
		int literalStart = literal.getTextRange().getStartOffset() + 1;
		return new TextRange(literalStart + replaceable.getStartOffset(), literalStart + replaceable.getEndOffset());
	}

	static boolean isClosed(JsonStringLiteral literal) {
		String text = literal.getText();
		return text.length() >= 2 && text.charAt(text.length() - 1) == '"'
				&& (text.length() < 3 || text.charAt(text.length() - 2) != '\\');
	}

	static @Nullable JsonStringLiteral findDependencyLiteral(PsiElement element) {

		PsiElement unleaf = PsiElements.unleaf(element);

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
