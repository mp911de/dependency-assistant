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

/**
 * Extension to {@link PsiRecursiveElementVisitor} that simplifies usage and
 * declaration so that visitors that want to visit the entire tree can be
 * constructed simpler and feature a less error-prone implementation for
 * entering the recursion loop.
 *
 * @author Mark Paluch
 */
class ConditionalPsiRecursiveElementVisitor extends PsiRecursiveElementVisitor {

	private final Predicate<PsiElement> actionFilter;

	private final Consumer<PsiElement> action;

	ConditionalPsiRecursiveElementVisitor(Predicate<PsiElement> actionFilter, Consumer<PsiElement> action) {
		this.actionFilter = actionFilter;
		this.action = action;
	}

	@Override
	public void visitElement(PsiElement element) {
		if (actionFilter.test(element)) {
			action.accept(element);
		}
		super.visitElement(element);
	}

}
