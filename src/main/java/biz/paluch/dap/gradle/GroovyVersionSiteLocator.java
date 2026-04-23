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
import biz.paluch.dap.gradle.GradleParserSupport.NamedDependencyDeclaration;
import biz.paluch.dap.gradle.GroovyDslUtils.PluginId;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.support.VersionedDependencySite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
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
class GroovyVersionSiteLocator implements LookupSiteLocator<GroovyPsiElement> {

	private final PropertyResolver propertyResolver;

	private final @Nullable VersionCatalogRegistry registry;

	GroovyVersionSiteLocator(PropertyResolver propertyResolver) {
		this(propertyResolver, null);
	}

	GroovyVersionSiteLocator(PropertyResolver propertyResolver, @Nullable VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
	}

	@Override
	public LookupSite locate(GroovyPsiElement element) {

		LookupSite catalogReference = locateCatalogReference(element);
		if (catalogReference.isPresent()) {
			return catalogReference;
		}

		if (element instanceof GrLiteral literal) {

			LookupSite propertySite = locatePropertyLiteral(literal);
			if (propertySite.isPresent()) {
				return propertySite;
			}

			return LookupSite.from(findGroovyVersionElement(literal, propertyResolver));
		}

		if (element instanceof GrReferenceExpression referenceExpression) {
			return locatePropertyReference(referenceExpression);
		}

		return LookupSite.absent();
	}

	@Nullable
	DependencySite locateDeclaration(GrMethodCall call) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);
		boolean isPlugin = GroovyDslUtils.isInsidePluginsBlock(call);
		if (isPlugin && GradleUtils.isPlugin(methodName)) {
			return locatePluginDeclaration(call);
		}

		if (!GradleUtils.isDependencySection(methodName) && !GradleUtils.isPlatformSection(methodName)) {
			return null;
		}

		DependencySite directDeclaration = locateDirectDeclaration(call);
		if (directDeclaration != null) {
			return directDeclaration;
		}

		NamedDependencyDeclaration declaration = GradleParser.parseNamedDependencyDeclaration(call, propertyResolver);

		if (declaration.isComplete()) {
			return declaration.toDependencySite(propertyResolver);
		}

		return null;
	}

	private @Nullable DependencySite locatePluginDeclaration(GrMethodCall call) {

		GroovyDslUtils.PluginId pluginId = GroovyDslUtils.PluginId.fromMethodCall(call, propertyResolver);
		if (pluginId == null) {
			return null;
		}

		ArtifactId artifactId = pluginId.toValidatedArtifactId();
		if (artifactId == null) {
			return null;
		}

		return GradleDependency.of(artifactId, PropertyExpression.from(pluginId.getVersionAsString()))
				.toDependencySite(call, pluginId.version());
	}

	private @Nullable DependencySite locateDirectDeclaration(GrMethodCall call) {

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

			return findGroovyVersionElement(literal, propertyResolver);
		}

		return null;
	}

	private LookupSite locatePropertyLiteral(GrLiteral literal) {

		PropertyValue property = GroovyDslUtils.findGroovyExtPropertyVersionElement(literal);
		if (property == null && propertyResolver instanceof GradlePropertyResolver resolver) {
			property = resolver.findBindingForValueLiteral(literal);
		}
		if (property == null) {
			return LookupSite.absent();
		}

		return LookupSite.ofProperty(property, literal);
	}

	private LookupSite locatePropertyReference(GrReferenceExpression refExpr) {

		String refName = refExpr.getReferenceName();
		if (!StringUtils.hasText(refName)) {
			return LookupSite.absent();
		}

		PropertyValue resolved = propertyResolver.getElement(refName);
		if (resolved == null) {
			return LookupSite.absent();
		}

		NamedDependencyDeclaration declaration = resolvePropertyDeclaration(refExpr);
		if (declaration == null || !declaration.isComplete()) {
			return LookupSite.absent();
		}

		return LookupSite.from(declaration.toDependencySite(propertyResolver));
	}

	private @Nullable NamedDependencyDeclaration resolvePropertyDeclaration(
			GrReferenceExpression refExpr) {

		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(refExpr, GrNamedArgument.class);
		if (namedArgument != null && "version".equals(namedArgument.getLabelName())) {
			if (PsiTreeUtil.getParentOfType(refExpr, GrStringInjection.class) != null) {
				return null;
			}

			PsiElement versionExpr = namedArgument.getExpression();
			if (versionExpr != null
					&& (versionExpr == refExpr || PsiTreeUtil.isAncestor(versionExpr, refExpr, false))) {
				GrMethodCall declarationCall = PsiTreeUtil.getParentOfType(namedArgument, GrMethodCall.class);
				if (declarationCall != null) {
					return GradleParser.parseNamedDependencyDeclaration(declarationCall, propertyResolver);
				}
			}
		}

		GrMethodCall enclosingCall = PsiTreeUtil.getParentOfType(refExpr, GrMethodCall.class);
		if (enclosingCall != null
				&& GradleVersionConstraint.isConstraint(GroovyDslUtils.getGroovyMethodName(enclosingCall))) {
			if (PsiTreeUtil.getParentOfType(refExpr, GrStringInjection.class) == null) {
				return null;
			}

			GrMethodCall dependencyCall = findVersionBlockDependencyCall(enclosingCall);
			if (dependencyCall != null) {
				return GradleParser.parseNamedDependencyDeclaration(dependencyCall, propertyResolver);
			}
		}

		return null;
	}

	private LookupSite locateCatalogReference(PsiElement element) {

		if (registry == null) {
			return LookupSite.absent();
		}

		GrMethodCall catalogCall = GroovyDslUtils.findEnclosingGroovyCatalogAccessorCall(element, registry);
		if (catalogCall == null) {
			return LookupSite.absent();
		}

		PsiElement argument = GroovyDslUtils.getFirstGroovyCatalogArgumentExpression(catalogCall);
		if (!(argument instanceof GrExpression expression)) {
			return LookupSite.absent();
		}

		TomlReference reference = GroovyDslUtils.getTomlReference(expression, registry.catalogPaths().keySet());
		return reference != null ? LookupSite.ofTomlReference(reference, catalogCall) : LookupSite.absent();
	}

	/**
	 * Returns a {@link VersionedDependencySite} when {@code literal} is the version
	 * value inside a {@code prefer} or {@code strictly} call within a
	 * {@code version {}} block of a Groovy dependency declaration, or {@code null}
	 * otherwise.
	 */
	private static @Nullable VersionedDependencySite resolveVersionBlockLiteral(GrLiteral literal,
			PropertyResolver scriptProperties) {

		if (PsiTreeUtil.getParentOfType(literal, GrStringInjection.class) != null) {
			return null;
		}

		GrMethodCall preferOrStrictlyCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		if (preferOrStrictlyCall == null) {
			return null;
		}
		String innerName = GroovyDslUtils.getGroovyMethodName(preferOrStrictlyCall);
		if (!GradleVersionConstraint.PREFER.equals(innerName) && !GradleVersionConstraint.STRICTLY.equals(innerName)) {
			return null;
		}

		PsiElement[] strictlyArgs = preferOrStrictlyCall.getArgumentList().getAllArguments();
		if (strictlyArgs.length > 0 && strictlyArgs[0].getText().contains("${")
				&& PsiTreeUtil.isAncestor(strictlyArgs[0], literal, false)) {
			return null;
		}

		if (GradleVersionConstraint.STRICTLY.equals(innerName)) {
			String text = GroovyDslUtils.renderText(literal);
			if (GradleUtils.isVersionRange(text)) {
				return null;
			}
		}

		GrMethodCall depCall = findVersionBlockDependencyCall(preferOrStrictlyCall);
		if (depCall == null) {
			return null;
		}

		GrLiteral gavLiteral = null;
		for (PsiElement arg : depCall.getArgumentList().getAllArguments()) {
			if (arg instanceof GrLiteral lit) {
				String text = GroovyDslUtils.renderText(lit);
				if (text.split(":").length == 2) {
					gavLiteral = lit;
					break;
				}
			}
		}

		if (gavLiteral == null) {
			return null;
		}

		String gavText = GroovyDslUtils.renderText(gavLiteral);
		if (StringUtils.isEmpty(gavText)) {
			return null;
		}
		String[] parts = gavText.split(":");
		String group = parts[0];
		String artifact = parts[1];
		String version = GroovyDslUtils.renderText(literal);

		ArtifactId artifactId = GradleDependency.getArtifactId(group, artifact, scriptProperties);

		return ArtifactVersion.from(version).map(
				it -> VersionedDependencySite.of(artifactId, it, VersionSource.declared(version), depCall, literal))
				.orElse(null);
	}

	/**
	 * Returns the version segment {@link PsiElement} if the caret is inside the
	 * version part of a Groovy string-notation dependency
	 * ({@code 'group:artifact:version'}), or {@code null} if the element is not in
	 * such a position.
	 */
	public static @Nullable DependencySite findGroovyVersionElement(GrLiteral element,
			PropertyResolver scriptProperties) {

		// Check if this literal is inside a prefer/strictly call inside a version {}
		// closure
		VersionedDependencySite site = resolveVersionBlockLiteral(element, scriptProperties);
		if (site != null) {
			return site;
		}

		GrMethodCall call = PsiTreeUtil.getParentOfType(element, GrMethodCall.class);
		if (call == null) {
			return null;
		}
		String callName = GroovyDslUtils.getGroovyMethodName(call);
		if (!GradleUtils.isDependencySection(callName) && !GradleUtils.isPlatformSection(callName)) {
			// Not a standard dependency/platform call; check the plugin version pattern:
			// id 'pluginId' version 'x.y.z'
			return resolvePluginVersionLiteral(call, scriptProperties);
		}

		// Try getValue() first (fast path for single-quoted and non-interpolated
		// double-quoted literals).
		// Fall back to toString() which preserves ${…} placeholders for interpolated
		// GStrings.
		String text = GroovyDslUtils.renderText(element);
		if (StringUtils.isEmpty(text)) {
			return null;
		}

		GrNamedArgument[] namedArguments = call.getNamedArguments();
		if (namedArguments.length >= 2 && element.getParent() instanceof GrNamedArgument namedArgument) {
			String labelName = namedArgument.getLabelName();
			if ("version".equals(labelName)) {
				NamedDependencyDeclaration declaration = GradleParser.parseNamedDependencyDeclaration(call,
						scriptProperties);
				if (declaration.isComplete()) {
					return declaration.toDependencySite(scriptProperties);
				}
			}
		}

		String[] parts = text.split(":");
		if (parts.length < 3) {
			return null;
		}
		GradleDependency dependency = GradleDependency.parse(text);
		if (dependency == null || !dependency.getVersionSource().isDefined()) {
			return null;
		}

		return dependency.toDependencySite(call, element);
	}

	/**
	 * Returns a {@link DependencySite} when {@code literal} is the version value in
	 * a Groovy plugin declaration of the form {@code id 'pluginId' version 'x.y.z'}
	 * inside a {@code plugins {}} block, or {@code null} otherwise.
	 * <p>The plugin ID is used as both {@link ArtifactId#groupId()} and
	 * {@link ArtifactId#artifactId()}, matching the convention used throughout the
	 * rest of the Gradle plugin support.
	 */
	public static @Nullable DependencySite resolvePluginVersionLiteral(GrMethodCall call,
			PropertyResolver scriptProperties) {
		// The 'x.y.z' literal is an argument of the outer `version` method call.
		// call.invokedExpression must be a `version` GrReferenceExpression whose
		// qualifier
		// is the inner `id 'pluginId'` method call.
		if (!(call.getInvokedExpression() instanceof GrReferenceExpression versionRef)) {
			return null;
		}
		if (!"version".equals(versionRef.getReferenceName())) {
			return null;
		}

		if (!(versionRef.getQualifierExpression() instanceof GrMethodCall idCall)
				|| !GroovyDslUtils.isInsidePluginsBlock(idCall)) {
			return null;
		}

		if (!GradleUtils.isPlugin(GroovyDslUtils.getGroovyMethodName(idCall))) {
			return null;
		}

		PluginId id = PluginId.fromMethodCall(idCall, scriptProperties);
		if (id == null) {
			return null;
		}
		ArtifactId artifactId = id.toValidatedArtifactId();
		if (artifactId == null) {
			return null;
		}
		String version = GroovyDslUtils.renderText(id.version());
		PropertyExpression versionExpression = PropertyExpression.from(version);

		return GradleDependency.of(artifactId, versionExpression).toDependencySite(idCall, id.version());
	}

	/**
	 * Walks up from a {@code prefer} or {@code strictly} call to the enclosing
	 * dependency method call, returning the outer {@link GrMethodCall} when the
	 * full version-block structure is present, or {@code null} otherwise.
	 * <p>The expected structure is: <pre class="code">
	 * depCall("g:a") {          // outer GrMethodCall (returned)
	 *     version {             // version call inside dep closure
	 *         prefer "1.0"      // preferOrStrictlyCall (starting point)
	 *     }
	 * }
	 * </pre>
	 */
	static @Nullable GrMethodCall findVersionBlockDependencyCall(GrMethodCall preferOrStrictlyCall) {

		GrClosableBlock versionClosure = PsiTreeUtil.getParentOfType(preferOrStrictlyCall, GrClosableBlock.class);
		if (versionClosure == null) {
			return null;
		}

		GrMethodCall versionCall = PsiTreeUtil.getParentOfType(versionClosure, GrMethodCall.class);
		if (versionCall == null || !"version".equals(GroovyDslUtils.getGroovyMethodName(versionCall))) {
			return null;
		}

		GrClosableBlock depClosure = PsiTreeUtil.getParentOfType(versionCall, GrClosableBlock.class);
		if (depClosure == null) {
			return null;
		}

		GrMethodCall depCall = PsiTreeUtil.getParentOfType(depClosure, GrMethodCall.class);
		if (depCall == null) {
			return null;
		}
		String depCallName = GroovyDslUtils.getGroovyMethodName(depCall);
		if (!GradleUtils.isDependencySection(depCallName) && !GradleUtils.isPlatformSection(depCallName)) {
			return null;
		}
		return depCall;
	}


}
