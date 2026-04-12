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
