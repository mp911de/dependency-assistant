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

package biz.paluch.dap.util;

import java.util.function.Consumer;
import java.util.function.Predicate;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.psi.impl.source.tree.LeafPsiElement;

/**
 * Utility to create {@link com.intellij.psi.PsiElement}
 * {@link com.intellij.psi.PsiElementVisitor visitors}.
 * @author Mark Paluch
 */
public abstract class PsiVisitors {

	/**
	 * Ensure the element is not a {@link LeafPsiElement leaf} by returning its
	 * parent {@code element} was a leaf.
	 * @param element the potential leaf element.
	 * @return {@code element} if it is not a {@link LeafPsiElement leaf}, otherwise
	 * its {@link PsiElement#getParent() parent}.
	 */
	public static PsiElement unleaf(PsiElement element) {
		return element instanceof LeafPsiElement ? element.getParent() : element;
	}

	/**
	 * Create a {@link PsiRecursiveElementVisitor} to visit the entire PSI tree
	 * recursively and invoke {@code action} only for elements that are subtypes of
	 * {@code psiElementType}.
	 * @param action the action to invoke.
	 * @return a new {@link PsiRecursiveElementVisitor}.
	 */
	public static <T> PsiRecursiveElementVisitor visitTree(Class<T> psiElementType,
			Consumer<T> action) {
		return new ConditionalPsiRecursiveElementVisitor(psiElementType::isInstance,
				it -> action.accept(psiElementType.cast(it)));
	}

	/**
	 * Create a {@link PsiRecursiveElementVisitor} to visit the entire PSI tree
	 * recursively and invoke {@code actionAndExitCondition} (until returning
	 * {@literal true}) only for elements that are subtypes of
	 * {@code psiElementType}.
	 * <p>The visitor stops subtree navigation once {@link Predicate
	 * actionAndExitCondition} returns {@literal true}.
	 * @param actionAndExitCondition the action to invoke. If the conditional
	 * returns {@literal true}, then the visitor will stop to navigate the tree.
	 * @return a new {@link PsiRecursiveElementVisitor}.
	 */
	public static <T> PsiRecursiveElementVisitor visitTreeUntil(Class<T> psiElementType,
			Predicate<T> actionAndExitCondition) {
		return new ExitConditionVisitor(psiElementType::isInstance,
				it -> actionAndExitCondition.test(psiElementType.cast(it)));
	}


}
