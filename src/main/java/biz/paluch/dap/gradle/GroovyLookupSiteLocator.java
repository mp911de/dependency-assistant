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
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.Property;
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
 * Groovy DSL PSI locator for semantic {@link LookupSite lookup sites} and
 * declaration-aware dependency lookups.
 *
 * @author Mark Paluch
 */
class GroovyLookupSiteLocator implements LookupSiteLocator<GroovyPsiElement> {

	private final PropertyResolver propertyResolver;

	private final @Nullable VersionCatalogRegistry registry;

	GroovyLookupSiteLocator(PropertyResolver propertyResolver) {
		this(propertyResolver, null);
	}

	GroovyLookupSiteLocator(PropertyResolver propertyResolver, @Nullable VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
	}

	/**
	 * Locate the semantic {@link LookupSite} owning the given Groovy PSI element.
	 * <p>Supports direct dependency literals, property-backed declarations, and
	 * version catalog references such as: <pre class="code">
	 * implementation 'org.springframework:spring-core:6.2.0'
	 * implementation "org.springframework:spring-core:$springVersion"
	 * ext.springVersion = '6.2.0'
	 * implementation libs.spring.core
	 * </pre>
	 *
	 * @param element the PSI element to inspect.
	 * @return the resolved lookup site, or {@link LookupSite#absent()} if no
	 * supported declaration can be derived.
	 */
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

			return LookupSite.from(findDependencySite(literal, propertyResolver));
		}

		if (element instanceof GrReferenceExpression referenceExpression) {
			return locatePropertyReference(referenceExpression);
		}

		return LookupSite.absent();
	}

	/**
	 * Locate a {@link DependencySite} from a Groovy dependency or plugin call.
	 * <p>Supports direct string notation, named-argument declarations, version
	 * blocks, and plugin declarations such as: <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * implementation('org.junit.jupiter:junit-jupiter') { version { prefer '5.11.0' } }
	 * id 'org.springframework.boot' version '3.3.2'
	 * </pre>
	 *
	 * @param call the method call to inspect.
	 * @return the dependency site, or {@code null} if the call does not represent a
	 * supported declaration.
	 */
	@Nullable
	DependencySite locateDeclaration(GrMethodCall call) {

		if (!GradleParser.isDependencyCallCandidate(call)) {
			return null;
		}

		String methodName = GroovyDslUtils.getGroovyMethodName(call);
		boolean isPlugin = GroovyDslUtils.isInsidePluginsBlock(call);
		if (isPlugin && GradleUtils.isPlugin(methodName)) {
			return locatePluginDeclaration(call);
		}

		if (GradleParser.isMapStyleDeclarationCandidate(call)) {
			DependencySite site = GradleParser.parseMapDeclaration(call, propertyResolver);
			if (site != null) {
				return site;
			}
		}

		return GradleParser.parseDependency(call, propertyResolver);
	}

	private @Nullable DependencySite locatePluginDeclaration(GrMethodCall call) {

		PluginId pluginId = GroovyPluginIds.fromMethodCall(call, propertyResolver);
		return pluginId != null ? pluginId.toDependencySite() : null;
	}

	private LookupSite locatePropertyLiteral(GrLiteral literal) {

		Property property = GroovyDslUtils.findGroovyExtPropertyVersionElement(literal);
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

		PropertyValue resolved = propertyResolver.getPropertyValue(refName);
		if (resolved == null) {
			return LookupSite.absent();
		}

		DependencySite site = resolvePropertyDeclaration(refExpr);
		if (site == null) {
			return LookupSite.absent();
		}

		return LookupSite.from(site);
	}

	private @Nullable DependencySite resolvePropertyDeclaration(
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

					if (GradleParser.isMapStyleDeclarationCandidate(declarationCall)) {
						DependencySite site = GradleParser.parseMapDeclaration(declarationCall,
								propertyResolver);
						if (site != null) {
							return site;
						}
					}

					DependencySite site = GradleParser.parseVersionBlockDependency(declarationCall, propertyResolver);
					if (site != null) {
						return site;
					}
				}
			}
		}

		GrMethodCall enclosingCall = PsiTreeUtil.getParentOfType(refExpr, GrMethodCall.class);
		if (enclosingCall != null
				&& GradleVersionConstraint.isConstraint(GroovyDslUtils.getGroovyMethodName(enclosingCall))) {
			/*
			 * if (PsiTreeUtil.getParentOfType(refExpr, GrStringInjection.class) == null) {
			 * return null; }
			 */

			GrMethodCall dependencyCall = findVersionBlockDependencyCall(enclosingCall);
			if (dependencyCall != null) {
				DependencySite site = GradleParser.parseMapDeclaration(dependencyCall,
						propertyResolver);

				if (site != null) {
					return site;
				}

				return GradleParser.parseVersionBlockDependency(dependencyCall, propertyResolver);
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
	 * Resolve a version-block literal inside a Groovy dependency declaration.
	 * <p>Matches literals used in declarations such as: <pre class="code">
	 * implementation('org.junit.jupiter:junit-jupiter') {
	 *     version {
	 *         prefer '5.11.0'
	 *     }
	 * }
	 * </pre>
	 *
	 * @param literal the literal to inspect.
	 * @param scriptProperties property resolver used for dependency metadata.
	 * @return the resolved dependency site, or {@code null} if the literal does not
	 * belong to a supported version block.
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
			String text = GroovyDslUtils.getText(literal);
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
				String text = GroovyDslUtils.getText(lit);
				if (text.split(":").length == 2) {
					gavLiteral = lit;
					break;
				}
			}
		}

		if (gavLiteral == null) {
			return null;
		}

		String gavText = GroovyDslUtils.getText(gavLiteral);
		if (StringUtils.isEmpty(gavText)) {
			return null;
		}
		String[] parts = gavText.split(":");
		String group = parts[0];
		String artifact = parts[1];
		String version = GroovyDslUtils.getText(literal);

		ArtifactId artifactId = GradleDependency.getArtifactId(group, artifact, scriptProperties);

		return ArtifactVersion.from(version).map(
				it -> VersionedDependencySite.of(artifactId, it, VersionSource.declared(version), depCall, literal))
				.orElse(null);
	}

	/**
	 * Locate a {@link DependencySite} for a Groovy version literal.
	 * <p>Supports direct string notation, named-argument versions, version-block
	 * constraints, and plugin versions such as: <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * implementation('org.junit.jupiter:junit-jupiter') { version { prefer '5.11.0' } }
	 * id 'org.springframework.boot' version '3.3.2'
	 * </pre>
	 *
	 * @param element the literal to inspect.
	 * @param scriptProperties property resolver used for property-backed versions.
	 * @return the dependency site, or {@code null} if the literal is not part of a
	 * supported declaration.
	 */
	public static @Nullable DependencySite findDependencySite(GrLiteral element,
			PropertyResolver scriptProperties) {

		// Check if this literal is inside a prefer/strictly call inside a version {}
		// closure
		DependencySite site = resolveVersionBlockLiteral(element, scriptProperties);
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

		String text = GroovyDslUtils.getText(element);
		if (StringUtils.isEmpty(text)) {
			return null;
		}

		if (GradleParser.isMapStyleDeclarationCandidate(call)) {
			GrNamedArgument[] namedArguments = call.getNamedArguments();
			if (namedArguments.length >= 2 && element.getParent() instanceof GrNamedArgument namedArgument) {
				if ("version".equals(namedArgument.getLabelName())) {
					site = GradleParser.parseMapDeclaration(call, scriptProperties);
					if (site != null) {
						return site;
					}
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
	 * Resolve a plugin version literal declared inside a Groovy {@code plugins {}}
	 * block. <pre class="code">
	 * plugins {
	 *     id 'org.springframework.boot' version '3.3.2'
	 * }
	 * </pre>
	 * <p>The plugin ID is used as both {@link ArtifactId#groupId()} and
	 * {@link ArtifactId#artifactId()}, matching the convention used throughout the
	 * Gradle plugin support.
	 *
	 * @param call the outer {@code version} call.
	 * @param scriptProperties property resolver used for plugin ID parsing.
	 * @return the dependency site, or {@code null} if the call does not match the
	 * supported plugin declaration shape.
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

		PluginId id = GroovyPluginIds.fromMethodCall(idCall, scriptProperties);
		return id != null ? id.toDependencySite() : null;
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
