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
 * Utilities for normalizing PSI elements, creating recursive visitors, and
 * walking parent chains.
 *
 * @author Mark Paluch
 */
public abstract class PsiElements {

	/**
	 * Return the element itself, or its parent when the element is a
	 * {@link LeafPsiElement leaf}.
	 * @param element the potential leaf element.
	 * @return {@code element} if it is not a {@link LeafPsiElement leaf}, otherwise
	 * its {@link PsiElement#getParent() parent}.
	 */
	public static PsiElement unleaf(PsiElement element) {
		return element instanceof LeafPsiElement ? element.getParent() : element;
	}

	/**
	 * Create a recursive visitor that applies a predicate to elements of the given
	 * type and prunes further descent after the predicate signals completion.
	 *
	 * <p>Parent-controlled PSI dispatch may still pass matching sibling elements to
	 * the predicate after it first returns {@code true}. Their descendants are not
	 * visited.
	 *
	 * @param <T> the selected PSI element type.
	 * @param psiElementType the element type passed to the predicate.
	 * @param actionAndExitCondition the action to invoke. Returning {@code true}
	 * requests that recursive descent stop.
	 * @return a visitor for passing to a root {@link PsiElement}.
	 */
	public static <T> PsiRecursiveElementVisitor visitTreeUntil(Class<T> psiElementType,
			Predicate<T> actionAndExitCondition) {
		return new ExitConditionVisitor(psiElementType::isInstance,
				it -> actionAndExitCondition.test(psiElementType.cast(it)));
	}

	/**
	 * Find the first ancestor of {@code element} that satisfies {@link Condition
	 * condition}.
	 * <p>Parent traversal stops when the parent element is a
	 * {@link PsiFileSystemItem file}.
	 * @param element the starting element to search from.
	 * @param strict whether to exclude {@code element} and start at its parent.
	 * @param condition determines whether an ancestor element satisfies the search
	 * criteria.
	 * @return the first ancestor of {@code element} that satisfies
	 * {@link Condition}, or {@literal null} if no such ancestor exists before a
	 * {@link PsiFileSystemItem} boundary.
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
