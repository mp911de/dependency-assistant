package biz.paluch.dap.util;

import java.util.function.Consumer;
import java.util.function.Predicate;

import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * Utility to create {@link com.intellij.psi.PsiElement}
 * {@link com.intellij.psi.PsiElementVisitor visitors}.
 * @author Mark Paluch
 */
public abstract class PsiVisitors {

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
	 * {@code true}) only for elements that are subtypes of {@code psiElementType}.
	 * <p>The visitor stops subtree navigation once {@link Predicate
	 * actionAndExitCondition} returns {@code true}.
	 * @param actionAndExitCondition the action to invoke. If the predicate returns
	 * {@code true}, then the visitor will stop to navigate the tree.
	 * @return a new {@link PsiRecursiveElementVisitor}.
	 */
	public static <T> PsiRecursiveElementVisitor visitTreeUntil(Class<T> psiElementType,
			Predicate<T> actionAndExitCondition) {
		return new ExitConditionVisitor(psiElementType::isInstance,
				it -> actionAndExitCondition.test(psiElementType.cast(it)));
	}


}
