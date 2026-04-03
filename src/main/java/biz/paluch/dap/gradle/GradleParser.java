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
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * Parser that extracts Gradle dependency declarations from Groovy DSL ({@code build.gradle}), Kotlin DSL
 * ({@code build.gradle.kts}), properties ({@code gradle.properties}), and TOML version catalog
 * ({@code libs.versions.toml}) files.
 * <p>
 * Results are accumulated into a {@link DependencyCollector} using the same coordinate model as the Maven parser,
 * making the version-resolution and UI layers build-tool-agnostic.
 *
 * @author Mark Paluch
 */
class GradleParser extends GradleParserSupport {

	private final Map<String, String> properties;

	public GradleParser(DependencyCollector collector) {
		this(collector, new LinkedHashMap<>());
	}

	public GradleParser(Map<String, String> properties) {
		this(new DependencyCollector(), properties);
	}

	public GradleParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector);
		this.properties = properties;
	}

	/**
	 * Loads all properties from a {@code gradle.properties} PSI file into a map.
	 */
	public static Map<String, String> parseGradleProperties(PsiFile file) {
		Map<String, String> result = new HashMap<>();
		if (file instanceof PropertiesFile propsFile) {
			for (IProperty prop : propsFile.getProperties()) {
				String key = prop.getKey();
				String value = prop.getValue();
				if (key != null && value != null) {
					result.put(key, value);
				}
			}
		}
		return result;
	}

	// -------------------------------------------------------------------------
	// Groovy DSL
	// -------------------------------------------------------------------------

	/**
	 * Parses a Groovy {@code build.gradle} or {@code settings.gradle} file and accumulates all resolvable dependency and
	 * plugin declarations into {@code collector}.
	 */
	public void parseGroovyScript(PsiFile file) {

		properties.putAll(GradleParser.parseExtProperties(file));
		getCollector().addProperties(properties.keySet());

		file.accept(new PsiRecursiveElementVisitor() {
			@Override
			public void visitElement(PsiElement element) {
				if (element instanceof GrMethodCall call) {
					handleGroovyCall(call);
				}
				super.visitElement(element);
			}
		});
	}

	private void handleGroovyCall(GrMethodCall call) {

		String methodName = GroovyDslUtils.getGroovyMethodName(call);

		boolean isPlatform = GradleUtils.PLATFORM_FUNCTIONS.contains(methodName);
		boolean isDependencyConfig = GradleUtils.DEPENDENCY_CONFIGS.contains(methodName);
		boolean isPlugin = GroovyDslUtils.isInsideGradleBlock(call, "plugins");

		if (!isDependencyConfig && !isPlatform && !isPlugin) {
			return;
		}


		if (isPlugin) {
			// id('plugin.id') version 'x.y.z' — handled as a single binary expression,
			GradleDependency dependency = parsePlugin(call);
			if (dependency != null) {
				register(dependency, DeclarationSource.plugin());
			}
		} else {

			// Try string notation first: implementation('group:artifact:version')
			GradleDependency dependency = parseDependency(call);
			DeclarationSource declarationSource = isPlatform ? DeclarationSource.managed() : DeclarationSource.dependency();

			if (dependency == null) {
				// Try map notation: implementation(group: 'g', name: 'a', version: 'v')
				GrNamedArgument[] named = call.getNamedArguments();
				if (named.length >= 2) {
					dependency = parseMapDependency(named);
				}
			}

			if (dependency != null) {
				register(dependency, declarationSource);
			}
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

	private @Nullable GradleDependency parseMapDependency(GrNamedArgument[] named) {

		String group = null;
		String artifact = null;
		String version = null;

		for (GrNamedArgument arg : named) {
			String key = arg.getLabelName();
			PsiElement val = arg.getExpression();
			String strVal = val instanceof GrLiteral lit ? GroovyDslUtils.toString(lit) : null;
			if ("group".equals(key)) {
				group = resolveValue(strVal);
			} else if ("name".equals(key)) {
				artifact = resolveValue(strVal);
			} else if ("version".equals(key)) {
				version = strVal;
				// TODO: handle property references here
			}
		}

		if (!StringUtils.hasText(group) || !StringUtils.hasText(artifact) || !StringUtils.hasText(version)) {
			return null;
		}

		return new SimpleDependency(ArtifactId.of(group, artifact), version, VersionSource.declared(version));
	}

	private @Nullable GradleDependency parsePlugin(GrMethodCall call) {
		// Three forms appear in Gradle Groovy DSL plugins {} blocks:
		//
		// (1) Flat command args (non-chained):
		// id 'x' version 'y' GrApplicationStatement(id, ['x', version_ref, 'y'])
		//
		// (2) Chained command expression (most common):
		// id 'x' version 'y' inner GrApplicationStatement(id, ['x'])
		// whose parent is GrReferenceExpression('version')
		// whose parent is outer GrApplicationStatement(version, ['y'])
		//
		// (3) Explicit-paren + command chain:
		// id('x') version 'y' same chained structure as (2) but inner uses explicit parens

		PsiElement[] ownArgs = call.getArgumentList().getAllArguments();
		String pluginId = GroovyDslUtils.firstLiteralString(ownArgs);
		if (pluginId == null) {
			return null;
		}

		// Form (1): version keyword + version literal in the flat arg list.
		String version = findVersionAfterRef(ownArgs);

		if (version == null) {
			// Forms (2) & (3): the inner call is the qualifier of a GrReferenceExpression('version'),
			// which is the invoked-expression of the outer GrApplicationStatement(['y']).
			PsiElement parent = call.getParent();
			if (parent instanceof GrReferenceExpression versionRef && "version".equals(versionRef.getReferenceName())
					&& versionRef.getParent() instanceof GrMethodCall outerApp) {
				// TODO: handle property references here
				version = GroovyDslUtils.firstLiteralString(outerApp.getArgumentList().getAllArguments());
			}
			// Fallback: direct GrMethodCall parent whose args contain the version_ref + literal pattern.
			else if (parent instanceof GrMethodCall outerApp) {
				// TODO: handle property references here
				version = findVersionAfterRef(outerApp.getArgumentList().getAllArguments());
			}
		}

		if (!StringUtils.hasText(version)) {
			return null;
		}

		return new SimpleDependency(ArtifactId.of(pluginId, pluginId), version, VersionSource.declared(version));
	}

	/**
	 * Collects all Groovy {@code ext} property declarations from the given file.
	 * <p>
	 * Three forms are recognised:
	 *
	 * <pre>
	 * ext {
	 *     springVersion = '6.1.0'              // assignment form
	 *     set('springVersion', '6.1.0')        // set() call form
	 * }
	 * ext.springVersion = '6.1.0'             // dot-qualified assignment form
	 * </pre>
	 *
	 * @param file a Groovy {@code .gradle} file
	 * @return a map of property key to literal string value; entries whose value is not a plain string are omitted
	 */
	public static Map<String, String> parseExtProperties(PsiFile file) {

		Map<String, String> result = new HashMap<>();

		file.accept(new PsiRecursiveElementVisitor() {
			@Override
			public void visitElement(PsiElement element) {
				super.visitElement(element);
				// ext { key = 'value' } or ext { set('key', 'value') }
				if (element instanceof GrMethodCall call && "ext".equals(GroovyDslUtils.getGroovyMethodName(call))) {
					collectExtClosureProperties(call, result);
				}
				// ext.key = 'value'
				if (element instanceof GrAssignmentExpression assign && !assign.isOperatorAssignment()) {
					collectExtDotProperty(assign, result);
				}
			}
		});
		return result;
	}

	private static void collectExtClosureProperties(GrMethodCall extCall, Map<String, String> result) {

		for (GrClosableBlock closure : extCall.getClosureArguments()) {
			for (PsiElement child : closure.getChildren()) {
				// Assignment form: springVersion = '6.1.0'

				if (child instanceof GrAssignmentExpression assign && !assign.isOperatorAssignment()) {
					GrExpression lhs = assign.getLValue();
					GrExpression rhs = assign.getRValue();
					if (lhs instanceof GrReferenceExpression ref && ref.getQualifierExpression() == null) {
						String key = ref.getReferenceName();
						if (key != null && rhs instanceof GrLiteral lit && lit.getValue() instanceof String s) {
							result.put(key, s);
						}
					}
				}

				// set() call form: set('springVersion', '6.1.0')
				if (child instanceof GrMethodCall setCall && "set".equals(GroovyDslUtils.getGroovyMethodName(setCall))) {
					PsiElement[] args = setCall.getArgumentList().getAllArguments();
					if (args.length >= 2 && args[0] instanceof GrLiteral keyLit && keyLit.getValue() instanceof String key
							&& args[1] instanceof GrLiteral valLit && valLit.getValue() instanceof String val) {
						result.put(key, val);
					}
				}
			}
		}
	}

	private static void collectExtDotProperty(GrAssignmentExpression assign, Map<String, String> result) {

		GrExpression lhs = assign.getLValue();
		GrExpression rhs = assign.getRValue();

		if (!(lhs instanceof GrReferenceExpression ref)) {
			return;
		}

		GrExpression qualifier = ref.getQualifierExpression();
		if (qualifier instanceof GrReferenceExpression qualRef && "ext".equals(qualRef.getReferenceName())) {
			String key = ref.getReferenceName();
			if (key != null && rhs instanceof GrLiteral lit && lit.getValue() instanceof String s) {
				result.put(key, s);
			}
		}
	}

	/**
	 * Scans {@code args} for the pattern {@code version <literal>} and returns the literal value, or {@code null} if no
	 * such pattern is found.
	 */
	private static @Nullable String findVersionAfterRef(PsiElement[] args) {
		boolean sawVersion = false;
		for (PsiElement arg : args) {
			if (!sawVersion && arg instanceof GrReferenceExpression ref && "version".equals(ref.getText())) {
				sawVersion = true;
			} else if (sawVersion && arg instanceof GrLiteral lit && lit.getValue() instanceof String s) {
				return s;
			}
		}
		return null;
	}

	// -------------------------------------------------------------------------
	// Gradle Properties
	// -------------------------------------------------------------------------
	public void parseGradleProperties(Cache cache, PsiFile file) {

		Map<String, String> properties = parseGradleProperties(file);
		getCollector().addProperties(properties.keySet());

		cache.doWithProperties(property -> {
			if (property.hasArtifacts()) {

				String value = properties.get(property.name());

				if (!StringUtils.hasText(value)) {
					return;
				}

				ArtifactVersion.from(value).ifPresent(version -> {
					for (CachedArtifact artifact : property.artifacts()) {
						getCollector().registerUpdateCandidate(artifact.toArtifactId(), version, DeclarationSource.managed(),
								VersionSource.property(property.name()));
					}
				});
			}
		});
	}

	// -------------------------------------------------------------------------
	// Shared helpers
	// -------------------------------------------------------------------------

	@Nullable
	String resolveValue(@Nullable String value) {
		return resolveValue(value, properties);
	}

	@Override
	@Nullable
	String getProperty(@Nullable String value) {
		return properties.get(value);
	}

}
