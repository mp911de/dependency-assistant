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
import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.util.PsiVisitors;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Parser that extracts Gradle dependency declarations from Groovy DSL
 * ({@code build.gradle}), Kotlin DSL ({@code build.gradle.kts}), properties
 * ({@code gradle.properties}), and TOML version catalog
 * ({@code libs.versions.toml}) files.
 * <p>Results are accumulated into a {@link DependencyCollector} using the same
 * coordinate model as the Maven parser, making the version-resolution and UI
 * layers build-tool-agnostic.
 *
 * @author Mark Paluch
 */
class GradleParser extends GradleParserSupport {

	private final Map<String, String> properties;

	private final Map<String, PsiPropertyValueElement> propertyLookup = new LinkedHashMap<>();

	public GradleParser() {
		this(new DependencyCollector(), new LinkedHashMap<>());
	}

	public GradleParser(DependencyCollector collector) {
		this(collector, new LinkedHashMap<>());
	}

	public GradleParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector);
		this.properties = new LinkedHashMap<>(properties);
	}

	@Override
	protected Map<String, String> getPropertyMap() {
		return properties;
	}

	@Override
	public @Nullable PsiPropertyValueElement getElement(String propertyKey) {
		return this.propertyLookup.get(propertyKey);
	}

	// -------------------------------------------------------------------------
	// Groovy DSL
	// -------------------------------------------------------------------------

	/**
	 * Parses a Groovy {@code build.gradle} or {@code settings.gradle} file and
	 * accumulates all resolvable dependency and plugin declarations into
	 * {@code collector}.
	 */
	public void parseGroovyScript(PsiFile file) {

		Map<String, PsiPropertyValueElement> properties = GroovyDslExtParser.parseExtProperties(file);
		this.propertyLookup.clear();
		this.propertyLookup.putAll(properties);

		properties.forEach((k, v) -> this.properties.put(k, v.propertyValue()));
		getCollector().addProperties(this.properties.keySet());
		file.accept(PsiVisitors.visitTree(GrMethodCall.class, this::handleGroovyCall));
	}

	private void handleGroovyCall(GrMethodCall call) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);

		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlugin = GroovyDslUtils.isInsidePluginsBlock(call);

		if (isPlugin) {
			// id('plugin.id') version 'x.y.z' — handled as a single binary expression,
			GradleDependency dependency = parsePlugin(call);
			if (dependency != null) {
				register(dependency, DeclarationSource.plugin());
			}
			return;
		}

		if (!isDependencyConfig && !isPlatform) {
			return;
		}

		// Try string notation first: implementation('group:artifact:version')
		GradleDependency dependency = parseDependency(call);
		DeclarationSource declarationSource = isPlatform ? DeclarationSource.managed()
				: DeclarationSource.dependency();

		if (dependency == null) {
			// Try map notation: implementation(group: 'g', name: 'a', version: 'v')
			GrNamedArgument[] named = call.getNamedArguments();
			if (named.length >= 2) {
				NamedDependencyDeclaration entry = parseMapDependency(call, named, this);
				if (entry.isComplete()) {
					dependency = entry.toDependency(this);
				}
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
				String rawText = GroovyDslUtils.toString(lit);
				GradleDependency dependency = parseGav(rawText);
				if (dependency != null) {
					return dependency;
				}
			}
		}

		return null;
	}

	/**
	 * Parse map-style dependency declarations.
	 */
	public static NamedDependencyDeclaration parseMapDependency(GrMethodCall call, GrNamedArgument[] named,
			PropertyResolver propertyResolver) {

		String group = null;
		String artifact = null;
		String version = null;
		String versionProperty = null;
		PsiElement versionLiteral = null;

		for (GrNamedArgument arg : named) {
			String key = arg.getLabelName();
			PsiElement val = arg.getExpression();
			String strVal = val instanceof GrLiteral lit ? GroovyDslUtils.toString(lit) : null;

			if ("group".equals(key)) {
				group = StringUtils.isEmpty(strVal) ? strVal : propertyResolver.resolvePlaceholders(strVal);
			} else if ("name".equals(key)) {
				artifact = StringUtils.isEmpty(strVal) ? strVal : propertyResolver.resolvePlaceholders(strVal);
			} else if ("version".equals(key)) {

				if (val instanceof GrReferenceExpression ref) {
					String refName = ref.getReferenceName();
					versionProperty = refName;
					if (StringUtils.hasText(refName)) {
						PsiPropertyValueElement element = propertyResolver.getElement(refName);
						if (element != null) {
							version = element.propertyValue();
							versionLiteral = element.element();
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

