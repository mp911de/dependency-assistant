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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GradleVersionSite.BackingProperty;
import biz.paluch.dap.gradle.GradleVersionSite.DirectCoordinate;
import biz.paluch.dap.gradle.GradleVersionSite.MapLiteralVersion;
import biz.paluch.dap.gradle.GradleVersionSite.MapPropertyVersion;
import biz.paluch.dap.gradle.GradleVersionSite.PluginVersion;
import biz.paluch.dap.gradle.GradleVersionSite.TomlCatalogAlias;
import biz.paluch.dap.gradle.GradleVersionSite.VersionBlockPreferLiteral;
import biz.paluch.dap.gradle.GradleVersionSite.VersionBlockPreferProperty;
import biz.paluch.dap.gradle.GradleVersionSite.VersionBlockStrictlyLiteral;
import biz.paluch.dap.gradle.GradleVersionSite.VersionBlockStrictlyProperty;
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
	 * <p>
	 * Used by lookup-site resolution to map version literals, named arguments,
	 * and version-constraint entries back to their declaration call.
	 */
	public static @Nullable KtCallExpression findDependencyExpression(PsiElement element) {

		if (PsiTreeUtil.getParentOfType(element, KtArrayAccessExpression.class) != null) {
			return null;
		}

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(element, KtBinaryExpression.class);
		KtCallExpression call = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);

		if (call != null && KotlinDslUtils.isDependencyCall(call)) {

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

				KtCallExpression depCall = PsiTreeUtil.getParentOfType(versionCall, KtCallExpression.class);
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
	 * <p>
	 * Used for literal entries nested within property initializers.
	 */
	public static @Nullable KtProperty findProperty(KtElement element) {
		return element instanceof KtLiteralStringTemplateEntry entry
				? PsiTreeUtil.getParentOfType(element, KtProperty.class)
				: null;
	}

	/**
	 * Find the {@code extra["key"] = ...} assignment that owns the given value PSI.
	 * <p>
	 * Also supports the {@code "value".also { extra["key"] = it }} form.
	 */
	public static @Nullable KtBinaryExpression findPropertyExpression(KtElement element) {

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
	 * Locate the {@link GradleVersionSite} owning the given Kotlin PSI element.
	 * <p>
	 * Supports direct dependency literals, property-backed declarations,
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
				return classifyDependencySite(site, propertyCandidate, dependencyExpression);
			}

			if (dependencyExpression != null) {
				DependencySite site = KotlinDslParser.parseDependencySite(dependencyExpression, propertyResolver);
				return classifyDependencySite(site, propertyCandidate, dependencyExpression);
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
						DependencySite site = KotlinDslParser.parseDependencySite(declaration, propertyResolver);
						return classifyDependencySite(site, expression, declaration);
					}

					return GradleVersionSite.absent();
				}
			}

			KtBinaryExpression propertyExpression = findPropertyExpression(versionCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return locateExtraProperty(propertyExpression, versionCandidate);
			}

			KtCallExpression declaration = findDependencyExpression(versionCandidate);
			if (declaration != null) {
				DependencySite site = KotlinDslParser.parseDependencySite(declaration, propertyResolver);
				return classifyDependencySite(site, versionCandidate, declaration);
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
				return classifyDependencySite(site, propertyCandidate, declaration);
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

	private GradleVersionSite classifyDependencySite(@Nullable DependencySite site, PsiElement versionElement,
			KtCallExpression declaration) {

		if (site == null) {
			return GradleVersionSite.absent();
		}

		ArtifactId id = site.getArtifactId();
		VersionSource source = site.getVersionSource();
		PsiElement declarationElement = site.getDeclarationElement();
		ArtifactVersion version = GradleVersionSite.versionOf(site);
		boolean isProperty = source.isProperty();

		if (KotlinDslUtils.isInsidePluginsBlock(declaration)) {
			return new PluginVersion(id, source, declarationElement, versionElement, version);
		}

		String enclosingCallName = enclosingCallName(versionElement);
		if (GradleVersionConstraint.PREFER.equals(enclosingCallName)) {
			return isProperty
					? new VersionBlockPreferProperty(id, propertyNameOf(source), source, declarationElement,
							versionElement, version)
					: new VersionBlockPreferLiteral(id, source, declarationElement, versionElement, version);
		}

		if (GradleVersionConstraint.STRICTLY.equals(enclosingCallName)) {
			return isProperty
					? new VersionBlockStrictlyProperty(id, propertyNameOf(source), source, declarationElement,
							versionElement, version)
					: new VersionBlockStrictlyLiteral(id, source, declarationElement, versionElement, version);
		}

		if (isNamedVersionArgument(versionElement)) {
			return isProperty
					? new MapPropertyVersion(id, propertyNameOf(source), source, declarationElement, versionElement,
							version)
					: new MapLiteralVersion(id, source, declarationElement, versionElement, version);
		}

		return new DirectCoordinate(id, source, declarationElement, versionElement, version);
	}

	private static String propertyNameOf(VersionSource source) {

		if (source instanceof VersionSource.VersionProperty property) {
			return property.getProperty();
		}
		return "";
	}

	private static @Nullable String enclosingCallName(PsiElement element) {

		KtCallExpression enclosing = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
		return enclosing != null ? KotlinDslUtils.getKotlinCallName(enclosing) : null;
	}

	private static boolean isNamedVersionArgument(PsiElement element) {

		KtValueArgument valueArgument = PsiTreeUtil.getParentOfType(element, KtValueArgument.class);
		if (valueArgument == null || valueArgument.getArgumentName() == null) {
			return false;
		}
		return GradleUtils.VERSION.equals(valueArgument.getArgumentName().getAsName().asString());
	}

}
