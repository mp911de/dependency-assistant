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

import java.util.function.Predicate;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * Extension to {@link PsiRecursiveElementVisitor} that simplifies usage and
 * declaration so that visitors that want to visit the entire tree can be
 * constructed simpler and feature a less error-prone implementation for
 * entering the recursion loop. Allows for exiting the tree once an action has
 * been performed.
 *
 * @author Mark Paluch
 */
class ExitConditionVisitor extends PsiRecursiveElementVisitor {

	private boolean exit = false;

	private final Predicate<PsiElement> actionFilter;

	private final Predicate<PsiElement> actionAndExitCondition;

	ExitConditionVisitor(Predicate<PsiElement> actionFilter, Predicate<PsiElement> actionAndExitCondition) {
		this.actionFilter = actionFilter;
		this.actionAndExitCondition = actionAndExitCondition;
	}

	@Override
	public void visitElement(PsiElement element) {
		if (actionFilter.test(element)) {
			if (actionAndExitCondition.test(element)) {
				this.exit = true;
				return;
			}
		}
		if (exit) {
			return;
		}
		super.visitElement(element);
	}

}
