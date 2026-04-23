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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.support.DependencySite;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PropertyValue;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

/**
 * Parser for Kotlin DSL Gradle build files ({@code build.gradle.kts} and
 * {@code settings.gradle.kts}).
 * <p>Extracted declarations are accumulated into a {@link DependencyCollector}
 * using the common Gradle dependency model so that version lookup and update
 * code can operate on Kotlin DSL semantics instead of raw PSI traversal.
 *
 * @author Mark Paluch
 */
class KotlinDslParser extends GradleParser {

	KotlinDslParser() {
		super(new DependencyCollector(), new LinkedHashMap<>());
	}

	KotlinDslParser(DependencyCollector collector) {
		super(collector, new LinkedHashMap<>());
	}

	KotlinDslParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector, properties);
	}

	// -------------------------------------------------------------------------
	// Kotlin DSL
	// -------------------------------------------------------------------------

	/**
	 * Parses a Kotlin {@code build.gradle.kts} or {@code settings.gradle.kts} file.
	 */
	public void parseKotlinScript(PsiFile file) {

		getPropertyMap().putAll(KotlinDslExtraParser.getExtraProperties(file));
		Map<String, PropertyValue> valProperties = parseValProperties(file);
		valProperties.forEach((k, v) -> {
			getPropertyMap().putIfAbsent(k, v.propertyValue());
			getPropertyLookup().putIfAbsent(k, v);
		});
		getCollector().addProperties(getPropertyMap().keySet());

		SyntaxTraverser.psiTraverser(file).filter(KtCallElement.class).forEach(this::handleKotlinCall);
	}

	/**
	 * Parses top-level {@code val} declarations from a Kotlin DSL file and returns
	 * them as {@link PropertyValue} instances. Handles:
	 * <ul>
	 * <li>{@code val key = "value"} — plain string literal initialiser</li>
	 * <li>{@code val key: T by project} — value from injected Gradle
	 * properties</li>
	 * <li>{@code val key: T by extra} — value from a preceding {@code extra["key"]}
	 * assignment</li>
	 * <li>{@code val key by extra("value")} — value from the {@code extra} delegate
	 * argument</li>
	 * </ul>
	 */
	Map<String, PropertyValue> parseValProperties(PsiFile file) {

		Map<String, PropertyValue> result = new LinkedHashMap<>();
		SyntaxTraverser.psiTraverser(file)
				.filter(KtProperty.class)
				.forEach(property -> {
					String name = property.getName();
					if (StringUtils.isEmpty(name)) {
						return;
					}

					if (property.getInitializer() instanceof KtStringTemplateExpression template) {
						String value = KotlinDslUtils.getText(template);
						if (value != null) {
							result.put(name, new PropertyValue(template, name, value));
						}
						return;
					}

					if (!property.hasDelegateExpression()) {
						return;
					}

					KtExpression delegateExpr = property.getDelegateExpression();
					if (delegateExpr instanceof KtNameReferenceExpression delegateRef) {
						String delegateName = delegateRef.getReferencedName();

						if ("project".equals(delegateName)) {
							String value = getPropertyMap().get(name);
							if (value != null) {
								result.put(name, new PropertyValue(property, name, value));
							}
						} else if ("extra".equals(delegateName)) {
							String value = getPropertyMap().get(name);
							if (value != null) {
								PropertyValue extraLocation = KotlinDslExtraParser
										.findExtraPropertyLocation(file, name);
								PsiElement psiElement = extraLocation != null ? extraLocation.element() : property;
								result.put(name, new PropertyValue(psiElement, name, value));
							}
						}
					} else if (delegateExpr instanceof KtCallExpression delegateCall
							&& "extra".equals(KotlinDslUtils.getKotlinCallName(delegateCall))) {
						for (ValueArgument va : delegateCall.getValueArguments()) {
							if (va.getArgumentExpression() instanceof KtStringTemplateExpression argTemplate) {
								String value = KotlinDslUtils.getText(argTemplate);
								if (value != null) {
									result.put(name, new PropertyValue(argTemplate, name, value));
								}
								break;
							}
						}
					}
				});
		return result;
	}

	private void handleKotlinCall(KtCallElement call) {

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (methodName == null) {
			return;
		}

		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isPlugin = KotlinDslUtils.isInsidePluginsBlock(call);

		if (!isDependencyConfig && !isPlatform && !isPlugin) {
			return;
		}

		DeclarationSource declarationSource = isPlugin ? DeclarationSource.plugin()
				: isPlatform ? DeclarationSource.managed() : DeclarationSource.dependency();
		DependencySite site = parseDependencySite(call, isPlugin, isPlatform, this);
		if (site != null) {
			register(site, declarationSource);
		}
	}

	public static @Nullable DependencySite parseDependencySite(KtCallElement call, PropertyResolver propertyResolver) {

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (methodName == null) {
			return null;
		}

		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isPlugin = KotlinDslUtils.isInsidePluginsBlock(call);

		if (!isDependencyConfig && !isPlatform && !isPlugin) {
			return null;
		}
		return parseDependencySite(call, isPlugin, isPlatform, propertyResolver);
	}

	private static @Nullable DependencySite parseDependencySite(KtCallElement call, boolean isPlugin,
			boolean isPlatform, PropertyResolver propertyResolver) {


		if (isMapStyleDeclarationCandidate(call)) {

			// Named arguments: group = "g", name = "a", version = "v"
			NamedDependencyDeclaration entry = parseMapDeclaration(call, propertyResolver);
			if (entry.isComplete()) {
				return entry.toDependencySite(propertyResolver);
			}
		}

		DependencySite site = findDependencySite(call, propertyResolver);

		if (isPlugin) {
			return site;
		}

		if (!KotlinDslUtils.isInsideBlock(call, "dependencies"::equals) && !isPlatform) {
			return null;
		}

		if (site != null) {
			return site;
		}

		// Try version block: implementation("g:a") { version { prefer("1.0") } }
		NamedDependencyDeclaration versionBlockEntry = parseVersionBlockDependency(call, propertyResolver);
		if (versionBlockEntry != null && versionBlockEntry.isComplete()) {
			return versionBlockEntry.toDependencySite(propertyResolver);
		}

		return null;
	}

	/**
	 * Parses a version-block dependency of the form: <pre class="code">
	 * implementation("group:artifact") {
	 *     version {
	 *         strictly("[1.7, 1.8[")
	 *         prefer("1.7.25")
	 *     }
	 * }
	 * </pre>
	 * <p>When {@code propertyResolver} is provided, bare name references such as
	 * {@code prefer(junit)} are resolved through it.
	 * <p>Returns a {@link NamedDependencyDeclaration} when a usable version can be
	 * extracted, or {@code null} otherwise.
	 */
	@Nullable
	static NamedDependencyDeclaration parseVersionBlockDependency(KtCallElement call,
			PropertyResolver propertyResolver) {

		KtStringTemplateExpression gavTemplate = null;
		KtLambdaExpression trailingLambda = null;

		for (ValueArgument arg : call.getValueArguments()) {
			if (arg instanceof KtLambdaArgument lambdaArgument
					&& lambdaArgument.getArgumentExpression() instanceof KtLambdaExpression lambda) {
				trailingLambda = lambda;
				continue;
			}
			if (gavTemplate == null && arg.getArgumentExpression() instanceof KtStringTemplateExpression expr) {
				String text = resolveKotlinStringTemplate(expr, propertyResolver);
				if (text != null && text.split(":").length == 2) {
					gavTemplate = expr;
				}
			}
		}

		if (gavTemplate == null || trailingLambda == null) {
			return null;
		}

		KtCallExpression versionCall = null;
		if (trailingLambda.getBodyExpression() != null) {
			for (KtCallExpression inner : PsiTreeUtil.collectElementsOfType(trailingLambda.getBodyExpression(),
					KtCallExpression.class)) {
				if ("version".equals(KotlinDslUtils.getKotlinCallName(inner))) {
					versionCall = inner;
					break;
				}
			}
		}

		if (versionCall == null) {
			return null;
		}

		KtLambdaExpression versionLambda = null;
		for (ValueArgument arg : versionCall.getValueArguments()) {
			if (arg instanceof KtLambdaArgument la && la.getArgumentExpression() instanceof KtLambdaExpression lam) {
				versionLambda = lam;
				break;
			}
		}

		if (versionLambda == null) {
			return null;
		}

		KtStringTemplateExpression preferTemplate = null;
		KtStringTemplateExpression strictlyTemplate = null;
		KtNameReferenceExpression preferNameRef = null;
		KtNameReferenceExpression strictlyNameRef = null;

		if (versionLambda.getBodyExpression() != null) {
			for (KtCallExpression inner : PsiTreeUtil.collectElementsOfType(versionLambda.getBodyExpression(),
					KtCallExpression.class)) {
				String name = KotlinDslUtils.getKotlinCallName(inner);
				for (ValueArgument va : inner.getValueArguments()) {
					KtExpression argExpr = va.getArgumentExpression();
					if (argExpr instanceof KtStringTemplateExpression st) {
						if (GradleVersionConstraint.PREFER.equals(name) && preferTemplate == null) {
							preferTemplate = st;
						} else if (GradleVersionConstraint.STRICTLY.equals(name) && strictlyTemplate == null) {
							strictlyTemplate = st;
						}
					} else if (argExpr instanceof KtNameReferenceExpression nameRef) {
						if (GradleVersionConstraint.PREFER.equals(name) && preferNameRef == null) {
							preferNameRef = nameRef;
						} else if (GradleVersionConstraint.STRICTLY.equals(name) && strictlyNameRef == null) {
							strictlyNameRef = nameRef;
						}
					}
				}
			}
		}

		PsiElement versionLiteralElement;
		String versionProperty = null;
		String version;

		if (preferTemplate != null) {
			version = resolveKotlinStringTemplate(preferTemplate, propertyResolver);
			versionLiteralElement = preferTemplate;
			versionProperty = extractTemplatePropertyKey(preferTemplate);
		} else if (preferNameRef != null) {
			String refName = preferNameRef.getReferencedName();
			PropertyValue resolved = resolveVersionProperty(propertyResolver, refName);
			if (resolved == null) {
				return null;
			}
			version = resolved.propertyValue();
			versionLiteralElement = resolved.element();
			versionProperty = refName;
		} else if (strictlyTemplate != null) {
			String strictlyText = resolveKotlinStringTemplate(strictlyTemplate, propertyResolver);
			if (GradleUtils.isVersionRange(strictlyText)) {
				return null;
			}
			version = strictlyText;
			versionLiteralElement = strictlyTemplate;
			versionProperty = extractTemplatePropertyKey(strictlyTemplate);
		} else if (strictlyNameRef != null) {
			String refName = strictlyNameRef.getReferencedName();
			PropertyValue resolved = resolveVersionProperty(propertyResolver, refName);
			if (resolved == null) {
				return null;
			}
			version = resolved.propertyValue();
			versionLiteralElement = resolved.element();
			versionProperty = refName;
		} else {
			return null;
		}

		if (StringUtils.isEmpty(version)) {
			return null;
		}

		String gavText = resolveKotlinStringTemplate(gavTemplate, propertyResolver);
		if (gavText == null) {
			return null;
		}

		String[] parts = gavText.split(":");
		String group = parts[0];
		String artifact = parts[1];

		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact, versionProperty, version,
				call, versionLiteralElement);
	}

	private static @Nullable PropertyValue resolveVersionProperty(PropertyResolver propertyResolver,
			String refName) {

		if (!StringUtils.hasText(refName)) {
			return null;
		}

		return propertyResolver.getElement(refName);
	}

	private static @Nullable KtStringTemplateExpression findInlineDependencyLiteral(KtCallElement call,
			PropertyResolver propertyResolver) {

		for (ValueArgument valueArgument : call.getValueArguments()) {
			KtExpression expression = valueArgument.getArgumentExpression();
			if (expression instanceof KtStringTemplateExpression template) {
				GradleDependency dependency = GradleDependency.parse(KtLiterals.from(template).toString(),
						propertyResolver);
				if (dependency != null && dependency.getVersionSource().isDefined()) {
					return template;
				}
			}

			if (expression instanceof KtCallElement nested
					&& GradleUtils.isPlatformSection(KotlinDslUtils.getKotlinCallName(nested))) {
				KtStringTemplateExpression nestedTemplate = findInlineDependencyLiteral(nested, propertyResolver);
				if (nestedTemplate != null) {
					return nestedTemplate;
				}
			}
		}

		return null;
	}

	public static @Nullable DependencySite findDependencySite(KtCallElement call,
			PropertyResolver scriptProperties) {

		PsiFile file = call.getContainingFile();
		if (!GradleUtils.isKotlinDsl(file)) {
			return null;
		}

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (StringUtils.isEmpty(methodName) || !KotlinDslUtils.isDependencyCall(call)) {
			return null;
		}

		if (GradleUtils.isPlugin(methodName) && KotlinDslUtils.isInsidePluginsBlock(call)) {
			return KotlinDslUtils.findPluginSite(call,
					PsiTreeUtil.getParentOfType(call, KtBinaryExpression.class),
					scriptProperties);
		}

		KtStringTemplateExpression directNotation = findInlineDependencyLiteral(call, scriptProperties);
		if (directNotation != null) {
			return KotlinDslUtils.getDependencySite(call, directNotation,
					KtLiterals.from(call.getValueArgumentList()).toString(), null);
		}

		KtVersion ktVersion = KtVersion.fromDependency(call);
		if (ktVersion == null || !ktVersion.containsVersion()) {
			return null;
		}

		PropertyExpression versionExpression;
		PsiElement versionElement = ktVersion.getVersionElement();
		if (ktVersion.hasProperty()) {
			String property = ktVersion.getProperty();
			versionExpression = PropertyExpression.property(property);
			PropertyValue version = scriptProperties.getElement(property);
			if (version != null) {
				versionElement = version.element();
			}
		} else {
			versionExpression = PropertyExpression.from(ktVersion.getVersion());
		}

		return KotlinDslUtils.getDependencySite(call, versionElement != null ? versionElement : call,
				KtLiterals.from(call.getValueArgumentList()).toString(), versionExpression);
	}

	/**
	 * Return whether the given call is a map-style declaration candidate
	 * {@code implementation(group = "org.junit", name = "junit-bom", …)}.
	 */
	public static boolean isMapStyleDeclarationCandidate(KtCallElement call) {

		KtValueArgumentList valueArgumentList = call.getValueArgumentList();
		if (valueArgumentList != null) {
			return valueArgumentList.getArguments().size() > 1;
		}

		return false;
	}

	static NamedDependencyDeclaration parseMapDeclaration(KtCallElement call,
			PropertyResolver propertyResolver) {

		String group = null;
		String artifact = null;
		String version = null;
		String versionProperty = null;
		PsiElement versionLiteral = null;

		for (ValueArgument arg : call.getValueArguments()) {
			String name = arg.getArgumentName() != null ? arg.getArgumentName().getAsName().asString() : null;
			PsiElement expr = arg.getArgumentExpression();
			String strVal = expr instanceof KtStringTemplateExpression st
					? resolveKotlinStringTemplate(st, propertyResolver)
					: null;

			if ("group".equals(name)) {
				group = strVal;
			} else if ("name".equals(name)) {
				artifact = strVal;
			} else if ("version".equals(name)) {
				if (expr instanceof KtStringTemplateExpression st) {
					String propKey = extractTemplatePropertyKey(st);
					if (propKey != null && propertyResolver.containsProperty(propKey)) {
						version = propertyResolver.getProperty(propKey);
						versionLiteral = expr;
						versionProperty = propKey;
					} else {
						version = renderKotlinStringTemplate(st);
						versionLiteral = expr;
					}
				} else if (expr instanceof KtNameReferenceExpression ref) {
					String refName = ref.getReferencedName();
					PropertyValue element = propertyResolver.getElement(refName);
					if (element != null) {
						version = element.propertyValue();
						versionLiteral = element.element();
						versionProperty = refName;
					}
				}
			}
		}

		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact, versionProperty, version,
				call, versionLiteral);
	}

	/**
	 * Extracts the property key referenced in a string template that contains a
	 * single expression of the form {@code ${property("key")}},
	 * {@code ${extra["key"]}}, {@code ${project.property("key")}}, or
	 * {@code ${varName}}. Returns {@code null} when the template contains literals
	 * or multiple/unsupported expressions.
	 */
	private static @Nullable String extractTemplatePropertyKey(KtStringTemplateExpression template) {

		String[] result = {null};
		boolean[] hasLiteral = {false};

		KotlinDslUtils.doWithStrings(template, text -> {
			if (!text.isEmpty()) {
				hasLiteral[0] = true;
			}
		}, expression -> {
			String key = KotlinDslUtils.getPropertyName(expression);
			if (StringUtils.hasText(key)) {
				result[0] = key;
			}
		});

		if (hasLiteral[0]) {
			return null;
		}
		return result[0];
	}

	private @Nullable String resolveKotlinStringTemplate(KtStringTemplateExpression st) {
		return resolveKotlinStringTemplate(st, this);
	}

	private static @Nullable String resolveKotlinStringTemplate(KtStringTemplateExpression st,
			PropertyResolver propertyResolver) {

		StringBuilder builder = new StringBuilder();
		boolean[] failed = new boolean[1];
		KotlinDslUtils.doWithStrings(st, builder::append, expression -> {

			String resolved = KotlinDslUtils.resolveKotlinExpression(expression, propertyResolver);
			if (resolved == null) {
				failed[0] = true;
			} else {
				builder.append(resolved);
			}
		});

		if (failed[0]) {
			return null;
		}
		String merged = builder.toString();
		return propertyResolver.resolvePlaceholders(merged);
	}

	private static String renderKotlinStringTemplate(KtStringTemplateExpression st) {

		StringBuilder builder = new StringBuilder();
		KotlinDslUtils.doWithStrings(st, builder::append, expression -> {

			if (expression instanceof KtCallExpression ktCall) {
				String name = KotlinDslUtils.getKotlinCallName(ktCall);
				StringBuilder property = new StringBuilder();
				if ("property".equals(name)) {
					KotlinDslUtils.doWithStrings(ktCall, property::append, ignore -> {
					});
					String key = property.toString();
					builder.append("${").append(key).append("}");
				}
			}
		});

		return builder.toString();
	}


}
