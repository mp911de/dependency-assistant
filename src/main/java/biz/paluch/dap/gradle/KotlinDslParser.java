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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.support.PropertyExpression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.psi.*;
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

	/**
	 * Returns the version segment {@link KtCallElement} if the caret is inside the
	 * version part of a Kotlin DSL string-notation dependency
	 * ({@code "group:artifact:version"}), or {@code null}.
	 */
	public static @Nullable DependencyAndVersionLocation findKotlinVersionElement(KtCallElement call,
			PropertyResolver scriptProperties) {

		PsiFile file = call.getContainingFile();
		if (!GradleUtils.isGradleFile(file)) {
			return null;
		}

		// Check if this call is prefer/strictly inside a version {} block of a
		// dependency

		KtVersion ktVersion = KtVersion.fromDependency(call);
		KtBinaryExpression be = PsiTreeUtil.getParentOfType(call, KtBinaryExpression.class);
		KtCallExpression parentCall = PsiTreeUtil.getParentOfType(call, KtCallExpression.class);
		if (parentCall == null) {
			return null;
		}

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (StringUtils.isEmpty(methodName) || !KotlinDslUtils.isDependencyCall(call)) {
			return null;
		}

		if (GradleUtils.isPlugin(methodName) && KotlinDslUtils.isInsidePluginsBlock(call)) {
			return KotlinDslUtils.findKotlinPluginLocation(call, be, scriptProperties);
		}

		KtLiterals literals = KtLiterals.from(call.getValueArgumentList());
		PropertyExpression versionExpression = null;
		PsiElement versionElement = call;
		if (ktVersion != null) {

			// e.g. implementation("org.slf4j:slf4j-api") { version { strictly(range) }}
			if (!ktVersion.containsVersion()) {
				return null;
			}

			if (ktVersion.hasProperty()) {
				versionExpression = PropertyExpression.property(ktVersion.getProperty());
				PsiPropertyValueElement version = scriptProperties.getElement(ktVersion.getProperty());
				if (version != null) {
					versionElement = version.element();
				}
			} else {
				versionExpression = PropertyExpression.from(ktVersion.getVersion());
			}
		}

		return KotlinDslUtils.getVersionLocation(parentCall, versionElement, literals.toString(), versionExpression);
	}

	/**
	 * implementation(group = "org.junit", name = "junit-bom", version = junit)
	 */
	public static @Nullable DependencyAndVersionLocation findDependencyAndVersionLocationFromMapDeclaration(
			KtCallElement call,
			KtElement versionElement, PropertyResolver scriptProperties) {

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (StringUtils.isEmpty(methodName) || !KotlinDslUtils.isDependencyCall(call)) {
			return null;
		}

		if (versionElement instanceof KtNameReferenceExpression referenceExpression) {

			String propertyName = referenceExpression.getReferencedName();

			PsiPropertyValueElement version = scriptProperties.getElement(propertyName);
			if (version != null) {
				NamedDependencyDeclaration namedDependencyDeclaration = parseMapDeclaration(call, scriptProperties);
				if (namedDependencyDeclaration.isComplete()) {
					GradleDependency dependency = namedDependencyDeclaration.toDependency(scriptProperties);
					return new DependencyAndVersionLocation(dependency, versionElement);
				}
			}
		}

		if (versionElement instanceof KtLiteralStringTemplateEntry entry) {

			KtLiterals literals = KtLiterals.from(entry);
			if (literals.hasText() && !literals.hasProperty()) {

				NamedDependencyDeclaration namedDependencyDeclaration = parseMapDeclaration(call, scriptProperties);
				if (namedDependencyDeclaration.isComplete()) {
					GradleDependency dependency = namedDependencyDeclaration.toDependency(scriptProperties);
					return new DependencyAndVersionLocation(dependency, versionElement);
				}
			}
		}

		return null;
	}

	// -------------------------------------------------------------------------
	// Kotlin DSL
	// -------------------------------------------------------------------------

	/**
	 * Parses a Kotlin {@code build.gradle.kts} or {@code settings.gradle.kts} file.
	 */
	public void parseKotlinScript(PsiFile file) {

		getPropertyMap().putAll(KotlinDslExtraParser.getExtraProperties(file));
		Map<String, PsiPropertyValueElement> valProperties = parseValProperties(file);
		valProperties.forEach((k, v) -> {
			getPropertyMap().putIfAbsent(k, v.propertyValue());
			getPropertyLookup().putIfAbsent(k, v);
		});
		getCollector().addProperties(getPropertyMap().keySet());

		SyntaxTraverser.psiTraverser(file).filter(KtCallElement.class).forEach(this::handleKotlinCall);
	}

	/**
	 * Parses top-level {@code val} declarations from a Kotlin DSL file and returns
	 * them as {@link PsiPropertyValueElement} instances. Handles:
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
	Map<String, PsiPropertyValueElement> parseValProperties(PsiFile file) {

		Map<String, PsiPropertyValueElement> result = new LinkedHashMap<>();
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
							result.put(name, new PsiPropertyValueElement(template, name, value));
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
								result.put(name, new PsiPropertyValueElement(property, name, value));
							}
						} else if ("extra".equals(delegateName)) {
							String value = getPropertyMap().get(name);
							if (value != null) {
								PsiPropertyValueElement extraLocation = KotlinDslExtraParser
										.findExtraPropertyLocation(file, name);
								PsiElement psiElement = extraLocation != null ? extraLocation.element() : property;
								result.put(name, new PsiPropertyValueElement(psiElement, name, value));
							}
						}
					} else if (delegateExpr instanceof KtCallExpression delegateCall
							&& "extra".equals(KotlinDslUtils.getKotlinCallName(delegateCall))) {
						for (ValueArgument va : delegateCall.getValueArguments()) {
							if (va.getArgumentExpression() instanceof KtStringTemplateExpression argTemplate) {
								String value = KotlinDslUtils.getText(argTemplate);
								if (value != null) {
									result.put(name, new PsiPropertyValueElement(argTemplate, name, value));
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
		DependencyAndVersionLocation versionLocation = findKotlinVersionElement(call, this);

		if (isPlugin) {
			if (versionLocation == null) {
				return;
			}

			register(versionLocation.dependency(), DeclarationSource.plugin());
			return;
		}

		if (!isDependencyConfig && !isPlatform) {
			return;
		}

		if (!KotlinDslUtils.isInsideBlock(call, "dependencies"::equals) && !isPlatform) {
			return;
		}

		DeclarationSource declarationSource = isPlatform ? DeclarationSource.managed() : DeclarationSource.dependency();
		if (versionLocation != null) {
			register(versionLocation.dependency(), declarationSource);
			return;
		}

		// Try version block: implementation("g:a") { version { prefer("1.0") } }
		NamedDependencyDeclaration versionBlockEntry = parseVersionBlockDependency(call);
		if (versionBlockEntry != null && versionBlockEntry.isComplete()) {
			register(versionBlockEntry.toDependency(this), declarationSource);
			return;
		}

		// Named arguments: group = "g", name = "a", version = "v"
		registerMapDeclaration(call, declarationSource);
	}

	/**
	 * Parses a version-block dependency of the form: <pre class="code">
	 * implementation("group:artifact") {
	 *     version {
	 *         strictly("[1.7, 1.8[")
	 *         prefer("1.7.25")
	 *     }
	 * }
	 * </pre> Returns a {@link NamedDependencyDeclaration} when a usable version can
	 * be extracted, or {@code null} otherwise.
	 */
	private @Nullable NamedDependencyDeclaration parseVersionBlockDependency(KtCallElement call) {

		KtStringTemplateExpression gavTemplate = null;
		for (ValueArgument arg : call.getValueArguments()) {
			if (arg instanceof KtLambdaArgument) {
				continue;
			}
			KtStringTemplateExpression expr = arg.getArgumentExpression() instanceof KtStringTemplateExpression st ? st
					: null;
			if (expr != null) {
				String text = resolveKotlinStringTemplate(expr);
				if (text != null && text.split(":").length == 2) {
					gavTemplate = expr;
					break;
				}
			}
		}

		if (gavTemplate == null) {
			return null;
		}

		KtLambdaExpression trailingLambda = null;
		for (ValueArgument arg : call.getValueArguments()) {
			if (arg instanceof KtLambdaArgument la && la.getArgumentExpression() instanceof KtLambdaExpression lam) {
				trailingLambda = lam;
				break;
			}
		}

		if (trailingLambda == null) {
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
			version = resolveKotlinStringTemplate(preferTemplate);
			versionLiteralElement = preferTemplate;
			versionProperty = extractTemplatePropertyKey(preferTemplate);
		} else if (preferNameRef != null) {
			String refName = preferNameRef.getReferencedName();
			PsiPropertyValueElement resolved = this.getElement(refName);
			if (resolved == null) {
				return null;
			}
			version = resolved.propertyValue();
			versionLiteralElement = resolved.element();
			versionProperty = refName;
		} else if (strictlyTemplate != null) {
			String strictlyText = resolveKotlinStringTemplate(strictlyTemplate);
			if (GradleUtils.isVersionRange(strictlyText)) {
				return null;
			}
			version = strictlyText;
			versionLiteralElement = strictlyTemplate;
			versionProperty = extractTemplatePropertyKey(strictlyTemplate);
		} else if (strictlyNameRef != null) {
			String refName = strictlyNameRef.getReferencedName();
			PsiPropertyValueElement resolved = this.getElement(refName);
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

		String gavText = resolveKotlinStringTemplate(gavTemplate);
		if (gavText == null) {
			return null;
		}

		String[] parts = gavText.split(":");
		String group = parts[0];
		String artifact = parts[1];

		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact, versionProperty, version,
				call, versionLiteralElement);
	}

	private void registerMapDeclaration(KtCallElement call, DeclarationSource src) {

		NamedDependencyDeclaration entry = parseMapDeclaration(call, this);

		if (entry.isComplete()) {
			register(entry.toDependency(this), src);
		}
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
					PsiPropertyValueElement element = propertyResolver.getElement(refName);
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
			if (expression instanceof KtNameReferenceExpression ref) {
				result[0] = ref.getReferencedName();
			} else if (expression instanceof KtCallExpression call
					&& "property".equals(KotlinDslUtils.getKotlinCallName(call))) {
				StringBuilder key = new StringBuilder();
				KotlinDslUtils.doWithStrings(call, key::append, e -> {
				});
				result[0] = key.toString();
			} else if (expression instanceof KtArrayAccessExpression arrayAccess
					&& arrayAccess.getArrayExpression() != null
					&& "extra".equals(arrayAccess.getArrayExpression().getText())) {
				List<KtExpression> indices = arrayAccess.getIndexExpressions();
				if (!indices.isEmpty()) {
					StringBuilder key = new StringBuilder();
					KotlinDslUtils.doWithStrings(indices.get(0), key::append, e -> {
					});
					result[0] = key.toString();
				}
			} else if (expression instanceof KtDotQualifiedExpression dotQualified
					&& dotQualified.getSelectorExpression() instanceof KtCallExpression selectorCall
					&& "property".equals(KotlinDslUtils.getKotlinCallName(selectorCall))) {
				StringBuilder key = new StringBuilder();
				KotlinDslUtils.doWithStrings(selectorCall, key::append, e -> {
				});
				result[0] = key.toString();
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
