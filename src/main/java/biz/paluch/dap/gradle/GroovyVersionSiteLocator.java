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
import biz.paluch.dap.artifact.DeclarationSource;
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
 * Groovy DSL PSI locator for {@link GradleVersionSite version sites} and
 * declaration-aware dependency lookups.
 *
 * @author Mark Paluch
 */
class GroovyVersionSiteLocator implements VersionSiteLocator<GroovyPsiElement> {

	private final PropertyResolver propertyResolver;

	private final @Nullable VersionCatalogRegistry registry;

	GroovyVersionSiteLocator(PropertyResolver propertyResolver) {
		this(propertyResolver, null);
	}

	GroovyVersionSiteLocator(PropertyResolver propertyResolver, @Nullable VersionCatalogRegistry registry) {
		this.propertyResolver = propertyResolver;
		this.registry = registry;
	}

	/**
	 * Locate the {@link GradleVersionSite} owning the given Groovy PSI element.
	 * <p>Supports direct dependency literals, property-backed declarations, and
	 * version catalog references such as: <pre class="code">
	 * implementation 'org.springframework:spring-core:6.2.0'
	 * implementation "org.springframework:spring-core:$springVersion"
	 * ext.springVersion = '6.2.0'
	 * implementation libs.spring.core
	 * </pre>
	 *
	 * @param element the PSI element to inspect.
	 * @return the resolved version site, or {@link GradleVersionSite#absent()} if
	 * no supported declaration can be derived.
	 */
	@Override
	public GradleVersionSite locate(GroovyPsiElement element) {

		GradleVersionSite catalogReference = locateCatalogReference(element);
		if (catalogReference.isPresent()) {
			return catalogReference;
		}

		if (element instanceof GrLiteral literal) {

			GradleVersionSite versionBlockSite = locateVersionBlockLiteral(literal);
			if (versionBlockSite.isPresent()) {
				return versionBlockSite;
			}

			GradleVersionSite propertySite = locatePropertyLiteral(literal);
			if (propertySite.isPresent()) {
				return propertySite;
			}

			return classifyLiteralSite(findDependencySite(literal, propertyResolver), literal);
		}

		if (element instanceof GrReferenceExpression referenceExpression) {
			GradleVersionSite commandPlatformSite = locateCommandPlatformString(referenceExpression);
			if (commandPlatformSite.isPresent()) {
				return commandPlatformSite;
			}
			return locatePropertyReference(referenceExpression);
		}

		return GradleVersionSite.absent();
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
	 * @return the dependency site, or {@literal null} if the call does not
	 * represent a supported declaration.
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
			DependencySite site = GradleParser.parseMapDeclaration(call, DeclarationSource.dependency(),
					propertyResolver);
			if (site != null) {
				return site;
			}
		}

		return GradleParser.parseDependency(call, DeclarationSource.dependency(), propertyResolver);
	}

	private GradleVersionSite locatePropertyLiteral(GrLiteral literal) {

		Property property = GroovyExtAssignment.findGroovyExtPropertyVersionElement(literal);
		if (property == null && propertyResolver instanceof GradlePropertyResolver resolver) {
			property = resolver.findBindingForValueLiteral(literal);
		}
		if (property == null) {
			return GradleVersionSite.absent();
		}

		return new BackingProperty(property.getKey(), property.getValue(), literal, property.getValueLiteral());
	}

	private GradleVersionSite locatePropertyReference(GrReferenceExpression refExpr) {

		String refName = refExpr.getReferenceName();
		if (!StringUtils.hasText(refName)) {
			return GradleVersionSite.absent();
		}

		PropertyValue resolved = propertyResolver.getPropertyValue(refName);
		if (resolved == null) {
			return GradleVersionSite.absent();
		}

		DependencySite site = resolvePropertyDeclaration(refExpr);
		if (site == null) {
			return GradleVersionSite.absent();
		}

		return classifyPropertyReferenceSite(site, refExpr);
	}

	private GradleVersionSite locateCommandPlatformString(PsiElement element) {

		GrMethodCall call = GradleParser.findCommandPlatformDependencyCall(element);
		String text = GradleParser.getCommandPlatformStringText(element);
		if (call == null || !StringUtils.hasText(text)) {
			return GradleVersionSite.absent();
		}

		GradleDependency dependency = GradleDependency.parse(text, DeclarationSource.managed(), propertyResolver);
		if (dependency == null) {
			return GradleVersionSite.absent();
		}

		PsiElement stringElement = GradleParser.findCommandPlatformString(element);
		PsiElement versionElement = stringElement != null ? stringElement : element;
		return new DirectCoordinate(dependency.toDependencySite(call, versionElement).getArtifactId(),
				dependency.getVersionSource(), dependency.getDeclarationSource(), call, versionElement);
	}

	private @Nullable DependencySite resolvePropertyDeclaration(GrReferenceExpression refExpr) {

		if (GradleParser.isVersionNamedArgumentReference(refExpr)) {
			if (PsiTreeUtil.getParentOfType(refExpr, GrStringInjection.class) != null) {
				return null;
			}

			GrMethodCall declarationCall = GradleParser.findVersionNamedArgumentDependencyCall(refExpr);
			if (declarationCall != null) {

				if (GradleParser.isMapStyleDeclarationCandidate(declarationCall)) {
					DependencySite site = GradleParser.parseMapDeclaration(declarationCall,
							DeclarationSource.dependency(),
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
				DependencySite site = GradleParser.parseMapDeclaration(dependencyCall, DeclarationSource.dependency(),
						propertyResolver);

				if (site != null) {
					return site;
				}

				return GradleParser.parseVersionBlockDependency(dependencyCall, propertyResolver);
			}
		}

		return null;
	}

	private GradleVersionSite locateCatalogReference(PsiElement element) {

		if (registry == null) {
			return GradleVersionSite.absent();
		}

		GrMethodCall catalogCall = GroovyDslUtils.findEnclosingGroovyCatalogAccessorCall(element);
		if (catalogCall == null) {
			return GradleVersionSite.absent();
		}

		PsiElement argument = GroovyDslUtils.getFirstGroovyCatalogArgumentExpression(catalogCall);
		if (!(argument instanceof GrExpression expression)) {
			return GradleVersionSite.absent();
		}

		List<String> segments = GroovyDslUtils.getVersionCatalogSegments(expression);
		if (segments.isEmpty() || !registry.containsAlias(segments.getFirst())) {
			return GradleVersionSite.absent();
		}

		TomlReference reference = TomlReference.from(segments, registry.catalogPaths().keySet());
		return reference != null ? new TomlCatalogAlias(reference, catalogCall) : GradleVersionSite.absent();
	}

	private GradleVersionSite locateVersionBlockLiteral(GrLiteral literal) {

		GrMethodCall enclosingCall = PsiTreeUtil.getParentOfType(literal, GrMethodCall.class);
		String constraintName = enclosingCall != null ? GroovyDslUtils.getGroovyMethodName(enclosingCall) : null;

		DependencySite site = resolveVersionBlockLiteral(literal, propertyResolver);
		if (site == null) {
			return GradleVersionSite.absent();
		}

		ArtifactId id = site.getArtifactId();
		VersionSource source = site.getVersionSource();
		PsiElement declaration = site.getDeclarationElement();
		ArtifactVersion version = GradleVersionSite.versionOf(site);
		boolean strictly = GradleVersionConstraint.STRICTLY.equals(constraintName);

		return strictly
				? new VersionBlockStrictlyLiteral(id, source, site.getDeclarationSource(), declaration, literal,
						version)
				: new VersionBlockPreferLiteral(id, source, site.getDeclarationSource(), declaration, literal, version);
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
	 * @param propertyResolver property resolver used for dependency metadata.
	 * @return the resolved dependency site, or {@literal null} if the literal does
	 * not belong to a supported version block.
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
				it -> (DependencySite) VersionedDependencySite.of(artifactId, it, versionSource,
						DeclarationSource.dependency(), depCall, literal))
				.orElseGet(() -> DependencySite.of(artifactId, versionSource, DeclarationSource.dependency(), depCall));
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
	 * @return the dependency site, or {@literal null} if the literal is not part of
	 * a supported declaration.
	 */
	public static @Nullable DependencySite findDependencySite(GrLiteral element,
			PropertyResolver scriptProperties) {

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
			return resolvePluginVersionLiteral(call, scriptProperties);
		}

		String text = GroovyDslUtils.getText(element);
		if (StringUtils.isEmpty(text)) {
			return null;
		}

		DeclarationSource declarationSource;

		if (GroovyDslUtils.isInsidePlatformBlock(call)) {
			declarationSource = DeclarationSource.managed();
		} else {
			declarationSource = DeclarationSource.dependency();
		}

		if (GradleParser.isMapStyleDeclarationCandidate(call)) {
			if (GradleParser.isVersionNamedArgumentLiteral(element)) {
				site = GradleParser.parseMapDeclaration(call, declarationSource, scriptProperties);
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
		GradleDependency dependency = GradleDependency.parse(text, declarationSource);
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
	 *
	 * @param call the outer {@code version} call.
	 * @param scriptProperties property resolver used for plugin ID parsing.
	 * @return the dependency site, or {@literal null} if the call does not match
	 * the supported plugin declaration shape.
	 */
	public static @Nullable DependencySite resolvePluginVersionLiteral(GrMethodCall call,
			PropertyResolver scriptProperties) {

		GrMethodCall idCall = GradleParser.findPluginIdCallForVersionCall(call);
		if (idCall == null) {
			return null;
		}

		return GroovyPluginDependencySite.fromMethodCall(idCall, scriptProperties);
	}

	private GradleVersionSite classifyLiteralSite(@Nullable DependencySite site, GrLiteral literal) {

		if (site == null) {
			return GradleVersionSite.absent();
		}

		ArtifactId id = site.getArtifactId();
		VersionSource source = site.getVersionSource();
		PsiElement declaration = site.getDeclarationElement();
		ArtifactVersion version = GradleVersionSite.versionOf(site);

		if (isPluginContext(literal)) {
			return new PluginVersion(id, source, declaration, literal, version);
		}

		if (isMapStyleVersionContext(literal)) {
			return new MapLiteralVersion(id, source, site.getDeclarationSource(), declaration, literal, version);
		}

		return new DirectCoordinate(id, source, site.getDeclarationSource(), declaration, literal, version);
	}

	private GradleVersionSite classifyPropertyReferenceSite(DependencySite site, GrReferenceExpression refExpr) {

		ArtifactId id = site.getArtifactId();
		VersionSource source = site.getVersionSource();
		PsiElement declaration = site.getDeclarationElement();
		ArtifactVersion version = GradleVersionSite.versionOf(site);
		String propertyName = refExpr.getReferenceName() != null ? refExpr.getReferenceName() : "";

		GrMethodCall enclosingCall = PsiTreeUtil.getParentOfType(refExpr, GrMethodCall.class);
		String enclosingName = enclosingCall != null ? GroovyDslUtils.getGroovyMethodName(enclosingCall) : null;

		if (GradleVersionConstraint.PREFER.equals(enclosingName)) {
			return new VersionBlockPreferProperty(id, propertyName, source, site.getDeclarationSource(), declaration,
					refExpr, version);
		}

		if (GradleVersionConstraint.STRICTLY.equals(enclosingName)) {
			return new VersionBlockStrictlyProperty(id, propertyName, source, site.getDeclarationSource(), declaration,
					refExpr, version);
		}

		return new MapPropertyVersion(id, propertyName, source, site.getDeclarationSource(), declaration, refExpr,
				version);
	}

	private static boolean isPluginContext(GrLiteral literal) {
		return GradleParser.isPluginVersionLiteral(literal);
	}

	private static boolean isMapStyleVersionContext(GrLiteral literal) {
		return GradleParser.isVersionNamedArgumentLiteral(literal);
	}

}
