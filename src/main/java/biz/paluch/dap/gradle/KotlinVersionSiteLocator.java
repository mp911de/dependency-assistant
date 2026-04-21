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

import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PsiPropertyValueElement;
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
class KotlinVersionSiteLocator {

	private final PropertyResolver propertyResolver;

	private final @Nullable VersionCatalogRegistry registry;

	KotlinVersionSiteLocator(PropertyResolver propertyResolver) {
		this(propertyResolver, null);
	}

	KotlinVersionSiteLocator(PropertyResolver propertyResolver, @Nullable VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
	}

	@Nullable
	public GradleLookupSite locate(KtElement element) {

		GradleLookupSite catalogReference = locateCatalogReference(element);
		if (catalogReference != null) {
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
				return locatePropertyUsage(literals.getProperty(), dependencyExpression, propertyCandidate);
			}

			if (dependencyExpression != null) {
				return KotlinDslParser.findKotlinVersionElement(dependencyExpression,
						propertyResolver);
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
					return null;
				}
			}

			KtBinaryExpression propertyExpression = KotlinDslUtils.findPropertyExpression(versionCandidate);
			if (KotlinDslExtraParser.isExtra(propertyExpression)) {
				return locateExtraProperty(propertyExpression, versionCandidate);
			}

			KtCallExpression declaration = KotlinDslUtils.findDependencyExpression(versionCandidate);
			if (declaration != null) {
				GradleLookupSite.GradleVersionSite site = KotlinDslParser
						.findDependencyAndVersionLocationFromMapDeclaration(declaration,
								versionCandidate, propertyResolver);
				if (site != null) {
					return site;
				}

				return KotlinDslParser.findKotlinVersionElement(declaration, propertyResolver);
			}
		}

		if (element instanceof KtNameReferenceExpression propertyCandidate
				&& element.getParent() instanceof ValueArgument) {
			if (GradleUtils.isDependencySection(propertyCandidate.getReferencedName())) {
				return null;
			}

			KtCallExpression declaration = KotlinDslUtils.findDependencyExpression(propertyCandidate);
			if (declaration != null) {
				return locatePropertyUsage(propertyCandidate.getReferencedName(), declaration, propertyCandidate);
			}
		}

		return null;
	}

	@Nullable
	public GradleDeclarationSite locateDeclaration(KtCallElement call) {

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (!StringUtils.hasText(methodName)) {
			return null;
		}

		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isPlugin = KotlinDslUtils.isInsidePluginsBlock(call);
		if (!isDependencyConfig && !isPlatform && !isPlugin) {
			return null;
		}

		if (isPlugin) {
			return GradleDeclarationSite
					.from(locatePluginDeclaration(call));
		}

		if (!isDependencyConfig && !isPlatform) {
			return null;
		}

		if (!KotlinDslUtils.isInsideBlock(call, "dependencies"::equals) && !isPlatform) {
			return null;
		}

		GradleDeclarationSite versionBlockDeclaration = GradleDeclarationSite
				.from(KotlinDslParser.parseVersionBlockDependency(call, propertyResolver), propertyResolver);
		if (versionBlockDeclaration != null) {
			return versionBlockDeclaration;
		}

		GradleDeclarationSite directDeclaration = GradleDeclarationSite
				.from(KotlinDslParser.findKotlinVersionElement(call, propertyResolver));
		if (directDeclaration != null) {
			return directDeclaration;
		}

		return GradleDeclarationSite.from(KotlinDslParser.parseMapDeclaration(call, propertyResolver),
				propertyResolver);
	}

	private GradleLookupSite.@Nullable GradleVersionSite locatePluginDeclaration(KtCallElement call) {
		return KotlinDslUtils.findKotlinPluginLocation(call,
				PsiTreeUtil.getParentOfType(call, KtBinaryExpression.class), propertyResolver);
	}

	private @Nullable GradlePropertySite locateExtraProperty(@Nullable KtBinaryExpression propertyExpression,
			KtElement versionEntry) {

		if (propertyExpression == null) {
			return null;
		}

		String propertyName = KotlinDslUtils.findProperty(propertyExpression);
		if (!StringUtils.hasText(propertyName)) {
			return null;
		}

		return GradleLookupSite.property(propertyName, versionEntry.getText(), propertyExpression, versionEntry, null);
	}

	private @Nullable GradlePropertySite locatePropertyDeclaration(String propertyName, KtProperty property) {

		PsiPropertyValueElement element = propertyResolver.getElement(propertyName);
		if (element == null) {
			return null;
		}

		return GradleLookupSite.property(propertyName, element.propertyValue(), property, element.element(), null);
	}

	private @Nullable GradlePropertySite locatePropertyUsage(String propertyName, KtCallExpression declaration,
			PsiElement declarationElement) {

		if (!StringUtils.hasText(propertyName)) {
			return null;
		}

		PsiPropertyValueElement element = propertyResolver.getElement(propertyName);
		if (element == null) {
			return null;
		}

		GradleDeclarationSite declarationSite = locateDeclaration(declaration);
		return GradleLookupSite.property(propertyName, element.propertyValue(), declaration,
				element.element(), declarationSite != null ? declarationSite.artifactId() : null);
	}

	private @Nullable GradleLookupSite locateCatalogReference(KtElement element) {

		if (registry == null) {
			return null;
		}

		PsiElement current = element;
		while (current != null) {
			if (current instanceof KtExpression expression) {
				TomlReference reference = TomlReference.from(KotlinDslUtils.collectKotlinCatalogDotSegments(expression),
						registry.catalogPaths().keySet());
				if (reference != null) {
					KtCallExpression catalogCall = PsiTreeUtil.getParentOfType(expression, KtCallExpression.class);
					if (isCatalogConsumerCall(catalogCall)) {
						return GradleLookupSite.from(reference, catalogCall);
					}
				}
			}
			current = current.getParent();
		}

		return null;
	}

	private static boolean isCatalogConsumerCall(@Nullable KtCallExpression call) {

		if (call == null) {
			return false;
		}

		String name = KotlinDslUtils.getKotlinCallName(call);
		if (!StringUtils.hasText(name)) {
			return false;
		}
		if ("alias".equals(name)) {
			return true;
		}
		if (GradleUtils.isPlugin(name) && KotlinDslUtils.isInsidePluginsBlock(call)) {
			return true;
		}
		return GradleUtils.isDependencySection(name) || GradleUtils.isPlatformSection(name);
	}

}
