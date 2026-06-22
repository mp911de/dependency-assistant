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

import biz.paluch.dap.gradle.KotlinDslParser.KotlinDeclarationCall;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.CachedValuesManager;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

/**
 * Kotlin DSL declaration-style detection.
 *
 * @author Mark Paluch
 * @see DeclarationStyleDetector
 */
class KotlinDeclarationStyleDetector implements DeclarationStyleDetector {

	private static final KotlinDeclarationStyleDetector INSTANCE = new KotlinDeclarationStyleDetector();

	public static KotlinDeclarationStyleDetector getInstance() {
		return INSTANCE;
	}

	@Override
	public DeclarationStyle detect(PsiElement element) {

		KtStringTemplateExpression template = element instanceof KtStringTemplateExpression t ? t
				: PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression.class, false);
		if (template != null) {

			DeclarationStyle position = classifyTemplate(template);
			if (position != null) {
				return position;
			}
		}

		KtNameReferenceExpression reference = element instanceof KtNameReferenceExpression r ? r
				: PsiTreeUtil.getParentOfType(element, KtNameReferenceExpression.class, false);
		if (reference != null) {

			DeclarationStyle position = classifyReference(reference);
			if (position != null) {
				return position;
			}
		}

		return DeclarationStyle.absent();
	}

	private @Nullable DeclarationStyle classifyTemplate(KtStringTemplateExpression template) {

		if (isDirectDependencyNotationLiteral(template)) {
			return DeclarationStyle.inline(template, enclosingDeclarationCall(template));
		}

		if (isVersionNamedArgumentLiteral(template)) {
			return DeclarationStyle.mapNotation(template, enclosingDeclarationCall(template));
		}

		if (isVersionBlockLiteral(template, GradleVersionConstraint.PREFER)) {
			return DeclarationStyle.versionBlock(DeclarationStyle.Kind.VERSION_BLOCK_PREFER, template,
					versionBlockCall(template));
		}

		if (isVersionBlockLiteral(template, GradleVersionConstraint.STRICTLY)) {
			return DeclarationStyle.versionBlock(DeclarationStyle.Kind.VERSION_BLOCK_STRICTLY, template,
					versionBlockCall(template));
		}

		if (isPluginVersionLiteral(template)) {
			return DeclarationStyle.pluginVersion(template, pluginIdCall(template));
		}

		if (isBackingVersionPropertyLiteral(template)) {
			return DeclarationStyle.backingProperty(template);
		}

		return null;
	}

	private @Nullable DeclarationStyle classifyReference(KtNameReferenceExpression reference) {

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

	private @Nullable KtCallExpression enclosingDeclarationCall(PsiElement versionElement) {

		KtCallExpression call = PsiTreeUtil.getParentOfType(versionElement, KtCallExpression.class);
		return call != null && KotlinDslUtils.isDependencyCall(call) ? call : null;
	}

	private @Nullable KtCallExpression versionBlockCall(PsiElement versionElement) {

		KtCallExpression constraintCall = PsiTreeUtil.getParentOfType(versionElement, KtCallExpression.class);
		return constraintCall != null ? findVersionBlockDependencyCall(constraintCall) : null;
	}

	private @Nullable PsiElement pluginIdCall(PsiElement versionElement) {

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(versionElement, KtBinaryExpression.class);
		return binary != null ? findPluginIdCallForVersionBinary(binary) : null;
	}

	/**
	 * Return whether the literal is the coordinate argument in a direct Kotlin DSL
	 * dependency or platform notation call.
	 */
	private boolean isDirectDependencyNotationLiteral(KtStringTemplateExpression literal) {

		KtValueArgument valueArgument = PsiTreeUtil.getParentOfType(literal, KtValueArgument.class);
		if (valueArgument != null && valueArgument.getArgumentName() != null) {
			return false;
		}

		KtCallExpression call = PsiTreeUtil.getParentOfType(literal, KtCallExpression.class);
		KotlinDeclarationCall context = KotlinDeclarationCall.from(call);
		return context != null && context.isDependency() && isArgumentOfCall(literal, call)
				&& call.getLambdaArguments().isEmpty();
	}

	/**
	 * Return whether the literal is a {@code version = "..."} value in map-style
	 * dependency notation.
	 */
	private boolean isVersionNamedArgumentLiteral(KtStringTemplateExpression literal) {

		KtValueArgument namedArgument = PsiTreeUtil.getParentOfType(literal, KtValueArgument.class);
		return namedArgument != null && isVersionNamedArgument(namedArgument)
				&& isExpressionOfValueArgument(literal, namedArgument)
				&& isNamedArgumentOfDependencyCall(namedArgument);
	}

	/**
	 * Return whether the reference is a {@code version = property} value in
	 * map-style dependency notation.
	 */
	private boolean isVersionNamedArgumentReference(KtNameReferenceExpression reference) {

		KtValueArgument namedArgument = PsiTreeUtil.getParentOfType(reference, KtValueArgument.class);
		return namedArgument != null && isVersionNamedArgument(namedArgument)
				&& isExpressionOfValueArgument(reference, namedArgument)
				&& isNamedArgumentOfDependencyCall(namedArgument);
	}

	/**
	 * Return whether the literal is an argument to a Kotlin version-block
	 * constraint call.
	 */
	private boolean isVersionBlockLiteral(KtStringTemplateExpression literal, String constraintName) {

		KtCallExpression constraintCall = PsiTreeUtil.getParentOfType(literal, KtCallExpression.class);
		return constraintCall != null && constraintName.equals(KotlinDslUtils.getKotlinCallName(constraintCall))
				&& isArgumentOfCall(literal, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the reference is an argument to a Kotlin version-block
	 * constraint call.
	 */
	private boolean isVersionBlockReference(KtNameReferenceExpression reference, String constraintName) {

		KtCallExpression constraintCall = PsiTreeUtil.getParentOfType(reference, KtCallExpression.class);
		return constraintCall != null && constraintName.equals(KotlinDslUtils.getKotlinCallName(constraintCall))
				&& isArgumentOfCall(reference, constraintCall)
				&& findVersionBlockDependencyCall(constraintCall) != null;
	}

	/**
	 * Return whether the literal is the version operand in a Kotlin plugin
	 * declaration.
	 */
	private boolean isPluginVersionLiteral(KtStringTemplateExpression literal) {

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(literal, KtBinaryExpression.class);
		if (binary == null || !isRightSideOfBinary(literal, binary)) {
			return false;
		}

		return findPluginIdCallForVersionBinary(binary) != null;
	}

	private @Nullable KtCallElement findPluginIdCallForVersionBinary(KtBinaryExpression binary) {

		if (!GradleUtils.VERSION.equals(binary.getOperationReference().getReferencedName())) {
			return null;
		}

		KtExpression left = binary.getLeft();
		if (left instanceof KtCallElement call && KotlinDslUtils.isInsidePluginsBlock(call)) {
			return call;
		}

		if (left == null) {
			return null;
		}

		KtCallElement call = PsiTreeUtil.findChildOfType(left, KtCallElement.class);
		if (call != null && KotlinDslUtils.isInsidePluginsBlock(call)) {
			return call;
		}

		return null;
	}

	/**
	 * Return whether the literal declares a Kotlin property or extra property that
	 * backs a supported dependency version reference.
	 */
	private boolean isBackingVersionPropertyLiteral(KtStringTemplateExpression literal) {

		String propertyName = findBackingVersionPropertyName(literal);
		if (!StringUtils.hasText(propertyName)) {
			return false;
		}

		PsiFile file = literal.getContainingFile();
		if (file == null) {
			return false;
		}

		return isReferencedBySupportedVersionSite(file, propertyName);
	}

	/**
	 * Walk up from a {@code prefer(...)} or {@code strictly(...)} call to the
	 * enclosing dependency call.
	 */
	private @Nullable KtCallExpression findVersionBlockDependencyCall(KtCallExpression preferOrStrictlyCall) {

		KtLambdaExpression versionLambda = PsiTreeUtil.getParentOfType(preferOrStrictlyCall,
				KtLambdaExpression.class);
		if (versionLambda == null) {
			return null;
		}

		KtCallExpression versionCall = PsiTreeUtil.getParentOfType(versionLambda, KtCallExpression.class);
		if (versionCall == null || !GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(versionCall))) {
			return null;
		}

		KtLambdaExpression dependencyLambda = PsiTreeUtil.getParentOfType(versionCall, KtLambdaExpression.class);
		if (dependencyLambda == null) {
			return null;
		}

		KtCallExpression dependencyCall = PsiTreeUtil.getParentOfType(dependencyLambda, KtCallExpression.class);
		KotlinDeclarationCall context = KotlinDeclarationCall.from(dependencyCall);
		if (context != null && context.isDependency()) {
			return dependencyCall;
		}

		return null;
	}

	private @Nullable String findBackingVersionPropertyName(KtStringTemplateExpression literal) {

		KtProperty property = PsiTreeUtil.getParentOfType(literal, KtProperty.class);
		if (property != null) {
			KtExpression initializer = property.getInitializer();
			if (initializer != null
					&& (initializer == literal || PsiTreeUtil.isAncestor(initializer, literal, false))) {
				return property.getName();
			}

			if (property.hasDelegateExpression()
					&& property.getDelegateExpression() instanceof KtCallExpression delegateCall
					&& "extra".equals(KotlinDslUtils.getKotlinCallName(delegateCall))
					&& isArgumentOfCall(literal, delegateCall)) {
				return property.getName();
			}
		}

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(literal, KtBinaryExpression.class);
		KotlinExtraAssignment assignment = KotlinExtraAssignment.from(binary);
		if (assignment == null) {
			assignment = KotlinExtraAssignment.fromAlsoReceiver(literal);
		}

		if (assignment != null && assignment.getValueLiteral() == literal) {
			return assignment.getKey();
		}

		return null;
	}

	private boolean isReferencedBySupportedVersionSite(PsiFile file, String propertyName) {
		return referencedVersionPropertyNames(file).contains(propertyName);
	}

	/**
	 * Return the property names referenced by a supported declaration anywhere in
	 * the file. The result is cached and recomputed when the PSI changes, so the
	 * repeated completion-position checks do not each re-traverse the file.
	 */
	private Set<String> referencedVersionPropertyNames(PsiFile file) {
		return CachedValuesManager.getProjectPsiDependentCache(file,
				psiFile -> computeReferencedVersionPropertyNames(file));
	}

	private Set<String> computeReferencedVersionPropertyNames(PsiFile file) {

		Set<String> names = new HashSet<>();

		for (KtNameReferenceExpression reference : SyntaxTraverser.psiTraverser(file)
				.filter(KtNameReferenceExpression.class)) {
			if (isVersionPropertyReference(reference)) {
				String name = reference.getReferencedName();
				if (StringUtils.hasText(name)) {
					names.add(name);
				}
			}
		}

		for (KtArrayAccessExpression arrayAccess : SyntaxTraverser.psiTraverser(file)
				.filter(KtArrayAccessExpression.class)) {
			if (isReferenceInsideSupportedVersionLiteral(arrayAccess)) {
				String name = getExtraPropertyKey(arrayAccess);
				if (StringUtils.hasText(name)) {
					names.add(name);
				}
			}
		}

		for (KtCallExpression call : SyntaxTraverser.psiTraverser(file).filter(KtCallExpression.class)) {
			if ("property".equals(KotlinDslUtils.getKotlinCallName(call))
					&& isReferenceInsideSupportedVersionLiteral(call)) {
				KtExpression argument = KotlinDslUtils.getFirstValueArgument(call);
				String name = argument != null ? KtLiterals.getText(argument) : null;
				if (StringUtils.hasText(name)) {
					names.add(name);
				}
			}
		}

		return names;
	}

	private boolean isVersionPropertyReference(KtNameReferenceExpression reference) {
		return isVersionNamedArgumentReference(reference)
				|| isVersionBlockReference(reference, GradleVersionConstraint.PREFER)
				|| isVersionBlockReference(reference, GradleVersionConstraint.STRICTLY)
				|| isReferenceInsideSupportedVersionLiteral(reference);
	}

	private boolean isReferenceInsideSupportedVersionLiteral(PsiElement reference) {

		KtStringTemplateExpression template = PsiTreeUtil.getParentOfType(reference, KtStringTemplateExpression.class);
		return template != null && isSupportedVersionLiteral(template);
	}

	private boolean isSupportedVersionLiteral(KtStringTemplateExpression template) {
		return isDirectDependencyNotationLiteral(template)
				|| isVersionNamedArgumentLiteral(template)
				|| isVersionBlockLiteral(template, GradleVersionConstraint.PREFER)
				|| isVersionBlockLiteral(template, GradleVersionConstraint.STRICTLY)
				|| isPluginVersionLiteral(template);
	}

	private @Nullable String getExtraPropertyKey(KtArrayAccessExpression arrayAccess) {

		if (!(arrayAccess.getArrayExpression() instanceof KtNameReferenceExpression nameReference)
				|| !"extra".equals(nameReference.getReferencedName())) {
			return null;
		}

		for (KtExpression indexExpression : arrayAccess.getIndexExpressions()) {
			String key = KtLiterals.getText(indexExpression);
			if (StringUtils.hasText(key)) {
				return key;
			}
		}

		return null;
	}

	private boolean isVersionNamedArgument(ValueArgument namedArgument) {
		return namedArgument.getArgumentName() != null
				&& GradleUtils.VERSION.equals(namedArgument.getArgumentName().getAsName().asString());
	}

	private boolean isExpressionOfValueArgument(PsiElement element, ValueArgument valueArgument) {

		KtExpression expression = valueArgument.getArgumentExpression();
		return expression != null && (expression == element || PsiTreeUtil.isAncestor(expression, element, false));
	}

	private boolean isNamedArgumentOfDependencyCall(ValueArgument namedArgument) {

		if (!(namedArgument instanceof PsiElement element)) {
			return false;
		}

		KtCallExpression call = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
		KotlinDeclarationCall declarationCall = KotlinDeclarationCall.from(call);
		return declarationCall != null && declarationCall.isDependency();
	}

	private boolean isArgumentOfCall(PsiElement element, KtCallElement call) {

		for (ValueArgument argument : call.getValueArguments()) {
			KtExpression expression = argument.getArgumentExpression();
			if (expression != null && (expression == element || PsiTreeUtil.isAncestor(expression, element, false))) {
				return true;
			}
		}

		return false;
	}

	private boolean isRightSideOfBinary(PsiElement element, KtBinaryExpression binary) {

		KtExpression right = binary.getRight();
		return right != null && (right == element || PsiTreeUtil.isAncestor(right, element, false));
	}

}
