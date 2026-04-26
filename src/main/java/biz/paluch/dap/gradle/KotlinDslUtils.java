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

import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLambdaArgument;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.ValueArgument;
import org.jspecify.annotations.Nullable;

/**
 * Internal Kotlin DSL PSI helpers used by parsers, lookup-site locators, and
 * update routines.
 *
 * <p>This class centralizes Kotlin build-script traversal rules shared across
 * parser infrastructure. It is not intended as a general-purpose Kotlin PSI
 * abstraction.
 *
 * @author Mark Paluch
 */
class KotlinDslUtils {

	private KotlinDslUtils() {
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	/**
	 * Return whether the call can declare a supported dependency or plugin.
	 */
	public static boolean isDependencyCall(KtCallElement call) {

		String methodName = getKotlinCallName(call);
		if (StringUtils.isEmpty(methodName)) {
			return false;
		}

		if (GradleUtils.isDependencySection(methodName) || GradleUtils.isPlatformSection(methodName)) {
			return true;
		}
		return GradleUtils.isPlugin(methodName) && isInsidePluginsBlock(call);
	}

	static @Nullable String getKotlinCallName(KtCallElement call) {
		PsiElement callee = call.getCalleeExpression();
		if (callee instanceof KtNameReferenceExpression ref) {
			return ref.getReferencedName();
		}
		return callee != null ? callee.getText() : null;
	}

	static boolean isInsidePluginsBlock(PsiElement element) {
		return isInsideBlock(element, GradleUtils::isPluginSection);
	}

	static boolean isInsideBlock(PsiElement element, Predicate<String> predicate) {
		PsiElement parent = element.getParent();
		while (parent != null && !(parent instanceof PsiFile)) {
			if (parent instanceof KtCallExpression parentCall) {
				String name = getKotlinCallName(parentCall);
				if (name != null && predicate.test(name)) {
					return true;
				}
			}
			parent = parent.getParent();
		}
		return false;
	}

	/**
	 * Return the trailing lambda argument, if present.
	 */
	public static @Nullable KtLambdaExpression getLambdaArgument(KtCallExpression call) {

		KtLambdaArgument lambdaArg = call.getLambdaArguments().isEmpty() ? null : call.getLambdaArguments().get(0);
		if (lambdaArg == null) {
			return null;
		}

		KtExpression expr = lambdaArg.getArgumentExpression();
		return expr instanceof KtLambdaExpression lambda ? lambda : null;
	}

	// -------------------------------------------------------------------------
	// Version catalog (Kotlin {@code libs.…})
	// -------------------------------------------------------------------------

	static @Nullable KtExpression getFirstValueArgument(KtCallElement call) {

		for (ValueArgument va : call.getValueArguments()) {
			return va.getArgumentExpression();
		}
		return null;
	}

}
