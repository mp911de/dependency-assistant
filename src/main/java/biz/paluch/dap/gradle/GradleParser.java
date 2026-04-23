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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GroovyDslUtils.PluginId;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.PsiVisitors;
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

	private final Map<String, String> properties;

	private final Map<String, PropertyValue> propertyLookup = new LinkedHashMap<>();

	GradleParser() {
		this(new DependencyCollector(), new LinkedHashMap<>());
	}

	GradleParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector);
		this.properties = new LinkedHashMap<>(properties);
	}

	@Override
	protected Map<String, String> getPropertyMap() {
		return properties;
	}

	@Override
	public @Nullable PropertyValue getElement(String propertyKey) {
		return this.propertyLookup.get(propertyKey);
	}

	protected Map<String, PropertyValue> getPropertyLookup() {
		return this.propertyLookup;
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
		Map<String, PropertyValue> localVars = GroovyDslExtParser.parseLocalVariables(file);
		this.propertyLookup.clear();
		this.propertyLookup.putAll(localVars);
		this.propertyLookup.putAll(extProperties);

		this.propertyLookup.forEach((k, v) -> this.properties.put(k, v.propertyValue()));
		getCollector().addProperties(this.properties.keySet());
		file.accept(PsiVisitors.visitTree(GrMethodCall.class, this::handleGroovyCall));
	}

	private void handleGroovyCall(GrMethodCall call) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);

		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlugin = GroovyDslUtils.isInsidePluginsBlock(call);

		if (isPlugin) {
			GradleDependency dependency = parsePlugin(call);
			if (dependency != null) {
				register(dependency, DeclarationSource.plugin());
			}
			return;
		}

		if (!isDependencyConfig && !isPlatform) {
			return;
		}

		GradleDependency dependency = parseDependency(call);
		DeclarationSource declarationSource = isPlatform ? DeclarationSource.managed()
				: DeclarationSource.dependency();

		if (dependency == null || dependency instanceof GradleDependency.DependencyReference) {
			NamedDependencyDeclaration declaration = parseNamedDependencyDeclaration(call, this);
			if (declaration.isComplete()) {
				dependency = declaration.toDependency(this);
			}
		}

		if (dependency != null) {
			register(dependency, declarationSource);
		}
	}

	private @Nullable GradleDependency parseDependency(GrMethodCall call) {

		PsiElement[] args = call.getArgumentList().getAllArguments();
		for (PsiElement arg : args) {

			// Unwrap platform()/enforcedPlatform() wrapper
			if (arg instanceof GrMethodCall innerCall
					&& GradleUtils.PLATFORM_FUNCTIONS.contains(GroovyDslUtils.getGroovyMethodName(innerCall))) {
				PsiElement[] innerArgs = innerCall.getArgumentList().getAllArguments();
				arg = innerArgs.length > 0 ? innerArgs[0] : null;
			}

			if (arg instanceof GrLiteral lit) {
				String rawText = GroovyDslUtils.renderText(lit);
				GradleDependency dependency = parseGav(rawText);
				if (dependency != null) {
					return dependency;
				}
			}
		}

		return null;
	}

	/**
	 * Parses Groovy dependency declarations that use version-block or map-style
	 * notation.
	 */
	static NamedDependencyDeclaration parseNamedDependencyDeclaration(GrMethodCall call,
			@Nullable PropertyResolver propertyResolver) {

		NamedDependencyDeclaration declaration = parseVersionBlockDependency(call, propertyResolver);
		if (declaration.isComplete()) {
			return declaration;
		}

		GrNamedArgument[] namedArguments = call.getNamedArguments();
		if (namedArguments.length < 2) {
			return new NamedDependencyDeclaration(call.getContainingFile());
		}

		return parseMapDependency(call, namedArguments, propertyResolver);
	}

	/**
	 * Parses a version-block dependency of the form: <pre class="code">
	 * implementation('group:artifact') {
	 *     version {
	 *         strictly '[1.7, 1.8['
	 *         prefer '1.7.25'
	 *     }
	 * }
	 * </pre>
	 * <p>When {@code propertyResolver} is provided, bare variable references such
	 * as {@code prefer varName} are resolved through it.
	 * <p>Returns a {@link NamedDependencyDeclaration} when a usable version can be
	 * extracted, or {@code null} otherwise.
	 */
	static NamedDependencyDeclaration parseVersionBlockDependency(GrMethodCall call,
			@Nullable PropertyResolver propertyResolver) {

		GrLiteral gavLiteral = findCoordinateLiteral(call);
		GrClosableBlock depClosure = getFirstClosure(call);

		if (gavLiteral == null) {
			return new NamedDependencyDeclaration(call.getContainingFile());
		}

		if (depClosure == null) {
			return new NamedDependencyDeclaration(call.getContainingFile());
		}

		GrMethodCall versionCall = findNestedMethodCall(depClosure, "version");

		if (versionCall == null) {
			return new NamedDependencyDeclaration(call.getContainingFile());
		}

		GrClosableBlock versionClosure = getFirstClosure(versionCall);

		if (versionClosure == null) {
			return new NamedDependencyDeclaration(call.getContainingFile());
		}

		GroovyVersionValue prefer = findConstraintValue(versionClosure, GradleVersionConstraint.PREFER,
				propertyResolver);
		GroovyVersionValue strictly = findConstraintValue(versionClosure, GradleVersionConstraint.STRICTLY,
				propertyResolver);
		GroovyVersionValue versionValue = prefer.isPresent() ? prefer : strictly;

		if (!versionValue.isPresent()) {
			return new NamedDependencyDeclaration(call.getContainingFile());
		}

		if (strictly.isPresent() && !prefer.isPresent() && GradleUtils.isVersionRange(strictly.version())) {
			return new NamedDependencyDeclaration(call.getContainingFile());
		}

		String gavText = GroovyDslUtils.renderText(gavLiteral);
		if (StringUtils.isEmpty(gavText)) {
			return new NamedDependencyDeclaration(call.getContainingFile());
		}
		String[] parts = gavText.split(":");
		String group = parts[0];
		String artifact = parts[1];
		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact,
				versionValue.versionProperty(), versionValue.version(), call, versionValue.versionElement());
	}

	/**
	 * Parse map-style dependency declarations.
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
			String strVal = val instanceof GrLiteral lit ? GroovyDslUtils.renderText(lit) : null;

			if ("group".equals(key)) {
				group = !StringUtils.isEmpty(strVal) ? propertyResolver.resolvePlaceholders(strVal) : strVal;
			} else if ("name".equals(key)) {
				artifact = !StringUtils.isEmpty(strVal) ? propertyResolver.resolvePlaceholders(strVal) : strVal;
			} else if ("version".equals(key)) {

				if (val instanceof GrReferenceExpression ref) {
					String refName = GroovyDslUtils.getRequiredText(ref);
					versionProperty = refName;
					if (StringUtils.hasText(refName)) {
						PropertyValue element = propertyResolver.getElement(refName);
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
							PropertyValue element = propertyResolver.getElement(refName);
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

	private @Nullable GradleDependency parsePlugin(GrMethodCall call) {
		PluginId id = PluginId.fromMethodCall(call, this);
		if (id == null) {
			return null;
		}

		ArtifactId artifactId = id.toValidatedArtifactId();
		if (artifactId == null) {
			return null;
		}

		String rawVersion = id.getVersionAsString();
		return GradleDependency.of(artifactId, PropertyExpression.from(rawVersion));
	}

	private static @Nullable GrLiteral findCoordinateLiteral(GrMethodCall call) {

		for (PsiElement argument : call.getArgumentList().getAllArguments()) {
			if (!(argument instanceof GrLiteral literal)) {
				continue;
			}

			String text = GroovyDslUtils.renderText(literal);
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
				String version = GroovyDslUtils.renderText(literal);
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

			PropertyValue resolved = propertyResolver.getElement(propertyName);
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
