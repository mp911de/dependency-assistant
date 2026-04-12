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
