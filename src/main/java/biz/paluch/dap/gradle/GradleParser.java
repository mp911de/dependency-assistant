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
import java.util.List;
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

	GradleParser() {
		this(new DependencyCollector(), new LinkedHashMap<>());
	}

	GradleParser(DependencyCollector collector) {
		this(collector, new LinkedHashMap<>());
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
	public @Nullable PsiPropertyValueElement getElement(String propertyKey) {
		return this.propertyLookup.get(propertyKey);
	}

	protected Map<String, PsiPropertyValueElement> getPropertyLookup() {
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

		Map<String, PsiPropertyValueElement> extProperties = GroovyDslExtParser.parseExtProperties(file);
		Map<String, PsiPropertyValueElement> localVars = GroovyDslExtParser.parseLocalVariables(file);
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
			// Try version block notation: implementation('g:a') { version { prefer '1.0' }
			// }
			NamedDependencyDeclaration versionBlockEntry = parseVersionBlockDependency(call);
			if (versionBlockEntry != null && versionBlockEntry.isComplete()) {
				dependency = versionBlockEntry.toDependency(this);
			}
		}

		if (dependency == null) {
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
	 * Parses a version-block dependency of the form: <pre class="code">
	 * implementation('group:artifact') {
	 *     version {
	 *         strictly '[1.7, 1.8['
	 *         prefer '1.7.25'
	 *     }
	 * }
	 * </pre> Returns a {@link NamedDependencyDeclaration} when a usable version can
	 * be extracted, or {@code null} otherwise.
	 */
	private @Nullable NamedDependencyDeclaration parseVersionBlockDependency(GrMethodCall call) {
		return parseVersionBlockDependency(call, null);
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
	@Nullable
	static NamedDependencyDeclaration parseVersionBlockDependency(GrMethodCall call,
			@Nullable PropertyResolver propertyResolver) {

		GrLiteral gavLiteral = null;
		GrClosableBlock depClosure = null;

		for (PsiElement arg : call.getArgumentList().getAllArguments()) {
			if (arg instanceof GrLiteral lit) {
				String text = GroovyDslUtils.renderText(lit);
				if (text != null && text.split(":").length == 2) {
					gavLiteral = lit;
				}
			}
		}

		if (gavLiteral == null) {
			return null;
		}

		for (GrClosableBlock closure : call.getClosureArguments()) {
			depClosure = closure;
			break;
		}


		if (depClosure == null) {
			return null;
		}

		GrMethodCall versionCall = null;
		List<GrMethodCall> depCallChildren = SyntaxTraverser.psiTraverser(depClosure)
				.filter(GrMethodCall.class).toList();
		for (GrMethodCall mc : depCallChildren) {
			if ("version".equals(GroovyDslUtils.getGroovyMethodName(mc))) {
				versionCall = mc;
				break;
			}
		}

		if (versionCall == null) {
			return null;
		}

		GrClosableBlock versionClosure = null;
		for (GrClosableBlock closure : versionCall.getClosureArguments()) {
			versionClosure = closure;
			break;
		}


		if (versionClosure == null) {
			return null;
		}

		GrLiteral preferLiteral = null;
		GrLiteral strictlyLiteral = null;
		PsiElement preferRef = null;
		PsiElement strictlyRef = null;
		String preferVarName = null;
		String strictlyVarName = null;

		for (GrMethodCall mc : SyntaxTraverser.psiTraverser(versionClosure).filter(GrMethodCall.class)) {
			String name = GroovyDslUtils.getGroovyMethodName(mc);
			GrLiteral firstArg = PsiTreeUtil.getChildOfType(mc.getArgumentList(), GrLiteral.class);

			if (GradleVersionConstraint.PREFER.equals(name) && preferLiteral == null) {
				if (firstArg != null) {
					preferLiteral = firstArg;
				} else if (propertyResolver != null) {
					GrReferenceExpression refArg = PsiTreeUtil.getChildOfType(mc.getArgumentList(),
							GrReferenceExpression.class);
					if (refArg != null) {
						preferVarName = refArg.getReferenceName();
						preferRef = refArg;
					}
				}
			} else if (GradleVersionConstraint.STRICTLY.equals(name) && strictlyLiteral == null) {
				if (firstArg != null) {
					strictlyLiteral = firstArg;
				} else if (propertyResolver != null) {
					GrReferenceExpression refArg = PsiTreeUtil.getChildOfType(mc.getArgumentList(),
							GrReferenceExpression.class);
					if (refArg != null) {
						strictlyVarName = refArg.getReferenceName();
						strictlyRef = refArg;
					}
				}
			}
		}

		String version;
		PsiElement versionElement;

		if (preferLiteral != null) {
			version = GroovyDslUtils.renderText(preferLiteral);
			versionElement = preferLiteral;
		} else if (preferVarName != null && propertyResolver != null) {
			PsiPropertyValueElement resolved = propertyResolver.getElement(preferVarName);
			if (resolved == null) {
				return null;
			}
			version = resolved.propertyValue();
			versionElement = resolved.element();
		} else if (strictlyLiteral != null) {
			String strictlyText = GroovyDslUtils.renderText(strictlyLiteral);
			if (GradleUtils.isVersionRange(strictlyText)) {
				return null;
			}
			version = strictlyText;
			versionElement = strictlyLiteral;
		} else if (strictlyVarName != null && propertyResolver != null) {
			PsiPropertyValueElement resolved = propertyResolver.getElement(strictlyVarName);
			if (resolved == null) {
				return null;
			}
			version = resolved.propertyValue();
			versionElement = resolved.element();
		} else {
			return null;
		}

		if (StringUtils.isEmpty(version)) {
			return null;
		}

		String gavText = GroovyDslUtils.renderText(gavLiteral);
		if (gavText == null) {
			return null;
		}
		String[] parts = gavText.split(":");
		String group = parts[0];
		String artifact = parts[1];

		String varName = preferVarName != null ? preferVarName : strictlyVarName;
		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact, varName, version, call,
				versionElement);
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
						PsiPropertyValueElement element = propertyResolver.getElement(refName);
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
							PsiPropertyValueElement element = propertyResolver.getElement(refName);
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
