package biz.paluch.dap.util;

import java.util.function.Consumer;
import java.util.function.Predicate;

import com.intellij.openapi.util.Predicates;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * Utility to create {@link com.intellij.psi.PsiElement}
 * {@link com.intellij.psi.PsiElementVisitor visitors}.
 * @author Mark Paluch
 */
public abstract class PsiVisitors {

	/**
	 * Create a simple {@link PsiRecursiveElementVisitor} to visit the entire PSI
	 * tree and invoke {@code action}.
	 * @param action the action to invoke.
	 * @return a new {@link PsiRecursiveElementVisitor}.
	 */
	public static PsiRecursiveElementVisitor recursive(Consumer<PsiElement> action) {
		return new ConditionalPsiRecursiveElementVisitor(Predicates.alwaysTrue(), action);
	}

	/**
	 * Create a {@link PsiRecursiveElementVisitor} to visit the entire PSI tree
	 * recursively and invoke {@code action} only for elements that match
	 * {@link Predicate actionFilter}.
	 * @param actionFilter the action filter to check whether to invoke
	 * {@link Consumer action}.
	 * @param action the action to invoke.
	 * @return a new {@link PsiRecursiveElementVisitor}.
	 */
	public static PsiRecursiveElementVisitor conditional(Predicate<PsiElement> actionFilter,
			Consumer<PsiElement> action) {
		return new ConditionalPsiRecursiveElementVisitor(actionFilter, action);
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
	 * recursively and invoke {@code action} only for elements that are subtypes of
	 * {@code psiElementType} and match {@link Predicate actionFilter}.
	 * @param action the action to invoke.
	 * @param actionFilter the action filter to check whether to invoke
	 * {@link Consumer action}.
	 * @return a new {@link PsiRecursiveElementVisitor}.
	 */
	public static <T> PsiRecursiveElementVisitor conditionalVisitTree(Class<T> psiElementType,
			Predicate<T> actionFilter, Consumer<T> action) {
		return new ConditionalPsiRecursiveElementVisitor(
				obj -> psiElementType.isInstance(obj) && actionFilter.test(psiElementType.cast(obj)),
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
