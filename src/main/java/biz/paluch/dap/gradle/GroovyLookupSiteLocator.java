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

import java.util.List;

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
	 * <p>
	 * Supports direct dependency literals, property-backed declarations, and
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

			LookupSite versionBlockSite = locateVersionBlockLiteral(literal);
			if (versionBlockSite.isPresent()) {
				return versionBlockSite;
			}

			LookupSite propertySite = locatePropertyLiteral(literal);
			if (propertySite.isPresent()) {
				return propertySite;
			}

			return LookupSite.from(findDependencySite(literal, propertyResolver));
		}

		if (element instanceof GrReferenceExpression referenceExpression) {
			LookupSite commandPlatformSite = locateCommandPlatformString(referenceExpression);
			if (commandPlatformSite.isPresent()) {
				return commandPlatformSite;
			}
			return locatePropertyReference(referenceExpression);
		}

		return LookupSite.absent();
	}

	/**
	 * Locate a {@link DependencySite} from a Groovy dependency or plugin call.
	 * <p>
	 * Supports direct string notation, named-argument declarations, version
	 * blocks, and plugin declarations such as: <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * implementation('org.junit.jupiter:junit-jupiter') { version { prefer '5.11.0' } }
	 * id 'org.springframework.boot' version '3.3.2'
	 * </pre>
	 *
	 * @param call the method call to inspect.
	 * @return the dependency site, or {@literal null} if the call does not represent a
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
			return GroovyPluginDependencySite.fromMethodCall(call, propertyResolver);
		}

		if (GradleParser.isMapStyleDeclarationCandidate(call)) {
			DependencySite site = GradleParser.parseMapDeclaration(call, propertyResolver);
			if (site != null) {
				return site;
			}
		}

		return GradleParser.parseDependency(call, propertyResolver);
	}

	private LookupSite locatePropertyLiteral(GrLiteral literal) {

		Property property = GroovyExtAssignment.findGroovyExtPropertyVersionElement(literal);
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

	private LookupSite locateCommandPlatformString(PsiElement element) {

		GrMethodCall call = GradleParser.findCommandPlatformDependencyCall(element);
		String text = GradleParser.getCommandPlatformStringText(element);
		if (call == null || !StringUtils.hasText(text)) {
			return LookupSite.absent();
		}

		GradleDependency dependency = GradleDependency.parse(text, propertyResolver);
		return dependency != null ? LookupSite.from(dependency.toDependencySite(call, element)) : LookupSite.absent();
	}

	private @Nullable DependencySite resolvePropertyDeclaration(
			GrReferenceExpression refExpr) {

		if (GradleParser.isVersionNamedArgumentReference(refExpr)) {
			if (PsiTreeUtil.getParentOfType(refExpr, GrStringInjection.class) != null) {
				return null;
			}

			GrMethodCall declarationCall = GradleParser.findVersionNamedArgumentDependencyCall(refExpr);
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

		GrMethodCall enclosingCall = PsiTreeUtil.getParentOfType(refExpr, GrMethodCall.class);
		if (enclosingCall != null
				&& GradleVersionConstraint.isConstraint(GroovyDslUtils.getGroovyMethodName(enclosingCall))) {

			GrMethodCall dependencyCall = GradleParser.findVersionBlockDependencyCall(enclosingCall);
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

		GrMethodCall catalogCall = GroovyDslUtils.findEnclosingGroovyCatalogAccessorCall(element);
		if (catalogCall == null) {
			return LookupSite.absent();
		}

		PsiElement argument = GroovyDslUtils.getFirstGroovyCatalogArgumentExpression(catalogCall);
		if (!(argument instanceof GrExpression expression)) {
			return LookupSite.absent();
		}

		List<String> segments = GroovyDslUtils.getVersionCatalogSegments(expression);
		if (segments.isEmpty() || !registry.containsAlias(segments.getFirst())) {
			return LookupSite.absent();
		}

		TomlReference reference = TomlReference.from(segments, registry.catalogPaths().keySet());
		return reference != null ? LookupSite.ofTomlReference(reference, catalogCall) : LookupSite.absent();
	}

	private LookupSite locateVersionBlockLiteral(GrLiteral literal) {

		DependencySite site = resolveVersionBlockLiteral(literal, propertyResolver);
		if (site == null) {
			return LookupSite.absent();
		}

		if (site instanceof VersionedDependencySite) {
			return LookupSite.from(site);
		}

		return new LookupSite.ArtifactIdLookupSite(site.getArtifactId(), site.getVersionSource(),
				site.getDeclarationElement(), literal);
	}

	/**
	 * Resolve a version-block literal inside a Groovy dependency declaration.
	 * <p>
	 * Matches literals used in declarations such as: <pre class="code">
	 * implementation('org.junit.jupiter:junit-jupiter') {
	 *     version {
	 *         prefer '5.11.0'
	 *     }
	 * }
	 * </pre>
	 *
	 * @param literal the literal to inspect.
	 * @param propertyResolver property resolver used for dependency metadata.
	 * @return the resolved dependency site, or {@literal null} if the literal does not
	 * belong to a supported version block.
	 */
	private static @Nullable DependencySite resolveVersionBlockLiteral(GrLiteral literal,
			PropertyResolver propertyResolver) {

		if (PsiTreeUtil.getParentOfType(literal, GrStringInjection.class) != null) {
			return null;
		}

		GrMethodCall preferOrStrictlyCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		if (preferOrStrictlyCall == null) {
			return null;
		}
		String innerName = GroovyDslUtils.getGroovyMethodName(preferOrStrictlyCall);
		if (!GradleParser.isVersionBlockLiteral(literal, GradleVersionConstraint.PREFER)
				&& !GradleParser.isVersionBlockLiteral(literal, GradleVersionConstraint.STRICTLY)) {
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

		GrMethodCall depCall = GradleParser.findVersionBlockDependencyCall(preferOrStrictlyCall);
		if (depCall == null) {
			return null;
		}

		GrLiteral gavLiteral = null;
		for (PsiElement arg : depCall.getArgumentList().getAllArguments()) {
			if (arg instanceof GrLiteral gavCandidate) {
				String text = GroovyDslUtils.getText(gavCandidate);
				if (GradleArtifactId.isValid(text)) {
					gavLiteral = gavCandidate;
					break;
				}
			}
		}

		if (gavLiteral == null) {
			return null;
		}

		String gav = GroovyDslUtils.getText(gavLiteral);
		if (!GradleArtifactId.isValid(gav)) {
			return null;
		}

		String version = GroovyDslUtils.getText(literal);
		ArtifactId artifactId = GradleArtifactId.from(gav).resolve(propertyResolver);
		VersionSource versionSource = VersionSource.declared(version);

		return ArtifactVersion.from(version).map(
				it -> (DependencySite) VersionedDependencySite.of(artifactId, it, versionSource, depCall, literal))
				.orElseGet(() -> DependencySite.of(artifactId, versionSource, depCall));
	}

	/**
	 * Locate a {@link DependencySite} for a Groovy version literal.
	 * <p>
	 * Supports direct string notation, named-argument versions, version-block
	 * constraints, and plugin versions such as: <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * implementation('org.junit.jupiter:junit-jupiter') { version { prefer '5.11.0' } }
	 * id 'org.springframework.boot' version '3.3.2'
	 * </pre>
	 *
	 * @param element the literal to inspect.
	 * @param scriptProperties property resolver used for property-backed versions.
	 * @return the dependency site, or {@literal null} if the literal is not part of a
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
			call = GradleParser.findCommandPlatformDependencyCall(element);
		}
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
			if (GradleParser.isVersionNamedArgumentLiteral(element)) {
				site = GradleParser.parseMapDeclaration(call, scriptProperties);
				if (site != null) {
					return site;
				}
			}
		}

		if (!GradleParser.isDirectDependencyNotationLiteral(element)) {
			return null;
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
	 * <p>
	 * The plugin ID is used as both {@link ArtifactId#groupId()} and
	 * {@link ArtifactId#artifactId()}, matching the convention used throughout the
	 * Gradle plugin support.
	 *
	 * @param call the outer {@code version} call.
	 * @param scriptProperties property resolver used for plugin ID parsing.
	 * @return the dependency site, or {@literal null} if the call does not match the
	 * supported plugin declaration shape.
	 */
	public static @Nullable DependencySite resolvePluginVersionLiteral(GrMethodCall call,
			PropertyResolver scriptProperties) {
		// The 'x.y.z' literal is an argument of the outer `version` method call.
		// call.invokedExpression must be a `version` GrReferenceExpression whose
		// qualifier
		// is the inner `id 'pluginId'` method call.
		GrMethodCall idCall = GradleParser.findPluginIdCallForVersionCall(call);
		if (idCall == null) {
			return null;
		}

		return GroovyPluginDependencySite.fromMethodCall(idCall, scriptProperties);
	}


}
