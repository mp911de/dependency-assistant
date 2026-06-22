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

import java.util.HashSet;
import java.util.Set;

import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Groovy DSL declaration-style detection.
 *
 * <p>Also exposes the command-style platform string helpers shared with the
 * forward {@link GroovyDslParser}, since both describe where a version string
 * sits in a {@code implementation platform "g:a:1.0"} notation.
 *
 * @author Mark Paluch
 * @see DeclarationStyleDetector
 */
class GroovyDeclarationStyleDetector implements DeclarationStyleDetector {

	private static final GroovyDeclarationStyleDetector INSTANCE = new GroovyDeclarationStyleDetector();

	public static GroovyDeclarationStyleDetector getInstance() {
		return INSTANCE;
	}

	@Override
	public DeclarationStyle detect(PsiElement element) {

		GrLiteral literal = element instanceof GrLiteral lit ? lit
				: PsiTreeUtil.getParentOfType(element, GrLiteral.class, false);
		if (literal != null) {

			DeclarationStyle position = classifyLiteral(literal);
			if (position != null) {
				return position;
			}
		}

		PsiElement commandPlatformString = findCommandPlatformString(element);
		if (commandPlatformString != null) {
			return DeclarationStyle.commandPlatform(commandPlatformString, findCommandPlatformDependencyCall(element));
		}

		GrReferenceExpression reference = element instanceof GrReferenceExpression ref ? ref
				: PsiTreeUtil.getParentOfType(element, GrReferenceExpression.class, false);
		if (reference != null) {

			DeclarationStyle position = classifyReference(reference);
			if (position != null) {
				return position;
			}
		}

		return DeclarationStyle.absent();
	}

	private @Nullable DeclarationStyle classifyLiteral(GrLiteral literal) {

		if (isDirectDependencyNotationLiteral(literal)) {
			return DeclarationStyle.inline(literal, enclosingDeclarationCall(literal));
		}

		if (isVersionNamedArgumentLiteral(literal)) {
			return DeclarationStyle.mapNotation(literal, enclosingDeclarationCall(literal));
		}

		if (isVersionBlockLiteral(literal, GradleVersionConstraint.PREFER)) {
			return DeclarationStyle.versionBlock(DeclarationStyle.Kind.VERSION_BLOCK_PREFER, literal,
					versionBlockCall(literal));
		}

		if (isVersionBlockLiteral(literal, GradleVersionConstraint.STRICTLY)) {
			return DeclarationStyle.versionBlock(DeclarationStyle.Kind.VERSION_BLOCK_STRICTLY, literal,
					versionBlockCall(literal));
		}

		if (isPluginVersionLiteral(literal)) {
			GrMethodCall versionCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
			GrMethodCall idCall = versionCall != null ? findPluginIdCallForVersionCall(versionCall) : null;
			return DeclarationStyle.pluginVersion(literal, idCall);
		}

		if (isBackingVersionPropertyLiteral(literal)) {
			return DeclarationStyle.backingProperty(literal);
		}

		return null;
	}

	private @Nullable DeclarationStyle classifyReference(GrReferenceExpression reference) {

		if (isVersionNamedArgumentReference(reference)) {
			return DeclarationStyle.mapNotation(reference, enclosingDeclarationCall(reference));
		}

		if (isVersionBlockReference(reference, GradleVersionConstraint.PREFER)) {
			return DeclarationStyle.versionBlock(DeclarationStyle.Kind.VERSION_BLOCK_PREFER, reference,
					versionBlockCall(reference));
		}

		if (isVersionBlockReference(reference, GradleVersionConstraint.STRICTLY)) {
			return DeclarationStyle.versionBlock(DeclarationStyle.Kind.VERSION_BLOCK_STRICTLY, reference,
					versionBlockCall(reference));
		}

		return null;
	}

	private @Nullable GrMethodCall enclosingDeclarationCall(PsiElement versionElement) {

		GrMethodCall call = PsiTreeUtil.getParentOfType(versionElement, GrMethodCall.class);
		return call != null && isDependencyOrPlatformCall(call) ? call : null;
	}

	private @Nullable GrMethodCall versionBlockCall(PsiElement versionElement) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(versionElement, GrMethodCall.class);
		return constraintCall != null ? findVersionBlockDependencyCall(constraintCall) : null;
	}

	/**
	 * Return whether the literal carries a constant Groovy string value, that is it
	 * is not an interpolated {@code GString}. Used to gate version completion,
	 * which only applies to a constant version literal.
	 */
	boolean isConstantStringLiteral(@Nullable GrLiteral literal) {
		return literal != null && literal.getValue() instanceof String;
	}

	/**
	 * Return whether the literal is the coordinate argument in a direct dependency
	 * notation call.
	 */
	private boolean isDirectDependencyNotationLiteral(GrLiteral literal) {

		if (literal.getParent() instanceof GrNamedArgument) {
			return false;
		}

		GrMethodCall call = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		if (call != null && isDependencyOrPlatformCall(call) && isArgumentOfCall(literal, call)
				&& call.getClosureArguments().length == 0) {
			return true;
		}

		return findCommandPlatformDependencyCall(literal) != null;
	}

	/**
	 * Return whether the literal is a {@code version: '...'} value in map-style
	 * dependency notation.
	 */
	private boolean isVersionNamedArgumentLiteral(GrLiteral literal) {

		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(literal, GrNamedArgument.class);
		return namedArgument != null && GradleUtils.VERSION.equals(namedArgument.getLabelName())
				&& isExpressionOfNamedArgument(literal, namedArgument)
				&& findNamedArgumentDependencyCall(namedArgument) != null;
	}

	/**
	 * Return whether the reference is a {@code version: property} value in
	 * map-style dependency notation.
	 */
	private boolean isVersionNamedArgumentReference(GrReferenceExpression reference) {

		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(reference, GrNamedArgument.class);
		return namedArgument != null && GradleUtils.VERSION.equals(namedArgument.getLabelName())
				&& isExpressionOfNamedArgument(reference, namedArgument)
				&& findNamedArgumentDependencyCall(namedArgument) != null;
	}

	/**
	 * Return whether the literal is an argument to a version-block constraint call.
	 */
	private boolean isVersionBlockLiteral(GrLiteral literal, String constraintName) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		return constraintCall != null && constraintName.equals(GroovyDslUtils.getGroovyMethodName(constraintCall))
				&& isArgumentOfCall(literal, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the reference is an argument to a version-block constraint
	 * call.
	 */
	private boolean isVersionBlockReference(GrReferenceExpression reference, String constraintName) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(reference, GrMethodCall.class);
		return constraintCall != null && constraintName.equals(GroovyDslUtils.getGroovyMethodName(constraintCall))
				&& isArgumentOfCall(reference, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the literal is an argument to any supported version-block
	 * constraint call ({@code prefer} or {@code strictly}).
	 * <p>Consolidates the per-constraint checks into a single ancestor walk so
	 * per-element reverse resolution does not recompute the enclosing dependency
	 * call once per constraint name.
	 */
	private boolean isVersionBlockLiteral(GrLiteral literal) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		return constraintCall != null
				&& GradleVersionConstraint.isConstraint(GroovyDslUtils.getGroovyMethodName(constraintCall))
				&& isArgumentOfCall(literal, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the reference is an argument to any supported version-block
	 * constraint call ({@code prefer} or {@code strictly}).
	 */
	private boolean isVersionBlockReference(GrReferenceExpression reference) {

		GrMethodCall constraintCall = PsiTreeUtil.getParentOfType(reference, GrMethodCall.class);
		return constraintCall != null
				&& GradleVersionConstraint.isConstraint(GroovyDslUtils.getGroovyMethodName(constraintCall))
				&& isArgumentOfCall(reference, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the literal is the version argument in a Groovy plugin
	 * declaration.
	 */
	private boolean isPluginVersionLiteral(GrLiteral literal) {

		GrMethodCall call = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		return call != null && findPluginIdCallForVersionCall(call) != null && isArgumentOfCall(literal, call);
	}

	/**
	 * Return whether the literal is a Groovy local/ext property that backs a
	 * supported dependency version reference.
	 */
	private boolean isBackingVersionPropertyLiteral(GrLiteral literal) {

		GroovyExtAssignment assignment = GroovyExtAssignment.from(literal);
		if (assignment == null) {
			return false;
		}

		PsiFile file = literal.getContainingFile();
		return file != null && referencedVersionPropertyNames(file).contains(assignment.getKey());
	}

	/**
	 * Return the property names referenced by a supported declaration anywhere in
	 * the file. The result is cached and recomputed when the PSI changes, so the
	 * repeated completion-position checks do not each re-traverse the file.
	 */
	private Set<String> referencedVersionPropertyNames(PsiFile file) {
		return CachedValuesManager.getProjectPsiDependentCache(file,
				psiFile -> {

					Set<String> names = new HashSet<>();

					for (GrReferenceExpression reference : SyntaxTraverser.psiTraverser(psiFile)
							.filter(GrReferenceExpression.class)) {
						if (isVersionPropertyReference(reference)
								&& StringUtils.hasText(reference.getReferenceName())) {
							names.add(reference.getReferenceName());
						}
					}

					return names;
				});
	}

	/**
	 * Walks up from a {@code prefer} or {@code strictly} call to the enclosing
	 * dependency method call, returning the outer {@link GrMethodCall} when the
	 * full version-block structure is present, or {@literal null} otherwise.
	 */
	private @Nullable GrMethodCall findVersionBlockDependencyCall(GrMethodCall preferOrStrictlyCall) {

		GrClosableBlock versionClosure = PsiTreeUtil.getParentOfType(preferOrStrictlyCall, GrClosableBlock.class);
		if (versionClosure == null) {
			return null;
		}

		GrMethodCall versionCall = PsiTreeUtil.getParentOfType(versionClosure, GrMethodCall.class);
		if (versionCall == null || !GradleUtils.VERSION.equals(GroovyDslUtils.getGroovyMethodName(versionCall))) {
			return null;
		}

		GrClosableBlock depClosure = PsiTreeUtil.getParentOfType(versionCall, GrClosableBlock.class);
		if (depClosure == null) {
			return null;
		}

		GrMethodCall depCall = PsiTreeUtil.getParentOfType(depClosure, GrMethodCall.class);
		if (depCall == null || !isDependencyOrPlatformCall(depCall)) {
			return null;
		}

		return depCall;
	}

	@Nullable
	GrMethodCall findCommandPlatformDependencyCall(PsiElement element) {

		PsiElement stringElement = findCommandPlatformString(element);
		if (stringElement == null) {
			return null;
		}

		GrMethodCall call = PsiTreeUtil.findChildOfType(stringElement, GrMethodCall.class);
		return call != null && findCommandPlatformString(call) == stringElement ? call : null;
	}

	@Nullable
	PsiElement findCommandPlatformString(PsiElement element) {

		if (element instanceof GrMethodCall call) {
			return findCommandPlatformString(call);
		}

		PsiElement candidate = element instanceof GrReferenceExpression ? element
				: PsiTreeUtil.getParentOfType(element, GrReferenceExpression.class, false);
		if (!(candidate instanceof GrReferenceExpression referenceExpression)) {
			return null;
		}

		GrMethodCall call = PsiTreeUtil.findChildOfType(referenceExpression, GrMethodCall.class);
		return call != null && findCommandPlatformString(call) == candidate ? candidate : null;
	}

	@Nullable
	String getCommandPlatformStringText(PsiElement element) {

		PsiElement stringElement = findCommandPlatformString(element);
		return stringElement != null ? getQuotedCommandArgument(stringElement.getText()) : null;
	}

	/**
	 * Return the plugin {@code id(...)} call if {@code call} is the chained
	 * {@code version(...)} call in a Groovy plugin declaration.
	 */
	private @Nullable GrMethodCall findPluginIdCallForVersionCall(GrMethodCall call) {

		if (!(call.getInvokedExpression() instanceof GrReferenceExpression versionReference)) {
			return null;
		}

		if (!GradleUtils.VERSION.equals(versionReference.getReferenceName())) {
			return null;
		}

		if (!(versionReference.getQualifierExpression() instanceof GrMethodCall idCall)) {
			return null;
		}

		if (!GradleUtils.isPlugin(GroovyDslUtils.getGroovyMethodName(idCall))
				|| !GroovyDslUtils.isInsidePluginsBlock(idCall)) {
			return null;
		}

		return idCall;
	}

	private boolean isExpressionOfNamedArgument(PsiElement element, GrNamedArgument namedArgument) {

		PsiElement expression = namedArgument.getExpression();
		return expression != null && (expression == element || PsiTreeUtil.isAncestor(expression, element, false));
	}

	private @Nullable GrMethodCall findNamedArgumentDependencyCall(GrNamedArgument namedArgument) {

		GrMethodCall call = PsiTreeUtil.getParentOfType(namedArgument, GrMethodCall.class);
		return call != null && isDependencyOrPlatformCall(call) ? call : null;
	}

	private boolean isVersionPropertyReference(GrReferenceExpression reference) {
		return isVersionNamedArgumentReference(reference)
				|| isVersionBlockReference(reference)
				|| isReferenceInsideSupportedVersionLiteral(reference);
	}

	private boolean isReferenceInsideSupportedVersionLiteral(GrReferenceExpression reference) {

		GrLiteral literal = PsiTreeUtil.getParentOfType(reference, GrLiteral.class);
		return literal != null && isSupportedVersionLiteral(literal);
	}

	private boolean isSupportedVersionLiteral(GrLiteral literal) {
		return isDirectDependencyNotationLiteral(literal)
				|| isVersionNamedArgumentLiteral(literal)
				|| isVersionBlockLiteral(literal)
				|| isPluginVersionLiteral(literal);
	}

	private boolean isDependencyOrPlatformCall(GrMethodCall call) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);
		return GradleUtils.isDependencySection(methodName) || GradleUtils.isPlatformSection(methodName);
	}

	private boolean isArgumentOfCall(PsiElement element, GrMethodCall call) {

		for (PsiElement argument : call.getArgumentList().getAllArguments()) {
			if (argument == element || PsiTreeUtil.isAncestor(argument, element, false)) {
				return true;
			}
		}

		return false;
	}

	private @Nullable PsiElement findCommandPlatformString(GrMethodCall call) {

		if (!GradleUtils.isDependencySection(GroovyDslUtils.getGroovyMethodName(call))
				|| !hasPlatformArgument(call)) {
			return null;
		}

		if (call.getParent() instanceof GrReferenceExpression referenceExpression
				&& getQuotedCommandArgument(referenceExpression.getText()) != null) {
			return referenceExpression;
		}

		return null;
	}

	private boolean hasPlatformArgument(GrMethodCall call) {

		for (GrReferenceExpression reference : SyntaxTraverser.psiTraverser(call)
				.filter(GrReferenceExpression.class)) {
			if (reference != call.getInvokedExpression()
					&& GradleUtils.isPlatformSection(reference.getReferenceName())) {
				return true;
			}
		}

		return false;
	}

	@Nullable
	String getQuotedCommandArgument(String text) {
		int singleQuote = text.indexOf('\'');
		int doubleQuote = text.indexOf('"');
		int start;
		if (singleQuote == -1) {
			start = doubleQuote;
		} else if (doubleQuote == -1) {
			start = singleQuote;
		} else {
			start = Math.min(singleQuote, doubleQuote);
		}

		if (start == -1) {
			return null;
		}

		char quote = text.charAt(start);
		int end = text.lastIndexOf(quote);
		return end > start ? text.substring(start + 1, end) : null;
	}

}
