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

import biz.paluch.dap.gradle.KtVersion.Constraint;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

/**
 * Kotlin DSL PSI locator for semantic {@link LookupSite lookup sites} and
 * parser-backed dependency declarations.
 *
 * @author Mark Paluch
 */
class KotlinLookupSiteLocator implements LookupSiteLocator<KtElement> {

	private final PropertyResolver propertyResolver;

	private final @Nullable VersionCatalogRegistry registry;

	KotlinLookupSiteLocator(PropertyResolver propertyResolver, @Nullable VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
	}

	/**
	 * Return whether the given PSI element is a version element suitable for
	 * highlighting or annotation.
	 */
	public static boolean isVersionElement(PsiElement element) {
		if (element instanceof KtStringTemplateEntry versionCandidate) {
			if (versionCandidate.getParent() instanceof KtStringTemplateExpression expression) {
				PsiElement[] children = expression.getChildren();
				if (children.length > 1 && children[0] == element) {
					return false;
				}
			}
		}

		return true;
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

		if (call != null && KotlinDslUtils.isDependencyCall(call)) {

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

		if (binary == null && call != null && KotlinDslUtils.isDependencyCall(call)) {

			if (element.getNextSibling() instanceof KtBlockStringTemplateEntry entry) {
				return null;
			}

			if (call.getValueArguments().size() == 1) {
				return call;
			}

			if (element.getParent().getParent() instanceof KtValueArgument valueArgument
					&& valueArgument.getArgumentName() instanceof KtValueArgumentName argumentName) {

				String name = argumentName.getAsName().asString();
				if (GradleUtils.VERSION.equals(name)) {
					return call;
				}

				return null;
			}

			return call;
		}

		if (binary != null && call != null && KotlinDslUtils.isDependencyCall(call)) {
			return null;
		}

		if (binary != null && binary != element) {
			PsiElement previous = element.getPrevSibling();
			while (previous != null && !(previous instanceof PsiFile)) {

				if (previous instanceof KtOperationReferenceExpression ops) {
					if (GradleUtils.VERSION.equals(ops.getReferencedName())) {
						for (PsiElement child : binary.getChildren()) {
							if (child instanceof KtCallExpression nested && KotlinDslUtils.isDependencyCall(nested)) {
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
			if (GradleVersionConstraint.PREFER.equals(KotlinDslUtils.getKotlinCallName(enclosingCall))
					|| GradleVersionConstraint.STRICTLY.equals(KotlinDslUtils.getKotlinCallName(enclosingCall))) {
				Constraint constraint = Constraint.of(enclosingCall);
				if (constraint.hasProperty() || constraint.hasText() && !constraint.isRange()) {

					versionCall = (KtCallExpression) PsiTreeUtil.findFirstParent(element, it -> {
						return it instanceof KtCallExpression candidate
								&& GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(candidate));
					});
				}
			} else {
				versionCall = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
			}

			if (versionCall != null && GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(versionCall))) {

				KtCallExpression depCall = PsiTreeUtil.getParentOfType(versionCall,
						KtCallExpression.class);
				if (depCall != null && KotlinDslUtils.isDependencyCall(depCall)) {
					return depCall;
				}
			}

			if (element instanceof KtBlockStringTemplateEntry block) {
				KtLiterals literals = KtLiterals.from(block);
				if (literals.hasProperty() && GradleUtils.isDependencySection(KotlinDslUtils.getKotlinCallName(call))) {
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
		return element instanceof KtLiteralStringTemplateEntry entry
				? PsiTreeUtil.getParentOfType(element, KtProperty.class)
				: null;
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

		KtBinaryExpression binaryExpression = PsiTreeUtil.getParentOfType(element, KtBinaryExpression.class);
		KotlinExtraAssignment extra = null;
		if (binaryExpression != null) {
			extra = KotlinExtraAssignment.from(binaryExpression);
		}

		if (extra == null) {
			extra = KotlinExtraAssignment.fromAlsoReceiver(element);
		}

		return extra != null ? extra.getDeclaration() : null;
	}

	/**
	 * Extract the property key from an {@code extra["key"] = ...} assignment.
	 */
	@Contract("null -> null")
	public static @Nullable String findProperty(@Nullable KtBinaryExpression element) {

		KotlinExtraAssignment assignment = KotlinExtraAssignment.from(element);
		return assignment != null ? assignment.getKey() : null;
	}

	/**
	 * Locate the semantic {@link LookupSite} owning the given Kotlin PSI element.
	 * <p>Supports direct dependency literals, property-backed declarations,
	 * {@code extra} assignments, and version catalog references such as:
	 * <pre class="code">
	 * implementation("org.springframework:spring-core:6.2.0")
	 * implementation("org.springframework:spring-core:$springVersion")
	 * extra["springVersion"] = "6.2.0"
	 * implementation(libs.spring.core)
	 * </pre>
	 *
	 * @param element the PSI element to inspect.
	 * @return the resolved lookup site, or {@link LookupSite#absent()} if no
	 * supported declaration can be derived.
	 */
	@Override
	public LookupSite locate(KtElement element) {

		LookupSite catalogReference = locateCatalogReference(element);
		if (catalogReference.isPresent()) {
			return catalogReference;
		}

		if (element instanceof KtStringTemplateExpression propertyCandidate) {
			KtBinaryExpression propertyExpression = findPropertyExpression(propertyCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return locateExtraProperty(propertyExpression, propertyCandidate);
			}
		}

		if (element instanceof KtBlockStringTemplateEntry propertyCandidate) {
			KtCallExpression dependencyExpression = findDependencyExpression(propertyCandidate);
			KtLiterals literals = KtLiterals.from(propertyCandidate);
			if (dependencyExpression != null && literals.hasProperty()) {
				return LookupSite
						.from(locatePropertyUsage(literals.getProperty(), dependencyExpression, propertyCandidate));
			}

			if (dependencyExpression != null) {
				return LookupSite.from(KotlinDslParser.parseDependencySite(dependencyExpression,
						propertyResolver));
			}
		}

		if (element instanceof KtStringTemplateEntry versionCandidate) {

			KtProperty property = findProperty(versionCandidate);
			if (property != null && StringUtils.hasText(property.getName())) {
				return locatePropertyDeclaration(property.getName(), property);
			}

			if (versionCandidate.getParent() instanceof KtStringTemplateExpression expression) {
				PsiElement[] children = expression.getChildren();
				if (children.length > 1 && children[0] == element) {

					KtCallExpression declaration = findDependencyExpression(expression);
					if (declaration != null) {
						return LookupSite.from(KotlinDslParser.parseDependencySite(declaration, propertyResolver));
					}

					return LookupSite.absent();
				}
			}

			KtBinaryExpression propertyExpression = findPropertyExpression(versionCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return locateExtraProperty(propertyExpression, versionCandidate);
			}

			KtCallExpression declaration = findDependencyExpression(versionCandidate);
			if (declaration != null) {
				return LookupSite.from(KotlinDslParser.parseDependencySite(declaration, propertyResolver));
			}
		}

		if (element instanceof KtNameReferenceExpression propertyCandidate
				&& element.getParent() instanceof ValueArgument) {
			if (GradleUtils.isDependencySection(propertyCandidate.getReferencedName())) {
				return LookupSite.absent();
			}

			KtCallExpression declaration = findDependencyExpression(propertyCandidate);
			if (declaration != null) {
				return LookupSite.from(
						locatePropertyUsage(propertyCandidate.getReferencedName(), declaration, propertyCandidate));
			}
		}

		return LookupSite.absent();
	}


	private LookupSite locateExtraProperty(@Nullable KtBinaryExpression propertyExpression,
			KtElement versionEntry) {

		if (propertyExpression == null) {
			return LookupSite.absent();
		}

		String propertyName = findProperty(propertyExpression);
		if (!StringUtils.hasText(propertyName)) {
			return LookupSite.absent();
		}

		return LookupSite.ofProperty(propertyName, versionEntry.getText(), propertyExpression, versionEntry);
	}

	private LookupSite locatePropertyDeclaration(String propertyName, KtElement declaration) {

		PropertyValue propertyValue = propertyResolver.getPropertyValue(propertyName);
		if (propertyValue == null) {
			return LookupSite.absent();
		}

		return LookupSite.ofProperty(propertyValue, declaration);
	}

	private @Nullable DependencySite locatePropertyUsage(String propertyName, KtCallExpression declaration,
			PsiElement declarationElement) {

		if (StringUtils.isEmpty(propertyName) || !propertyResolver.containsProperty(propertyName)) {
			return null;
		}

		return KotlinDslParser.parseDependencySite(declaration, propertyResolver);
	}

	private LookupSite locateCatalogReference(KtElement element) {

		if (registry == null) {
			return LookupSite.absent();
		}

		if (!(element instanceof KtDotQualifiedExpression dots)
				|| !(element.getParent() instanceof KtValueArgument arg)) {
			return LookupSite.absent();
		}

		KtCallExpression catalogCall = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
		if (!KotlinDslUtils.isCatalogConsumerCall(catalogCall)) {
			return LookupSite.absent();
		}

		TomlReference reference = TomlReference.from(KotlinDslParser.getSegments(dots),
				registry.catalogPaths().keySet());
		if (reference == null) {
			return LookupSite.absent();
		}

		return LookupSite.ofTomlReference(reference, catalogCall);
	}

}
