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
import biz.paluch.dap.gradle.GradleParserSupport.NamedDependencyDeclaration;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jspecify.annotations.Nullable;

/**
 * Groovy PSI locator for semantic version and declaration sites.
 *
 * @author Mark Paluch
 */
class GroovyVersionSiteLocator {

	private final PropertyResolver propertyResolver;

	private final @Nullable VersionCatalogRegistry registry;

	GroovyVersionSiteLocator(PropertyResolver propertyResolver) {
		this(propertyResolver, null);
	}

	GroovyVersionSiteLocator(PropertyResolver propertyResolver, @Nullable VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
	}

	@Nullable
	GradleLookupSite locate(PsiElement element) {

		GradleLookupSite catalogReference = locateCatalogReference(element);
		if (catalogReference != null) {
			return catalogReference;
		}

		if (element instanceof GrLiteral literal) {
			GradleLookupSite.GradleVersionSite versionSite = locateVersionSite(literal);
			if (versionSite != null) {
				return versionSite;
			}
			return locatePropertyLiteral(literal);
		}

		GrReferenceExpression referenceExpression = element instanceof GrReferenceExpression reference ? reference
				: PsiTreeUtil.getParentOfType(element, GrReferenceExpression.class);
		if (referenceExpression != null
				&& PsiTreeUtil.getParentOfType(referenceExpression, GrStringInjection.class) == null) {
			return locatePropertyReference(referenceExpression);
		}

		return null;
	}

	@Nullable
	GradleDeclarationSite locateDeclaration(GrMethodCall call) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);
		boolean isPlugin = GroovyDslUtils.isInsidePluginsBlock(call);
		if (isPlugin && GradleUtils.isPlugin(methodName)) {
			return locatePluginDeclaration(call);
		}

		if (!GradleUtils.isDependencySection(methodName) && !GradleUtils.isPlatformSection(methodName)) {
			return null;
		}

		GradleDeclarationSite directDeclaration = locateDirectDeclaration(call);
		if (directDeclaration != null) {
			return directDeclaration;
		}

		GradleDeclarationSite versionBlockDeclaration = GradleDeclarationSite
				.from(GradleParser.parseVersionBlockDependency(call, propertyResolver), propertyResolver);
		if (versionBlockDeclaration != null) {
			return versionBlockDeclaration;
		}

		GrNamedArgument[] namedArguments = call.getNamedArguments();
		if (namedArguments.length >= 2) {
			return GradleDeclarationSite.from(GradleParser.parseMapDependency(call, namedArguments, propertyResolver),
					propertyResolver);
		}

		return null;
	}

	private @Nullable GradleDeclarationSite locatePluginDeclaration(GrMethodCall call) {

		GroovyDslUtils.PluginId pluginId = GroovyDslUtils.PluginId.fromMethodCall(call, propertyResolver);
		if (pluginId == null) {
			return null;
		}

		ArtifactId artifactId = pluginId.toValidatedArtifactId();
		if (artifactId == null) {
			return null;
		}

		return GradleDeclarationSite.fromPlugin(artifactId, call, pluginId.version(), pluginId.getVersionAsString());
	}

	private @Nullable GradleDeclarationSite locateDirectDeclaration(GrMethodCall call) {

		for (PsiElement arg : call.getArgumentList().getAllArguments()) {
			PsiElement candidate = arg;
			if (arg instanceof GrMethodCall innerCall
					&& GradleUtils.isPlatformSection(GroovyDslUtils.getGroovyMethodName(innerCall))) {
				PsiElement[] innerArgs = innerCall.getArgumentList().getAllArguments();
				candidate = innerArgs.length > 0 ? innerArgs[0] : null;
			}

			if (!(candidate instanceof GrLiteral literal)) {
				continue;
			}

			DependencyAndVersionLocation location = GroovyDslUtils.findGroovyVersionElement(literal, propertyResolver);
			GradleDeclarationSite declarationSite = GradleDeclarationSite.from(location, call);
			if (declarationSite != null) {
				return declarationSite;
			}
		}

		return null;
	}

	private GradleLookupSite.@Nullable GradleVersionSite locateVersionSite(GrLiteral literal) {

		DependencyAndVersionLocation location = GroovyDslUtils.findGroovyVersionElement(literal, propertyResolver);
		if (location == null) {
			return null;
		}

		GrMethodCall declarationCall = PsiTreeUtil.getParentOfType(location.version(), GrMethodCall.class);
		if (declarationCall == null) {
			return null;
		}

		return new GradleLookupSite.GradleVersionSite(location.dependency(), declarationCall, location.version());
	}

	private @Nullable GradlePropertySite locatePropertyLiteral(GrLiteral literal) {

		PsiPropertyValueElement property = GroovyDslUtils.findGroovyExtPropertyVersionElement(literal);
		if (property == null) {
			return null;
		}

		return GradleLookupSite.property(property.propertyKey(), property.propertyValue(), literal, literal, null);
	}

	private @Nullable GradlePropertySite locatePropertyReference(GrReferenceExpression refExpr) {

		String refName = refExpr.getReferenceName();
		if (!StringUtils.hasText(refName)) {
			return null;
		}

		PsiPropertyValueElement resolved = propertyResolver.getElement(refName);
		if (resolved == null) {
			return null;
		}

		NamedDependencyDeclaration declaration = resolvePropertyDeclaration(refExpr);
		if (declaration == null || !declaration.isComplete()) {
			return null;
		}

		return GradleLookupSite.property(refName, resolved.propertyValue(), declaration.declaration(),
				resolved.element(),
				declaration.toDependency(propertyResolver).getId());
	}

	private @Nullable NamedDependencyDeclaration resolvePropertyDeclaration(
			GrReferenceExpression refExpr) {

		GrMethodCall enclosingCall = PsiTreeUtil.getParentOfType(refExpr, GrMethodCall.class);
		if (enclosingCall != null
				&& GradleVersionConstraint.isConstraint(GroovyDslUtils.getGroovyMethodName(enclosingCall))) {
			GrMethodCall dependencyCall = GroovyDslUtils.findVersionBlockDependencyCall(enclosingCall);
			if (dependencyCall != null) {
				return GradleParser.parseVersionBlockDependency(dependencyCall, propertyResolver);
			}
		}

		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(refExpr, GrNamedArgument.class);
		if (namedArgument != null && namedArgument.getExpression() == refExpr
				&& "version".equals(namedArgument.getLabelName())) {
			GrMethodCall declarationCall = PsiTreeUtil.getParentOfType(namedArgument, GrMethodCall.class);
			if (declarationCall != null) {
				GrNamedArgument[] namedArguments = declarationCall.getNamedArguments();
				if (namedArguments.length >= 2) {
					return GradleParser.parseMapDependency(declarationCall, namedArguments, propertyResolver);
				}
			}
		}

		return null;
	}

	private @Nullable GradleLookupSite locateCatalogReference(PsiElement element) {

		if (registry == null) {
			return null;
		}

		GrMethodCall catalogCall = GroovyDslUtils.findEnclosingGroovyCatalogAccessorCall(element, registry);
		if (catalogCall == null) {
			return null;
		}

		PsiElement argument = GroovyDslUtils.getFirstGroovyCatalogArgumentExpression(catalogCall);
		if (!(argument instanceof org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression expression)) {
			return null;
		}

		TomlReference reference = GroovyDslUtils.getTomlReference(expression, registry.catalogPaths().keySet());
		return reference != null ? GradleLookupSite.from(reference, catalogCall) : null;
	}

}
