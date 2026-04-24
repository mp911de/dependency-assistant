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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.support.VersionedDependencySite;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrString;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrStringInjection;
import org.jspecify.annotations.Nullable;

/**
 * Parser that extracts Gradle dependency declarations from Groovy DSL
 * ({@code build.gradle}) files.
 * <p>Results are accumulated into a {@link DependencyCollector} using the same
 * coordinate model as the Maven parser, making the version-resolution and UI
 * layers build-tool-agnostic.
 *
 * @author Mark Paluch
 */
class GradleParser extends GradleParserSupport {

	private final PropertyResolver global;

	GradleParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector);
		this.global = properties::get;
	}

	GradleParser(DependencyCollector collector, PropertyResolver propertyResolver) {
		super(collector);
		this.global = propertyResolver;
	}

	public static boolean isDependencyCallCandidate(GrMethodCall call) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);

		return GradleUtils.isPlatformSection(methodName) || GradleUtils.isDependencySection(methodName)
				|| GroovyDslUtils.isInsidePluginsBlock(call);
	}

	protected PropertyResolver getPropertyResolver() {
		return global;
	}

	// -------------------------------------------------------------------------
	// Groovy DSL
	// -------------------------------------------------------------------------

	/**
	 * Parses a Groovy {@code build.gradle} or {@code settings.gradle} file and
	 * accumulates all resolvable dependency and plugin declarations into
	 * {@code collector}.
	 */
	public void parseGroovyDsl(PsiFile file) {

		Map<String, PropertyValue> extProperties = GroovyDslExtParser.parseExtProperties(file);
		Map<String, PropertyValue> properties = new LinkedHashMap<>(extProperties);
		properties.putAll(GroovyDslExtParser.parseLocalVariables(file));

		PropertyResolver propertyResolver = PropertyResolver.fromMap(properties)
				.withFallback(global);

		getCollector().addProperties(properties.keySet());

		SyntaxTraverser.psiTraverser(file).filter(GrMethodCall.class)
				.forEach(call -> handleGroovyCall(call, propertyResolver));
	}

	private void handleGroovyCall(GrMethodCall call, PropertyResolver propertyResolver) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);
		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlugin = GroovyDslUtils.isInsidePluginsBlock(call);

		if (isPlugin) {
			DependencySite pluginSite = parsePlugin(call, propertyResolver);
			if (pluginSite != null) {
				register(pluginSite, DeclarationSource.plugin(), propertyResolver);
			}
			return;
		}

		if (!isDependencyConfig && !isPlatform) {
			return;
		}

		DeclarationSource declarationSource = isPlatform ? DeclarationSource.managed()
				: DeclarationSource.dependency();
		DependencySite site = null;
		if (isMapStyleDeclarationCandidate(call)) {
			site = parseMapDeclaration(call, propertyResolver);
		}

		if (site == null) {
			site = parseDependency(call, propertyResolver);
		}

		if (site != null) {
			register(site, declarationSource, propertyResolver);
		}
	}

	/**
	 * Parse a direct Groovy string-notation dependency declaration.
	 * <pre class="code">
	 * implementation 'org.junit.jupiter:junit-jupiter:5.11.0'
	 * implementation platform('org.springframework.boot:spring-boot-dependencies:3.3.2')
	 * </pre>
	 * @param call the method call to inspect.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed dependency, or {@code null} if the call does not contain a
	 * supported direct declaration.
	 */
	public static @Nullable DependencySite parseDependency(GrMethodCall call, PropertyResolver propertyResolver) {

		PsiElement[] args = call.getArgumentList().getAllArguments();
		for (PsiElement arg : args) {

			// Unwrap platform()/enforcedPlatform() wrapper
			if (arg instanceof GrMethodCall innerCall
					&& GradleUtils.PLATFORM_FUNCTIONS.contains(GroovyDslUtils.getGroovyMethodName(innerCall))) {
				PsiElement[] innerArgs = innerCall.getArgumentList().getAllArguments();
				arg = innerArgs.length > 0 ? innerArgs[0] : null;
			}

			if (arg instanceof GrLiteral literal) {

				String rawText = GroovyDslUtils.getText(literal);
				DependencySite site = parseVersionBlockDependency(call, propertyResolver);
				if (site != null) {
					return site;
				}

				GradleDependency dependency = GradleDependency.parse(rawText, propertyResolver);
				if (dependency != null) {
					return dependency.toDependencySite(call, literal);
				}
			}
		}

		return null;
	}

	/**
	 * Return whether the given call is a map-style declaration candidate.
	 * <pre class="code">
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * </pre>
	 */
	public static boolean isMapStyleDeclarationCandidate(GrMethodCall call) {
		GrNamedArgument[] namedArguments = call.getNamedArguments();
		return namedArguments.length > 1;
	}

	/**
	 * Parse Groovy dependency declarations that use version-block or map-style
	 * notation.
	 * <p>Supports declarations such as: <pre class="code">
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * implementation('org.junit.jupiter:junit-jupiter') { version { prefer '5.11.0' } }
	 * </pre>
	 *
	 * @param call the method call to parse.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed declaration, possibly incomplete.
	 */
	static @Nullable DependencySite parseMapDeclaration(GrMethodCall call, PropertyResolver propertyResolver) {
		NamedDependencyDeclaration declaration = parseMapDependency(call, call.getNamedArguments(), propertyResolver);
		return declaration.isComplete() ? declaration.toDependencySite(propertyResolver) : null;
	}

	/**
	 * Parse a Groovy version-block dependency declaration.
	 * <p><pre class="code">
	 * implementation('group:artifact') {
	 *     version {
	 *         strictly '[1.7, 1.8['
	 *         prefer '1.7.25'
	 *     }
	 * }
	 * </pre>
	 * <p>When {@code propertyResolver} is provided, bare variable references such
	 * as {@code prefer varName} are resolved through it.
	 *
	 * @param call the method call to parse.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed declaration, possibly incomplete if no usable version can
	 * be extracted.
	 */
	static @Nullable DependencySite parseVersionBlockDependency(GrMethodCall call, PropertyResolver propertyResolver) {

		GrLiteral gavLiteral = findCoordinateLiteral(call);
		GrClosableBlock depClosure = getFirstClosure(call);

		if (gavLiteral == null) {
			return null;
		}

		if (depClosure == null) {
			return null;
		}

		GrMethodCall versionCall = findNestedMethodCall(depClosure, "version");

		if (versionCall == null) {
			return null;
		}

		GrClosableBlock versionClosure = getFirstClosure(versionCall);
		if (versionClosure == null) {
			return null;
		}

		GroovyVersionValue prefer = findConstraintValue(versionClosure, GradleVersionConstraint.PREFER,
				propertyResolver);
		GroovyVersionValue strictly = findConstraintValue(versionClosure, GradleVersionConstraint.STRICTLY,
				propertyResolver);
		GroovyVersionValue versionValue = prefer.isPresent() ? prefer : strictly;

		if (!versionValue.isPresent()) {
			return null;
		}

		if (strictly.isPresent() && !prefer.isPresent() && GradleUtils.isVersionRange(strictly.version())) {
			return null;
		}

		String gavText = GroovyDslUtils.getText(gavLiteral);
		if (StringUtils.isEmpty(gavText)) {
			return null;
		}

		String[] parts = gavText.split(":");
		String group = parts[0];
		String artifact = parts[1];

		ArtifactId artifactId = GradleDependency.getArtifactId(group, artifact, propertyResolver);
		VersionSource versionSource;
		if (StringUtils.hasText(versionValue.versionProperty) || StringUtils.hasText(versionValue.version)) {
			PropertyExpression expression = StringUtils.hasText(versionValue.versionProperty)
					? PropertyExpression.property(versionValue.versionProperty)
					: PropertyExpression.from(versionValue.version);
			versionSource = expression.asVersionSource();
			if (expression.isProperty()) {
				PropertyValue propertyValue = propertyResolver.getPropertyValue(expression.getPropertyName());

				if (propertyValue != null) {
					Optional<ArtifactVersion> optionalVersion = ArtifactVersion.from(versionValue.version());
					if (optionalVersion.isPresent()) {

						return VersionedDependencySite.of(artifactId, optionalVersion.get(),
								versionSource, call,
								propertyValue.element());
					}
				}
			}
		} else {
			versionSource = StringUtils.hasText(versionValue.version) ? VersionSource.declared(versionValue.version)
					: VersionSource.none();
		}

		if (versionValue.isPresent()) {
			Optional<ArtifactVersion> optionalVersion = ArtifactVersion.from(versionValue.version());
			if (optionalVersion.isPresent()) {
				return VersionedDependencySite.of(artifactId, optionalVersion.get(), versionSource, call,
						versionValue.versionElement());
			}
		}

		return DependencySite.of(artifactId, versionSource, call);
	}

	/**
	 * Parse a Groovy map-style dependency declaration. <pre class="code">
	 * implementation group: 'org.junit.jupiter', name: 'junit-jupiter', version: '5.11.0'
	 * </pre>
	 *
	 * @param call the owning method call.
	 * @param named the named arguments to parse.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed declaration, possibly incomplete.
	 */
	static NamedDependencyDeclaration parseMapDependency(GrMethodCall call, GrNamedArgument[] named,
			PropertyResolver propertyResolver) {

		String group = null;
		String artifact = null;
		String version = null;
		String versionProperty = null;
		PsiElement versionLiteral = null;

		for (GrNamedArgument arg : named) {
			String key = arg.getLabelName();
			PsiElement val = arg.getExpression();
			String strVal = val instanceof GrLiteral lit ? GroovyDslUtils.getText(lit) : null;

			if ("group".equals(key)) {
				group = !StringUtils.isEmpty(strVal) ? propertyResolver.resolvePlaceholders(strVal) : strVal;
			} else if ("name".equals(key)) {
				artifact = !StringUtils.isEmpty(strVal) ? propertyResolver.resolvePlaceholders(strVal) : strVal;
			} else if ("version".equals(key)) {

				if (val instanceof GrReferenceExpression ref) {
					String refName = GroovyDslUtils.getRequiredText(ref);
					versionProperty = refName;
					if (StringUtils.hasText(refName)) {
						PropertyValue element = propertyResolver.getPropertyValue(refName);
						if (element != null) {
							version = element.propertyValue();
							versionLiteral = element.element();
						}
					}
				} else if (val instanceof GrString gstr) {
					GrStringInjection[] injections = gstr.getInjections();
					if (injections.length == 1) {
						GrReferenceExpression ref = PsiTreeUtil.findChildOfType(injections[0],
								GrReferenceExpression.class);
						if (ref != null) {
							String refName = ref.getReferenceName();
							versionProperty = refName;
							PropertyValue element = propertyResolver.getPropertyValue(refName);
							if (element != null) {
								version = element.propertyValue();
								versionLiteral = element.element();
							}
						}
					}
				} else if (!StringUtils.isEmpty(strVal)) {
					version = strVal;
					versionLiteral = val;
				}
			}
		}

		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact,
				versionProperty, version, call, versionLiteral);
	}

	/**
	 * Parse a Groovy plugin declaration inside a {@code plugins {}} block.
	 * <pre class="code">
	 * plugins {
	 *     id 'org.springframework.boot' version '3.3.2'
	 * }
	 * </pre>
	 *
	 * @param call the method call to inspect.
	 * @param propertyResolver property resolver used for property-backed versions.
	 * @return the parsed plugin dependency, or {@code null} if the call does not
	 * match the supported plugin declaration shape.
	 */
	private @Nullable DependencySite parsePlugin(GrMethodCall call, PropertyResolver propertyResolver) {

		PluginId id = GroovyPluginIds.fromMethodCall(call, propertyResolver);
		return id != null ? id.toDependencySite() : null;
	}

	private static @Nullable GrLiteral findCoordinateLiteral(GrMethodCall call) {

		for (PsiElement argument : call.getArgumentList().getAllArguments()) {
			if (!(argument instanceof GrLiteral literal)) {
				continue;
			}

			String text = GroovyDslUtils.getText(literal);
			if (StringUtils.hasText(text) && text.split(":").length == 2) {
				return literal;
			}
		}

		return null;
	}

	private static @Nullable GrClosableBlock getFirstClosure(GrMethodCall call) {

		for (GrClosableBlock closure : call.getClosureArguments()) {
			return closure;
		}

		return null;
	}

	private static @Nullable GrMethodCall findNestedMethodCall(GrClosableBlock closure, String methodName) {

		for (GrMethodCall methodCall : SyntaxTraverser.psiTraverser(closure).filter(GrMethodCall.class)) {
			if (methodName.equals(GroovyDslUtils.getGroovyMethodName(methodCall))) {
				return methodCall;
			}
		}

		return null;
	}

	private static GroovyVersionValue findConstraintValue(GrClosableBlock versionClosure, String constraint,
			@Nullable PropertyResolver propertyResolver) {

		for (GrMethodCall methodCall : SyntaxTraverser.psiTraverser(versionClosure).filter(GrMethodCall.class)) {
			if (!constraint.equals(GroovyDslUtils.getGroovyMethodName(methodCall))) {
				continue;
			}

			PsiElement argument = getFirstArgument(methodCall);
			if (argument instanceof GrLiteral literal) {
				String version = GroovyDslUtils.getText(literal);
				return StringUtils.hasText(version) ? new GroovyVersionValue(null, version, literal)
						: GroovyVersionValue.absent();
			}

			if (!(argument instanceof GrReferenceExpression referenceExpression) || propertyResolver == null) {
				return GroovyVersionValue.absent();
			}

			String propertyName = referenceExpression.getReferenceName();
			if (!StringUtils.hasText(propertyName)) {
				return GroovyVersionValue.absent();
			}

			PropertyValue resolved = propertyResolver.getPropertyValue(propertyName);
			if (resolved == null || !StringUtils.hasText(resolved.propertyValue())) {
				return GroovyVersionValue.absent();
			}

			return new GroovyVersionValue(propertyName, resolved.propertyValue(), resolved.element());
		}

		return GroovyVersionValue.absent();
	}

	private static @Nullable PsiElement getFirstArgument(GrMethodCall call) {

		PsiElement[] arguments = call.getArgumentList().getAllArguments();
		return arguments.length > 0 ? arguments[0] : null;
	}

	private record GroovyVersionValue(@Nullable String versionProperty, @Nullable String version,
			@Nullable PsiElement versionElement) {

		static GroovyVersionValue absent() {
			return new GroovyVersionValue(null, null, null);
		}

		boolean isPresent() {
			return StringUtils.hasText(version) && versionElement != null;
		}

	}

	// -------------------------------------------------------------------------
	// Gradle Properties
	// -------------------------------------------------------------------------
	public void parseGradleProperties(Cache cache, PsiFile file) {

		Map<String, String> properties = GradlePropertiesParser.getGradleProperties(file);
		getCollector().addProperties(properties.keySet());

		cache.doWithProperties(property -> {
			if (property.hasArtifacts()) {
				String value = properties.get(property.name());
				if (StringUtils.isEmpty(value)) {
					return;
				}

				ArtifactVersion.from(value).ifPresent(version -> {
					for (CachedArtifact artifact : property.artifacts()) {
						getCollector().registerUsage(artifact.toArtifactId(), version,
								DeclarationSource.managed(),
								VersionSource.property(property.name()));
					}
				});
			}
		});
	}

}
