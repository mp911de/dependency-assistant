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
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.util.StringUtils;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
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
	 * Parses top-level {@code val key = "value"} declarations from a Kotlin DSL
	 * file and returns them as {@link PsiPropertyValueElement} instances.
	 */
	static Map<String, PsiPropertyValueElement> parseValProperties(PsiFile file) {

		Map<String, PsiPropertyValueElement> result = new LinkedHashMap<>();
		SyntaxTraverser.psiTraverser(file)
				.filter(KtProperty.class)
				.forEach(property -> {
					if (property.getInitializer() instanceof KtStringTemplateExpression template) {
						String name = property.getName();
						String value = KotlinDslUtils.getText(template);
						if (name != null && value != null) {
							result.put(name, new PsiPropertyValueElement(template, name, value));
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

		DependencyAndVersionLocation versionLocation = KotlinDslUtils.findKotlinVersionElement(call, this);

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
		NamedDependencyDeclaration versionBlockEntry = parseVersionBlockDependency(call, this);
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
	@Nullable
	static NamedDependencyDeclaration parseVersionBlockDependency(KtCallElement call,
			PropertyResolver propertyResolver) {

		KtStringTemplateExpression gavTemplate = null;
		for (ValueArgument arg : call.getValueArguments()) {
			if (arg instanceof KtLambdaArgument) {
				continue;
			}
			KtStringTemplateExpression expr = arg.getArgumentExpression() instanceof KtStringTemplateExpression st ? st
					: null;
			if (expr != null) {
				String text = resolveKotlinStringTemplate(expr, propertyResolver);
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
		KtBlockExpression trailingBody = trailingLambda.getBodyExpression();
		if (trailingBody != null) {
			for (KtExpression stmt : trailingBody.getStatements()) {
				if (stmt instanceof KtCallExpression inner
						&& "version".equals(KotlinDslUtils.getKotlinCallName(inner))) {
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

		KtBlockExpression versionBody = versionLambda.getBodyExpression();
		if (versionBody != null) {
			for (KtExpression stmt : versionBody.getStatements()) {
				if (!(stmt instanceof KtCallExpression inner)) {
					continue;
				}
				String name = KotlinDslUtils.getKotlinCallName(inner);
				for (ValueArgument va : inner.getValueArguments()) {
					if (va.getArgumentExpression() instanceof KtStringTemplateExpression st) {
						if ("prefer".equals(name) && preferTemplate == null) {
							preferTemplate = st;
						} else if ("strictly".equals(name) && strictlyTemplate == null) {
							strictlyTemplate = st;
						}
					}
				}
			}
		}

		KtStringTemplateExpression versionLiteralTemplate;
		if (preferTemplate != null) {
			versionLiteralTemplate = preferTemplate;
		} else if (strictlyTemplate != null) {
			String strictlyText = KotlinDslUtils.getText(strictlyTemplate);
			if (GradleParser.isVersionRange(strictlyText)) {
				return null;
			}
			versionLiteralTemplate = strictlyTemplate;
		} else {
			return null;
		}

		String version = KotlinDslUtils.getText(versionLiteralTemplate);
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

		return new NamedDependencyDeclaration(call.getContainingFile(), null, group, artifact, null, version, call,
				versionLiteralTemplate);
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
					version = renderKotlinStringTemplate(st);
					versionLiteral = expr;
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
