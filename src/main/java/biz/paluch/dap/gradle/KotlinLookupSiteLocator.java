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

import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

/**
 * Kotlin PSI locator for semantic version and declaration sites.
 *
 * @author Mark Paluch
 */
class KotlinLookupSiteLocator implements LookupSiteLocator<KtElement> {

	private final PropertyResolver propertyResolver;

	private final @Nullable VersionCatalogRegistry registry;

	KotlinLookupSiteLocator(PropertyResolver propertyResolver) {
		this(propertyResolver, null);
	}

	KotlinLookupSiteLocator(PropertyResolver propertyResolver, @Nullable VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
	}

	/**
	 * Locate a {@link LookupSite} for the given element.
	 *
	 * @param element the element to inspect.
	 * @return the lookup site or {@link LookupSite#absent()} if the element does
	 * not represent a version element that is (within) a declaration.
	 */
	@Override
	public LookupSite locate(KtElement element) {

		LookupSite catalogReference = locateCatalogReference(element);
		if (catalogReference.isPresent()) {
			return catalogReference;
		}

		if (element instanceof KtStringTemplateExpression propertyCandidate) {
			KtBinaryExpression propertyExpression = KotlinDslUtils.findPropertyExpression(propertyCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return locateExtraProperty(propertyExpression, propertyCandidate);
			}
		}

		if (element instanceof KtBlockStringTemplateEntry propertyCandidate) {
			KtCallExpression dependencyExpression = KotlinDslUtils.findDependencyExpression(propertyCandidate);
			KtLiterals literals = KtLiterals.from(propertyCandidate);
			if (dependencyExpression != null && literals.hasProperty()) {
				return LookupSite
						.from(locatePropertyUsage(literals.getProperty(), dependencyExpression, propertyCandidate));
			}

			if (dependencyExpression != null) {
				return LookupSite.from(KotlinDslParser.findDependencySite(dependencyExpression,
						propertyResolver));
			}
		}

		if (element instanceof KtStringTemplateEntry versionCandidate) {

			KtProperty property = KotlinDslUtils.findProperty(versionCandidate);
			if (property != null && StringUtils.hasText(property.getName())) {
				return locatePropertyDeclaration(property.getName(), property);
			}

			if (versionCandidate.getParent() instanceof KtStringTemplateExpression expression) {
				PsiElement[] children = expression.getChildren();
				if (children.length > 1 && children[0] == element) {
					return LookupSite.absent();
				}
			}

			KtBinaryExpression propertyExpression = KotlinDslUtils.findPropertyExpression(versionCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return locateExtraProperty(propertyExpression, versionCandidate);
			}

			KtCallExpression declaration = KotlinDslUtils.findDependencyExpression(versionCandidate);
			if (declaration != null) {
				DependencySite site = KotlinDslParser.parseDependencySite(declaration, propertyResolver);
				if (site != null) {
					return LookupSite.from(site);
				}

				return LookupSite.from(KotlinDslParser.findDependencySite(declaration, propertyResolver));
			}
		}

		if (element instanceof KtNameReferenceExpression propertyCandidate
				&& element.getParent() instanceof ValueArgument) {
			if (GradleUtils.isDependencySection(propertyCandidate.getReferencedName())) {
				return LookupSite.absent();
			}

			KtCallExpression declaration = KotlinDslUtils.findDependencyExpression(propertyCandidate);
			if (declaration != null) {
				return LookupSite.from(
						locatePropertyUsage(propertyCandidate.getReferencedName(), declaration, propertyCandidate));
			}
		}

		return LookupSite.absent();
	}

	/**
	 * Locate a dependency declaration site from a Kotlin DSL call.
	 * @param call the call to inspect.
	 * @return the dependency declaration site or {@code null} if the call does not
	 * represent a valid dependency declaration.
	 */
	public @Nullable DependencySite locateDeclaration(KtCallElement call) {

		DependencySite site = KotlinDslParser.parseDependencySite(call, propertyResolver);
		if (site != null) {
			return site;
		}

		site = KotlinDslParser.findDependencySite(call, propertyResolver);
		if (site != null) {
			return site;
		}

		GradleParserSupport.NamedDependencyDeclaration declaration = KotlinDslParser.parseMapDeclaration(call,
				propertyResolver);
		if (declaration.isComplete()) {
			return declaration.toDependencySite(propertyResolver);
		}

		return KotlinDslParser.findDependencySite(call, propertyResolver);
	}

	private @Nullable DependencySite locatePluginDeclaration(KtCallElement call) {
		return KotlinDslUtils.findPluginSite(call,
				PsiTreeUtil.getParentOfType(call, KtBinaryExpression.class), propertyResolver);
	}

	private LookupSite locateExtraProperty(@Nullable KtBinaryExpression propertyExpression,
			KtElement versionEntry) {

		if (propertyExpression == null) {
			return LookupSite.absent();
		}

		String propertyName = KotlinDslUtils.findProperty(propertyExpression);
		if (!StringUtils.hasText(propertyName)) {
			return LookupSite.absent();
		}

		return LookupSite.ofProperty(propertyName, versionEntry.getText(), propertyExpression, versionEntry);
	}

	private LookupSite locatePropertyDeclaration(String propertyName, KtElement declaration) {

		PropertyValue propertyValue = propertyResolver.getElement(propertyName);
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

		// TODO
		return locateDeclaration(declaration);
	}

	private LookupSite locateCatalogReference(KtElement element) {

		if (registry == null) {
			return LookupSite.absent();
		}

		KtCallExpression catalogCall = PsiTreeUtil.getParentOfType(element, KtCallExpression.class);
		while (catalogCall != null && !isCatalogConsumerCall(catalogCall)) {
			catalogCall = PsiTreeUtil.getParentOfType(catalogCall, KtCallExpression.class);
		}
		if (catalogCall == null) {
			return LookupSite.absent();
		}

		KtExpression argument = KotlinDslUtils.getFirstValueArgument(catalogCall);
		if (argument == null) {
			return LookupSite.absent();
		}

		KtExpression canonicalAccessor = KotlinDslUtils.unwrapParenthesizedExpression(argument);
		if (!(element instanceof KtExpression ktExpr) || !canonicalAccessor.equals(ktExpr)) {
			return LookupSite.absent();
		}

		TomlReference reference = TomlReference.from(
				KotlinDslUtils.collectKotlinCatalogDotSegments(canonicalAccessor),
				registry.catalogPaths().keySet());
		if (reference == null) {
			return LookupSite.absent();
		}

		return LookupSite.ofTomlReference(reference, catalogCall);
	}

	private static boolean isCatalogConsumerCall(@Nullable KtCallExpression call) {

		if (call == null) {
			return false;
		}

		String name = KotlinDslUtils.getKotlinCallName(call);
		return GradleUtils.isCatalogConsumerCall(name)
				&& (!GradleUtils.isPlugin(name) || KotlinDslUtils.isInsidePluginsBlock(call));
	}

}
