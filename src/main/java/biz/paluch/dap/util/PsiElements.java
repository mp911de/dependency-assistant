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

package biz.paluch.dap.util;

import java.util.function.Predicate;

import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

/**
 * Utilities for PSI traversal and element normalization.
 *
 * @author Mark Paluch
 */
public abstract class PsiElements {

	/**
	 * Return the parent of a {@link LeafPsiElement leaf}, or the element itself.
	 */
	public static PsiElement unleaf(PsiElement element) {
		return element instanceof LeafPsiElement ? element.getParent() : element;
	}

	/**
	 * Create a recursive visitor that applies the predicate to elements of the
	 * given type. Returning {@code true} stops further descent.
	 * <p>The predicate may still receive matching siblings after returning
	 * {@code true}. Their descendants are not visited.
	 */
	public static <T> PsiRecursiveElementVisitor visitTreeUntil(Class<T> psiElementType,
			Predicate<T> actionAndExitCondition) {
		return new ExitConditionVisitor(psiElementType::isInstance,
				it -> actionAndExitCondition.test(psiElementType.cast(it)));
	}

	/**
	 * Find the nearest matching element without crossing a
	 * {@link PsiFileSystemItem} boundary.
	 * @param strict whether to exclude the starting element.
	 * @return the matching element, or {@literal null} if none is found before the
	 * boundary. The boundary itself is excluded.
	 */
	@Contract("null, _, _ -> null")
	public static @Nullable PsiElement findFirstParent(@Nullable PsiElement element,
			boolean strict, Condition<? super PsiElement> condition) {
		if (strict && element != null) {
			element = element.getParent();
		}
		while (element != null) {
			if (element instanceof PsiFileSystemItem) {
				return null;
			}
			if (condition.value(element)) {
				return element;
			}
			element = element.getParent();
		}
		return null;
	}

}
