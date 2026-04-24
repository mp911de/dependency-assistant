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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import biz.paluch.dap.gradle.KtVersion.Constraint;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;
import org.springframework.util.Assert;

/**
 * Internal Kotlin DSL PSI helpers used by parsers, lookup-site locators, and
 * update routines.
 *
 * <p>This class centralizes Kotlin build-script traversal rules shared across
 * parser infrastructure. It is not intended as a general-purpose Kotlin PSI
 * abstraction.
 *
 * @author Mark Paluch
 */
class KotlinDslUtils {

	private KotlinDslUtils() {
	}

	// -------------------------------------------------------------------------
	// Version element location
	// -------------------------------------------------------------------------

	static void doWithStrings(KtStringTemplateExpression element, Consumer<String> text,
			Consumer<KtExpression> expressionConsumer) {

		for (PsiElement child : element.getChildren()) {
			if (child instanceof KtStringTemplateExpression kse) {
				doWithStrings(kse, text, expressionConsumer);
			} else if (child instanceof KtBlockStringTemplateEntry block) {
				for (KtExpression expression : block.getExpressions()) {
					expressionConsumer.accept(expression);
				}
			} else if (child instanceof KtSimpleNameStringTemplateEntry simple) {
				KtExpression expr = simple.getExpression();
				if (expr == null) {
					expr = PsiTreeUtil.getChildOfType(simple, KtNameReferenceExpression.class);
				}
				if (expr != null) {
					expressionConsumer.accept(expr);
				} else {
					text.accept(child.getText());
				}
			} else {
				text.accept(child.getText());
			}
		}
	}

	static void doWithStrings(KtElement element, Consumer<String> text, Consumer<KtExpression> expressionConsumer) {

		for (PsiElement child : element.getChildren()) {
			if (child instanceof KtStringTemplateExpression kse) {
				doWithStrings(kse, text, expressionConsumer);
			} else if (child instanceof KtLiteralStringTemplateEntry kse) {
				text.accept(kse.getText());
			} else if (child instanceof KtElement kte) {
				doWithStrings(kte, text, expressionConsumer);
			}
		}
	}

	/**
	 * Resolve a Kotlin string-template expression against script properties.
	 * <p>Used when parsing interpolated plugin ids and version literals.
	 */
	static @Nullable String resolveKotlinExpression(KtExpression expr, PropertyResolver properties) {

		if (expr instanceof KtNameReferenceExpression ref) {
			String name = ref.getReferencedName();
			String value = properties.getProperty(name);
			if (value == null) {
				return null;
			}
			return properties.resolvePlaceholders(value);
		}

		if (expr instanceof KtCallExpression call && "property".equals(getKotlinCallName(call))) {
			StringBuilder key = new StringBuilder();
			doWithStrings(call, key::append, e -> {
			});

			String k = key.toString();
			String value = properties.getProperty(k);
			if (value == null) {
				return null;
			}
			return properties.resolvePlaceholders(value);
		}

		if (expr instanceof KtArrayAccessExpression arrayAccess) {
			KtExpression receiver = arrayAccess.getArrayExpression();
			List<KtExpression> indices = arrayAccess.getIndexExpressions();
			if (receiver != null && "extra".equals(receiver.getText()) && indices.size() == 1) {
				StringBuilder key = new StringBuilder();
				doWithStrings(indices.get(0), key::append, e -> {
				});
				String k = key.toString();
				String value = properties.getProperty(k);
				if (value == null) {
					return null;
				}
				return properties.resolvePlaceholders(value);
			}
		}

		if (expr instanceof KtDotQualifiedExpression dotQualified) {
			KtExpression receiver = dotQualified.getReceiverExpression();
			KtExpression selector = dotQualified.getSelectorExpression();
			if ("project".equals(receiver.getText()) && selector instanceof KtCallExpression selectorCall
					&& "property".equals(getKotlinCallName(selectorCall))) {
				StringBuilder key = new StringBuilder();
				doWithStrings(selectorCall, key::append, e -> {
				});
				String k = key.toString();
				String value = properties.getProperty(k);
				if (value == null) {
					return null;
				}
				return properties.resolvePlaceholders(value);
			}
		}

		return null;
	}

	/**
	 * Extract a property name from a supported Kotlin property expression.
	 * <p>Supports {@code property("...")}, {@code project.property("...")},
	 * {@code extra["..."]}, and direct name references.
	 */
	public static @Nullable String getPropertyName(KtExpression ktExpression) {

		StringBuilder property = new StringBuilder();

		if (ktExpression instanceof KtCallExpression ktCall) {
			String name = getKotlinCallName(ktCall);
			if ("property".equals(name)) {
				doWithStrings(ktExpression, property::append, e -> {
				});
			}
		}

		if (StringUtils.isEmpty(property)) {
			if (ktExpression instanceof KtNameReferenceExpression nameRef) {
				String name = nameRef.getReferencedName();
				property.append(name);
			} else if (ktExpression instanceof KtArrayAccessExpression arrayAccess
					&& arrayAccess.getArrayExpression() != null
					&& "extra".equals(arrayAccess.getArrayExpression().getText())) {
				List<KtExpression> indices = arrayAccess.getIndexExpressions();
				if (!indices.isEmpty()) {
					StringBuilder propKey = new StringBuilder();
					doWithStrings(indices.get(0), propKey::append, e -> {
					});
					String key = propKey.toString();
					property.append(key);
				}
			} else if (ktExpression instanceof KtDotQualifiedExpression dotQualified
					&& dotQualified.getSelectorExpression() instanceof KtCallExpression selectorCall
					&& "property".equals(getKotlinCallName(selectorCall))) {
				StringBuilder propKey = new StringBuilder();
				doWithStrings(selectorCall, propKey::append, e -> {
				});
				String key = propKey.toString();
				property.append(key);
			}
		}

		return StringUtils.hasText(property) ? property.toString() : null;
	}

	/**
	 * Create a {@link DependencySite} for a Kotlin plugin declaration.
	 * <p>Used for plugin ids declared in {@code plugins { }} blocks and infix
	 * {@code version} declarations.
	 */
	public static @Nullable DependencySite findPluginSite(KtCallElement call,
			@Nullable KtBinaryExpression be, PropertyResolver scriptProperties) {

		StringBuilder pluginId = new StringBuilder();
		boolean[] failed = new boolean[1];

		doWithStrings(call, pluginId::append, expr -> {
			String resolved = resolveKotlinExpression(expr, scriptProperties);
			if (resolved == null) {
				failed[0] = true;
			} else {
				pluginId.append(resolved);
			}
		});

		if (failed[0]) {
			return null;
		}

		String id = scriptProperties.resolvePlaceholders(pluginId.toString());
		if (!GradlePlugin.isValidPluginId(id)) {
			return null;
		}

		String versionText = null;
		String versionPropertyKey = null;
		PsiElement versionElement = null;
		if (be != null) {
			PsiElement[] children = be.getChildren();
			for (int i = 0; i < children.length; i++) {
				PsiElement child = children[i];
				if (child instanceof KtOperationReferenceExpression ops && "version".equals(ops.getReferencedName())
						&& children.length > i + 1
						&& children[i + 1] instanceof KtStringTemplateExpression versionExpr) {
					KtLiterals literals = KtLiterals.from(versionExpr);
					if (!literals.hasText()) {
						return null;
					}

					versionElement = versionExpr;
					versionText = literals.toString();
					if (literals.hasProperty() && literals.size() == 1) {
						versionPropertyKey = literals.getProperty();
						PsiElement resolvedElement = scriptProperties.getPropertyValue(versionPropertyKey) != null
								? scriptProperties.getPropertyValue(versionPropertyKey).element()
								: null;
						if (resolvedElement != null) {
							versionElement = resolvedElement;
						}
					}
					break;
				}
			}
		}

		if (!StringUtils.hasText(versionText)) {
			return null;
		}

		PropertyExpression versionExpression = StringUtils.hasText(versionPropertyKey)
				? PropertyExpression.property(versionPropertyKey)
				: PropertyExpression.from(versionText);

		return GradleDependency.of(GradlePlugin.of(id), versionExpression).toDependencySite(call,
				versionElement);
	}

	/**
	 * Create a {@link DependencySite} from parsed Kotlin DSL dependency data.
	 * <p>The supplied {@code declaration} and {@code version} elements are reused
	 * as the PSI anchors for the resulting site.
	 */
	public static @Nullable DependencySite getDependencySite(KtCallElement declaration,
			PsiElement version, String gav, @Nullable PropertyExpression versionExpression) {

		// Infix plugin form: id("plugin.id") version "x.y.z" — declaration may not be
		// `plugins` depending on PSI shape.
		if (version instanceof KtCallElement ktCall && GradleUtils.isPlugin(getKotlinCallName(ktCall))
				&& isInsidePluginsBlock(version) && !gav.contains(":")
				&& versionExpression != null) {
			GradleDependency dependency = GradleDependency.of(GradlePlugin.of(gav), versionExpression);
			return dependency.toDependencySite(declaration, version);
		}

		GradleDependency dependency = GradleDependency.parse(gav);
		if (dependency == null) {
			return null;
		}

		if (dependency.getVersionSource().isDefined()) {
			return dependency.toDependencySite(declaration, version);
		}

		if (versionExpression == null
				|| (!versionExpression.isProperty() && !StringUtils.hasText(versionExpression.toString()))) {
			return null;
		}

		return dependency.withVersion(versionExpression).toDependencySite(declaration, version);
	}

	/**
	 * Extract the property key from an {@code extra["key"] = ...} assignment.
	 */
	@Contract("null -> null")
	public static @Nullable String findProperty(@Nullable KtBinaryExpression element) {

		if (element == null) {
			return null;
		}

		for (PsiElement child : element.getChildren()) {

			if (child instanceof KtArrayAccessExpression array) {
				KtExpression arrayExpression = array.getArrayExpression();
				List<KtExpression> indexExpressions = array.getIndexExpressions();

				if (arrayExpression == null || !"extra".equals(arrayExpression.getText())
						|| indexExpressions.size() != 1) {
					return null;
				}

				StringBuilder builder = new StringBuilder();
				KtExpression indexExpression = indexExpressions.getFirst();
				doWithStrings(indexExpression, builder::append, e -> {
				});

				return builder.toString();
			}
		}

		return null;
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	public static boolean isDependencyCall(KtCallElement call) {

		String methodName = getKotlinCallName(call);
		if (StringUtils.isEmpty(methodName)) {
			return false;
		}

		if (GradleUtils.isDependencySection(methodName) || GradleUtils.isPlatformSection(methodName)) {
			return true;
		}
		return GradleUtils.isPlugin(methodName) && isInsidePluginsBlock(call);
	}

	/**
	 * Find the dependency call that owns the given PSI element.
	 * <p>Used by lookup-site resolution to map version literals, named arguments,
	 * and version-constraint entries back to their declaration call.
	 */
	public static @Nullable KtCallExpression findDependencyExpression(PsiElement element) {

		// A literal nested inside an array-access index (e.g. "junit" in
		// extra["junit"])
		// is never the element of a dependency declaration.
		if (PsiTreeUtil.getParentOfType(element, KtArrayAccessExpression.class) != null) {
			return null;
		}

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(element, KtBinaryExpression.class);
		KtCallExpression call = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);

		if (call != null && isDependencyCall(call)) {

			// GA string containing a versions block
			int lambdas = PsiTreeUtil.countChildrenOfType(call, KtLambdaExpression.class);
			if (lambdas > 0) {
				return null;
			}

			lambdas = PsiTreeUtil.countChildrenOfType(call, KtLambdaArgument.class);
			if (lambdas > 0) {
				return null;
			}
		}

		if (binary == null && call != null && isDependencyCall(call)) {

			if (element.getNextSibling() instanceof KtBlockStringTemplateEntry entry) {
				return null;
			}

			if (call.getValueArguments().size() == 1) {
				return call;
			}

			if (element.getParent().getParent() instanceof KtValueArgument valueArgument
					&& valueArgument.getArgumentName() instanceof KtValueArgumentName argumentName) {

				String name = argumentName.getAsName().asString();
				if ("version".equals(name)) {
					return call;
				}

				return null;
			}

			return call;
		}

		if (binary != null && call != null && isDependencyCall(call)) {
			return null;
		}

		if (binary != null && binary != element) {
			PsiElement previous = element.getPrevSibling();
			while (previous != null && !(previous instanceof PsiFile)) {

				if (previous instanceof KtOperationReferenceExpression ops) {
					if ("version".equals(ops.getReferencedName())) {
						for (PsiElement child : binary.getChildren()) {
							if (child instanceof KtCallExpression nested && isDependencyCall(nested)) {
								return nested;
							}
						}
					}
				}

				if (previous.getPrevSibling() != null) {
					previous = previous.getPrevSibling();
				} else {
					previous = previous.getParent();
				}
			}
		}

		// Handle: literal inside prefer("v") or strictly("v") inside element { } block
		KtCallExpression enclosingCall = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
		KtCallExpression versionCall = null;
		if (enclosingCall != null) {
			if (GradleVersionConstraint.PREFER.equals(getKotlinCallName(enclosingCall))
					|| GradleVersionConstraint.STRICTLY.equals(getKotlinCallName(enclosingCall))) {
				Constraint constraint = Constraint.of(enclosingCall);
				if (constraint.hasProperty() || constraint.hasText() && !constraint.isRange()) {

					versionCall = (KtCallExpression) PsiTreeUtil.findFirstParent(element, it -> {
						return it instanceof KtCallExpression candidate
								&& "version".equals(getKotlinCallName(candidate));
					});
				}
			} else {
				versionCall = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
			}
			if (versionCall != null && "version".equals(getKotlinCallName(versionCall))) {

				KtCallExpression depCall = PsiTreeUtil.getParentOfType(versionCall,
						KtCallExpression.class);
				if (depCall != null && isDependencyCall(depCall)) {
					return depCall;
				}
			}


			if (element instanceof KtBlockStringTemplateEntry block) {
				KtLiterals literals = KtLiterals.from(block);
				if (literals.hasProperty() && GradleUtils.isDependencySection(getKotlinCallName(call))) {
					return call;
				}
			}
		}


		return null;
	}

	/**
	 * Find the Kotlin property declaration that owns the given PSI element.
	 * <p>Used for literal entries nested within property initializers.
	 */
	public static @Nullable KtProperty findProperty(KtElement element) {

		if (element instanceof KtLiteralStringTemplateEntry entry) {
			return PsiTreeUtil.getParentOfType(element, KtProperty.class);
		}

		return null;
	}

	/**
	 * Find the {@code extra["key"] = ...} assignment that owns the given value PSI.
	 * <p>Also supports the {@code "value".also { extra["key"] = it }} form.
	 */
	public static @Nullable KtBinaryExpression findPropertyExpression(KtElement element) {

		// don't allow lookup from index side.
		if (element.getParent() instanceof KtContainerNode node) {
			return null;
		}

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(element, KtBinaryExpression.class);

		if (binary != null) {

			if (binary.getLeft() instanceof KtArrayAccessExpression arrayAccess) {
				KtExpression arrayExpression = arrayAccess.getArrayExpression();
				if ("extra".equals(getText(arrayExpression))) {
					return binary;
				}
			}
		}

		// "v".also { extra["k"] = it } — element PSI lives in the receiver, not under
		// the assignment RHS.
		return findExtraItAssignmentFromAlsoReceiver(element);
	}

	private static @Nullable KtBinaryExpression findExtraItAssignmentFromAlsoReceiver(PsiElement version) {

		KtQualifiedExpression qual = PsiTreeUtil.getParentOfType(version, KtQualifiedExpression.class);
		while (qual != null) {
			KtExpression recv = qual.getReceiverExpression();
			if (recv != null && PsiTreeUtil.isAncestor(recv, version, false)) {
				KtExpression selector = qual.getSelectorExpression();
				if (selector instanceof KtCallExpression alsoCall && "also".equals(getKotlinCallName(alsoCall))) {
					KtLambdaExpression lambda = firstLambdaArgument(alsoCall);
					if (lambda != null) {
						KtExpression body = lambda.getBodyExpression();
						if (body != null) {
							for (KtBinaryExpression assign : PsiTreeUtil.collectElementsOfType(body,
									KtBinaryExpression.class)) {
								if (!"=".equals(assign.getOperationReference().getText())) {
									continue;
								}
								if (!(assign.getLeft() instanceof KtArrayAccessExpression arrayAccess)) {
									continue;
								}
								KtExpression arrayExpr = arrayAccess.getArrayExpression();
								if (!(arrayExpr instanceof KtNameReferenceExpression nr)
										|| !"extra".equals(nr.getReferencedName())) {
									continue;
								}
								if (assign.getRight() instanceof KtNameReferenceExpression ref
										&& "it".equals(ref.getReferencedName())) {
									return assign;
								}
							}
						}
					}
				}
				return null;
			}
			qual = PsiTreeUtil.getParentOfType(qual, KtQualifiedExpression.class);
		}
		return null;
	}

	private static @Nullable KtLambdaExpression firstLambdaArgument(KtCallExpression call) {

		for (ValueArgument va : call.getValueArguments()) {
			KtExpression expr = va.getArgumentExpression();
			if (expr instanceof KtLambdaExpression lam) {
				return lam;
			}
		}
		return null;
	}

	static @Nullable String getKotlinCallName(KtCallElement call) {
		PsiElement callee = call.getCalleeExpression();
		if (callee instanceof KtNameReferenceExpression ref) {
			return ref.getReferencedName();
		}
		return callee != null ? callee.getText() : null;
	}

	static boolean isInsideBlock(PsiElement element, Predicate<String> predicate) {
		PsiElement parent = element.getParent();
		while (parent != null && !(parent instanceof PsiFile)) {
			if (parent instanceof KtCallExpression parentCall) {
				String name = getKotlinCallName(parentCall);
				if (name != null && predicate.test(name)) {
					return true;
				}
			}
			parent = parent.getParent();
		}
		return false;
	}

	static boolean isInsidePluginsBlock(PsiElement element) {
		return isInsideBlock(element, GradleUtils::isPluginSection);
	}

	/**
	 * Return text for a supported Kotlin DSL expression.
	 * @throws IllegalArgumentException if the expression cannot be converted to
	 * text by {@link #getText(KtElement)}
	 */
	static String getRequiredText(KtExpression expression) {

		Assert.notNull(expression, "Expression must not be null");
		String text = getText(expression);
		if (text == null) {
			throw new IllegalArgumentException(
					"No text available: %s (%s)".formatted(expression, expression.getClass().getName()));
		}
		return text;
	}

	/**
	 * Extract text from supported Kotlin DSL literal forms.
	 * <p>Used for property keys, dependency coordinates, and simple synthesized
	 * string values.
	 * @throws IllegalArgumentException if the element type is not supported
	 */
	@Contract("null -> null")
	static @Nullable String getText(@Nullable KtElement element) {

		switch (element) {
		case null -> {
			return null;
		}
		case KtLiteralStringTemplateEntry literal -> {
			return literal.getText();
		}
		case KtStringTemplateExpression st -> {
			StringBuilder builder = new StringBuilder();
			doWithStrings(st, builder::append, builder::append);
			return builder.toString();
		}
		case KtNameReferenceExpression ref -> {

			if ("it".equals(ref.getReferencedName())) {
				return extractAlsoReceiverStringLiteral(ref);
			}

			return ref.getReferencedName();
		}
		case KtCallExpression call -> {
			KtStringTemplateExpression literal = findBuildStringAppendLiteral(call);
			if (literal != null) {
				return getText(literal);
			}
		}
		default -> {
		}
		}

		throw new IllegalArgumentException(
				"Unexpected expression: %s (%s)".formatted(element, element.getClass().getName()));
	}

	private static @Nullable String extractAlsoReceiverStringLiteral(KtNameReferenceExpression itRef) {

		KtStringTemplateExpression receiver = findAlsoReceiverStringTemplate(itRef);
		return receiver != null ? getText(receiver) : null;
	}

	/**
	 * Return the receiver string template for an {@code also { ... = it }}
	 * assignment.
	 */
	public static @Nullable KtStringTemplateExpression findAlsoReceiverStringTemplate(KtNameReferenceExpression itRef) {

		KtLambdaExpression lambda = PsiTreeUtil.getParentOfType(itRef, KtLambdaExpression.class);
		if (lambda == null) {
			return null;
		}
		KtCallExpression alsoCall = PsiTreeUtil.getParentOfType(lambda, KtCallExpression.class);
		if (alsoCall == null || !"also".equals(KotlinDslUtils.getKotlinCallName(alsoCall))) {
			return null;
		}
		PsiElement parent = alsoCall.getParent();
		if (parent instanceof KtQualifiedExpression dot) {
			KtExpression recv = dot.getReceiverExpression();
			if (recv instanceof KtStringTemplateExpression st) {
				return st;
			}
		}
		return null;
	}

	/**
	 * Return the first string template appended within a {@code buildString { }}
	 * call.
	 */
	public static @Nullable KtStringTemplateExpression findBuildStringAppendLiteral(KtCallExpression buildStringCall) {

		if (!"buildString".equals(KotlinDslUtils.getKotlinCallName(buildStringCall))) {
			return null;
		}

		KtLambdaExpression lambda = findTrailingLambda(buildStringCall);
		if (lambda == null) {
			return null;
		}
		KtExpression body = lambda.getBodyExpression();
		if (body == null) {
			return null;
		}
		for (KtCallExpression inner : PsiTreeUtil.collectElementsOfType(body, KtCallExpression.class)) {
			if (!"append".equals(KotlinDslUtils.getKotlinCallName(inner))) {
				continue;
			}
			for (ValueArgument va : inner.getValueArguments()) {
				KtExpression argExpr = va.getArgumentExpression();
				if (argExpr instanceof KtStringTemplateExpression st) {
					return st;
				}
			}
		}
		return null;
	}

	private static @Nullable KtLambdaExpression findTrailingLambda(KtCallExpression call) {

		for (ValueArgument va : call.getValueArguments()) {
			KtExpression expr = va.getArgumentExpression();
			if (expr instanceof KtLambdaExpression lambda) {
				return lambda;
			}
		}
		return null;
	}

	// -------------------------------------------------------------------------
	// Version catalog (Kotlin {@code libs.…})
	// -------------------------------------------------------------------------

	/**
	 * Unwrap nested {@link KtParenthesizedExpression} nodes.
	 */
	static KtExpression unwrapParenthesizedExpression(KtExpression expression) {

		KtExpression e = expression;
		while (e instanceof KtParenthesizedExpression paren) {
			KtExpression inner = paren.getExpression();
			if (inner == null) {
				break;
			}
			e = inner;
		}
		return e;
	}

	static @Nullable KtExpression getFirstValueArgument(KtCallElement call) {

		for (ValueArgument va : call.getValueArguments()) {
			return va.getArgumentExpression();
		}
		return null;
	}

	static List<String> collectKotlinCatalogDotSegments(KtExpression expr) {

		List<String> reversed = new ArrayList<>();
		KtExpression cur = expr;
		while (cur instanceof KtDotQualifiedExpression dq) {
			String seg = kotlinSelectorToSegment(dq.getSelectorExpression());
			if (seg == null) {
				return List.of();
			}
			reversed.add(seg);
			cur = dq.getReceiverExpression();
		}
		if (cur instanceof KtNameReferenceExpression ref) {
			reversed.add(ref.getReferencedName());
		} else {
			return reversed;
		}
		Collections.reverse(reversed);
		return reversed;
	}

	private static @Nullable String kotlinSelectorToSegment(KtExpression selector) {

		if (selector instanceof KtNameReferenceExpression ref) {
			return ref.getReferencedName();
		}
		if (selector instanceof KtCallExpression call && call.getValueArguments().isEmpty()) {
			return getKotlinCallName(call);
		}
		return null;
	}

}
