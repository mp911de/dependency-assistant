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

import biz.paluch.dap.gradle.GradleVersionSite.BackingProperty;
import biz.paluch.dap.gradle.GradleVersionSite.CoordinateSite;
import biz.paluch.dap.gradle.GradleVersionSite.TomlCatalogAlias;
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
 * Kotlin DSL PSI locator for {@link GradleVersionSite version sites} and
 * parser-backed dependency declarations.
 *
 * @author Mark Paluch
 */
class KotlinVersionSiteLocator implements VersionSiteLocator<KtElement> {

	private final PropertyResolver propertyResolver;

	private final @Nullable VersionCatalogRegistry registry;

	KotlinVersionSiteLocator(PropertyResolver propertyResolver, @Nullable VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
	}

	/**
	 * Return whether the given PSI element is a version element suitable for
	 * highlighting or annotation.
	 */
	public static boolean isVersionElement(PsiElement element) {
		return !isLeadingTemplateEntry(element);
	}

	/**
	 * Return whether the element is the leading entry of a multi-entry string
	 * template (e.g. the {@code "group:artifact:"} prefix before a {@code $version}
	 * interpolation). Such an entry is the coordinate, not the version.
	 */
	private static boolean isLeadingTemplateEntry(PsiElement element) {
		return element instanceof KtStringTemplateEntry
				&& element.getParent() instanceof KtStringTemplateExpression expression
				&& expression.getChildren().length > 1 && expression.getChildren()[0] == element;
	}

	/**
	 * Find the dependency call that owns the given PSI element.
	 * <p>Used by lookup-site resolution to map version literals, named arguments,
	 * and version-constraint entries back to their declaration call.
	 */
	public static @Nullable KtCallExpression findDependencyExpression(PsiElement element) {

		if (PsiTreeUtil.getParentOfType(element, KtArrayAccessExpression.class) != null) {
			return null;
		}

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(element, KtBinaryExpression.class);
		KtCallExpression call = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
		boolean dependencyCall = call != null && KotlinDslUtils.isDependencyCall(call);

		// A version-block declaration carries a trailing lambda; its literal is reached
		// through the
		// version-block branch below, not as a direct argument of the dependency call.
		if (dependencyCall && (PsiTreeUtil.countChildrenOfType(call, KtLambdaExpression.class) > 0
				|| PsiTreeUtil.countChildrenOfType(call, KtLambdaArgument.class) > 0)) {
			return null;
		}

		// Direct argument of a dependency call: compact notation or a `version = ...`
		// named argument.
		if (binary == null && dependencyCall) {

			if (element.getNextSibling() instanceof KtBlockStringTemplateEntry) {
				return null;
			}

			if (call.getValueArguments().size() == 1) {
				return call;
			}

			if (element.getParent().getParent() instanceof KtValueArgument valueArgument
					&& valueArgument.getArgumentName() instanceof KtValueArgumentName argumentName) {
				return GradleUtils.VERSION.equals(argumentName.getAsName().asString()) ? call : null;
			}

			return call;
		}

		// `id("plugin") version "1.0"`: the literal lives in a binary that wraps the
		// dependency call.
		if (binary != null && dependencyCall) {
			return null;
		}

		if (binary != null && binary != element) {
			PsiElement previous = element.getPrevSibling();
			while (previous != null && !(previous instanceof PsiFile)) {

				if (previous instanceof KtOperationReferenceExpression ops
						&& GradleUtils.VERSION.equals(ops.getReferencedName())) {
					for (PsiElement child : binary.getChildren()) {
						if (child instanceof KtCallExpression nested && KotlinDslUtils.isDependencyCall(nested)) {
							return nested;
						}
					}
				}

				previous = previous.getPrevSibling() != null ? previous.getPrevSibling() : previous.getParent();
			}
		}

		if (call == null) {
			return null;
		}

		// version { prefer(...) / strictly(...) } block: resolve the version() call,
		// then its dependency call.
		String callName = KotlinDslUtils.getKotlinCallName(call);
		KtCallExpression versionCall;
		if (GradleVersionConstraint.PREFER.equals(callName) || GradleVersionConstraint.STRICTLY.equals(callName)) {
			Constraint constraint = Constraint.of(call);
			versionCall = constraint.hasProperty() || constraint.hasText() && !constraint.isRange()
					? (KtCallExpression) PsiTreeUtil.findFirstParent(element,
							it -> it instanceof KtCallExpression candidate
									&& GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(candidate)))
					: null;
		} else {
			versionCall = call;
		}

		if (versionCall != null && GradleUtils.VERSION.equals(KotlinDslUtils.getKotlinCallName(versionCall))) {
			KtCallExpression depCall = PsiTreeUtil.getParentOfType(versionCall, KtCallExpression.class);
			if (depCall != null && KotlinDslUtils.isDependencyCall(depCall)) {
				return depCall;
			}
		}

		if (element instanceof KtBlockStringTemplateEntry block) {
			KtLiterals literals = KtLiterals.from(block);
			if (literals.hasProperty() && GradleUtils.isDependencySection(callName)) {
				return call;
			}
		}

		return null;
	}

	/**
	 * Find the Kotlin property declaration that owns the given PSI element.
	 * <p>Used for literal entries nested within property initializers.
	 */
	public static @Nullable KtProperty findProperty(KtElement element) {
		return element instanceof KtLiteralStringTemplateEntry
				? PsiTreeUtil.getParentOfType(element, KtProperty.class)
				: null;
	}

	/**
	 * Find the {@code extra["key"] = ...} assignment that owns the given value PSI.
	 * <p>Also supports the {@code "value".also { extra["key"] = it }} form.
	 */
	public static @Nullable KtBinaryExpression findPropertyExpression(KtElement element) {

		if (element.getParent() instanceof KtContainerNode) {
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
	 * Locate the {@link GradleVersionSite} owning the given Kotlin PSI element.
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
	 * @return the resolved version site, or {@link GradleVersionSite#absent()} if
	 * no supported declaration can be derived.
	 */
	@Override
	public GradleVersionSite locate(KtElement element) {

		GradleVersionSite catalogReference = locateCatalogReference(element);
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
				DependencySite site = locatePropertyUsage(literals.getProperty(), dependencyExpression,
						propertyCandidate);
				return coordinateSite(site, propertyCandidate);
			}

			if (dependencyExpression != null) {
				DependencySite site = KotlinDslParser.parseDependencySite(dependencyExpression, propertyResolver);
				return coordinateSite(site, propertyCandidate);
			}
		}

		if (element instanceof KtStringTemplateEntry versionCandidate) {

			KtProperty property = findProperty(versionCandidate);
			if (property != null && StringUtils.hasText(property.getName())) {
				return locatePropertyDeclaration(property.getName(), property);
			}

			if (isLeadingTemplateEntry(versionCandidate)) {
				KtStringTemplateExpression expression = (KtStringTemplateExpression) versionCandidate.getParent();

				KtCallExpression declaration = findDependencyExpression(expression);
				if (declaration != null) {
					DependencySite site = KotlinDslParser.parseDependencySite(declaration, propertyResolver);
					return coordinateSite(site, expression);
				}

				return GradleVersionSite.absent();
			}

			KtBinaryExpression propertyExpression = findPropertyExpression(versionCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return locateExtraProperty(propertyExpression, versionCandidate);
			}

			KtCallExpression declaration = findDependencyExpression(versionCandidate);
			if (declaration != null) {
				DependencySite site = KotlinDslParser.parseDependencySite(declaration, propertyResolver);
				return coordinateSite(site, versionCandidate);
			}
		}

		if (element instanceof KtNameReferenceExpression propertyCandidate
				&& element.getParent() instanceof ValueArgument) {
			if (GradleUtils.isDependencySection(propertyCandidate.getReferencedName())) {
				return GradleVersionSite.absent();
			}

			KtCallExpression declaration = findDependencyExpression(propertyCandidate);
			if (declaration != null) {
				DependencySite site = locatePropertyUsage(propertyCandidate.getReferencedName(), declaration,
						propertyCandidate);
				return coordinateSite(site, propertyCandidate);
			}
		}

		return GradleVersionSite.absent();
	}

	private GradleVersionSite locateExtraProperty(@Nullable KtBinaryExpression propertyExpression,
			KtElement versionEntry) {

		if (propertyExpression == null) {
			return GradleVersionSite.absent();
		}

		String propertyName = findProperty(propertyExpression);
		if (!StringUtils.hasText(propertyName)) {
			return GradleVersionSite.absent();
		}

		return new BackingProperty(propertyName, versionEntry.getText(), propertyExpression, versionEntry);
	}

	private GradleVersionSite locatePropertyDeclaration(String propertyName, KtElement declaration) {

		PropertyValue propertyValue = propertyResolver.getPropertyValue(propertyName);
		if (propertyValue == null) {
			return GradleVersionSite.absent();
		}

		return new BackingProperty(propertyValue.getKey(), propertyValue.getValue(), declaration,
				propertyValue.getValueLiteral());
	}

	private @Nullable DependencySite locatePropertyUsage(String propertyName, KtCallExpression declaration,
			PsiElement declarationElement) {

		if (StringUtils.isEmpty(propertyName) || !propertyResolver.containsProperty(propertyName)) {
			return null;
		}

		return KotlinDslParser.parseDependencySite(declaration, propertyResolver);
	}

	private GradleVersionSite locateCatalogReference(KtElement element) {

		if (registry == null) {
			return GradleVersionSite.absent();
		}

		if (!(element instanceof KtDotQualifiedExpression dots)
				|| !(element.getParent() instanceof KtValueArgument arg)) {
			return GradleVersionSite.absent();
		}

		KtCallExpression catalogCall = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
		if (!KotlinDslUtils.isCatalogConsumerCall(catalogCall)) {
			return GradleVersionSite.absent();
		}

		TomlReference reference = TomlReference.from(KotlinDslParser.getSegments(dots),
				registry.catalogPaths().keySet());
		if (reference == null) {
			return GradleVersionSite.absent();
		}

		return new TomlCatalogAlias(reference, catalogCall);
	}

	/**
	 * Wrap a parser-derived {@link DependencySite} as a {@link CoordinateSite}
	 * anchored at the given version element, or {@link GradleVersionSite#absent()}
	 * when no site could be derived.
	 */
	private static GradleVersionSite coordinateSite(@Nullable DependencySite site, PsiElement versionElement) {

		if (site == null) {
			return GradleVersionSite.absent();
		}

		return new CoordinateSite(site.getArtifactId(), site.getVersionSource(), site.getDeclarationSource(),
				site.getDeclarationElement(), versionElement, GradleVersionSite.versionOf(site));
	}

}
