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

import biz.paluch.dap.util.PsiVisitors;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.psi.KtArrayAccessExpression;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jspecify.annotations.Nullable;

/**
 * Kotlin DSL {@code extra["key"]} assignment patterns beyond plain
 * {@code extra["k"] = "v"}.
 *
 * @author Mark Paluch
 */
class KotlinDslExtraSupport {

	private KotlinDslExtraSupport() {
	}


	/**
	 * Locates the PSI element whose text should be updated or highlighted as the
	 * declared value for {@code propertyKey}.
	 */
	public static @Nullable PsiPropertyValueElement findExtraPropertyLocation(PsiFile file, String propertyKey) {

		KtStringTemplateExpression expression = findExtraPropertyElement(file, propertyKey);
		if (expression == null) {
			return null;
		}

		return new PsiPropertyValueElement(expression, propertyKey, KotlinDslUtils.getText(expression));
	}

	/**
	 * Locates the PSI element whose text should be updated or highlighted as the
	 * declared value for {@code propertyKey}.
	 */
	public static @Nullable KtStringTemplateExpression findExtraPropertyElement(PsiFile file, String propertyKey) {

		KtBinaryExpression assign = findExtraAssignment(file, propertyKey);
		if (assign == null) {
			return null;
		}
		KtExpression right = assign.getRight();
		if (right instanceof KtStringTemplateExpression st) {
			return st;
		}
		if (right instanceof KtNameReferenceExpression ref && "it".equals(ref.getReferencedName())) {
			KtStringTemplateExpression template = KotlinDslUtils.findAlsoReceiverStringTemplate(ref);
			return template;
		}
		if (right instanceof KtCallExpression call) {
			return KotlinDslUtils.findBuildStringAppendLiteral(call);
		}
		return null;
	}

	public static @Nullable KtBinaryExpression findExtraAssignment(PsiFile file, String propertyKey) {

		@Nullable
		KtBinaryExpression[] found = {null};
		file.accept(PsiVisitors.visitTreeUntil(KtBinaryExpression.class, expression -> {

			if (matchesExtraKey(expression, propertyKey)) {
				found[0] = expression;
				return true;
			}

			return false;
		}));

		return found[0];
	}

	static boolean matchesExtraKey(KtBinaryExpression expr, String propertyKey) {

		if (!"=".equals(expr.getOperationReference().getText())) {
			return false;
		}

		if (!(expr.getLeft() instanceof KtArrayAccessExpression arrayAccess)
				|| arrayAccess.getIndexExpressions().isEmpty()) {
			return false;
		}

		KtExpression receiver = arrayAccess.getArrayExpression();
		if (!(receiver instanceof KtNameReferenceExpression nameRef) || !"extra".equals(nameRef.getReferencedName())) {
			return false;
		}

		if (!(arrayAccess.getIndexExpressions().get(0) instanceof KtStringTemplateExpression keyTemplate)) {
			return false;
		}

		String key = KotlinDslUtils.getText(keyTemplate);
		return propertyKey.equals(key);
	}

}
