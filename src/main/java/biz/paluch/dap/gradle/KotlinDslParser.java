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

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.util.PsiVisitors;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.kotlin.psi.KtArrayAccessExpression;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;

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

	private final Map<String, String> properties;

	public KotlinDslParser(DependencyCollector collector) {
		this(collector, new LinkedHashMap<>());
	}

	public KotlinDslParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector, properties);
		this.properties = properties;
	}

	// -------------------------------------------------------------------------
	// Extra properties
	// -------------------------------------------------------------------------

	/**
	 * Collects {@code extra["key"]} property declarations from a Kotlin DSL file,
	 * including:
	 * <ul>
	 * <li>{@code extra["key"] = "value"} and
	 * {@code extra["key"] = """value"""}</li>
	 * <li>{@code "value".also { extra["key"] = it }}</li>
	 * <li>{@code extra["key"] = buildString { append("value") }}</li>
	 * </ul>
	 * <p>This is the Kotlin DSL equivalent of {@code gradle.properties}: versions
	 * can be declared inline in {@code build.gradle.kts} and referenced via
	 * {@code property("key")} or {@code extra["key"]}.
	 *
	 * @param file the Kotlin build script ({@code .kts})
	 * @return a map of property key to literal value; entries whose value contains
	 * interpolation are omitted
	 */
	public static Map<String, String> parseExtraProperties(PsiFile file) {

		Map<String, String> result = new HashMap<>();

		file.accept(PsiVisitors.conditionalVisitTree(KtBinaryExpression.class, KotlinDslUtils::isExtra,
				expression -> {
					result.putAll(parseExtra(expression));
				}));
		return result;
	}

	/**
	 * If {@code expr} assigns a resolvable literal to {@code extra["key"]}, returns
	 * that key-value pair.
	 */
	private static Map<String, String> parseExtra(KtBinaryExpression expr) {

		KtExpression left = expr.getLeft();
		KtExpression right = expr.getRight();

		// Left must be extra["key"]
		if (!(left instanceof KtArrayAccessExpression arrayAccess) || arrayAccess.getIndexExpressions().isEmpty()) {
			return Map.of();
		}

		KtExpression indexExpr = arrayAccess.getIndexExpressions().get(0);
		if (!(indexExpr instanceof KtStringTemplateExpression keyTemplate)) {
			return Map.of();
		}
		String key = KotlinDslUtils.getText(keyTemplate);
		if (key == null) {
			return Map.of();
		}

		String value = KotlinDslUtils.getText(right);
		if (value != null) {
			return Map.of(key, value);
		}

		return Map.of();
	}

	// -------------------------------------------------------------------------
	// Kotlin DSL
	// -------------------------------------------------------------------------

	/**
	 * Parses a Kotlin {@code build.gradle.kts} or {@code settings.gradle.kts} file.
	 */
	public void parseKotlinScript(PsiFile file) {

		properties.putAll(parseExtraProperties(file));
		getCollector().addProperties(properties.keySet());

		file.accept(PsiVisitors.visitTree(KtCallElement.class, this::handleKotlinCall));
	}

	private void handleKotlinCall(KtCallElement call) {

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (methodName == null) {
			return;
		}

		boolean isDependencyConfig = GradleUtils.isDependencySection(methodName);
		boolean isPlatform = GradleUtils.isPlatformSection(methodName);
		boolean isPlugin = KotlinDslUtils.isInsidePluginsBlock(call);

		DependencyLocation versionLocation = KotlinDslUtils.findKotlinVersionElement(call);

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

		// Named arguments: group = "g", name = "a", version = "v"
		registerMapDeclaration(call, declarationSource);
	}

	private void registerMapDeclaration(KtCallElement call, DeclarationSource src) {

		String group = null;
		String artifact = null;
		String version = null;

		for (ValueArgument arg : call.getValueArguments()) {
			String name = arg.getArgumentName() != null ? arg.getArgumentName().getAsName().asString() : null;
			PsiElement expr = arg.getArgumentExpression();

			String strVal = expr instanceof KtStringTemplateExpression st ? resolveKotlinStringTemplate(st) : null;
			String rendered = expr instanceof KtStringTemplateExpression st ? renderKotlinStringTemplate(st)
					: null;
			if ("group".equals(name)) {
				group = strVal;
			} else if ("name".equals(name)) {
				artifact = strVal;
			} else if ("version".equals(name)) {
				version = rendered;
			}
		}

		if (group == null || artifact == null || version == null) {
			return;
		}

		register(GradleDependency.of(group, artifact, version, this), src);
	}

	private String resolveKotlinStringTemplate(KtStringTemplateExpression st) {

		StringBuilder builder = new StringBuilder();
		KotlinDslUtils.doWithStrings(st, builder::append, expression -> {

			if (expression instanceof KtCallExpression ktCall) {
				String name = KotlinDslUtils.getKotlinCallName(ktCall);
				StringBuilder property = new StringBuilder();
				if ("property".equals(name)) {
					KotlinDslUtils.doWithStrings(ktCall, property::append, ignore -> {
					});
					builder.append(properties.getOrDefault(property.toString(), property.toString()));
				}
			}
		});

		return builder.toString();
	}

	private String renderKotlinStringTemplate(KtStringTemplateExpression st) {

		StringBuilder builder = new StringBuilder();
		KotlinDslUtils.doWithStrings(st, builder::append, expression -> {

			if (expression instanceof KtCallExpression ktCall) {
				String name = KotlinDslUtils.getKotlinCallName(ktCall);
				StringBuilder property = new StringBuilder();
				if ("property".equals(name)) {
					KotlinDslUtils.doWithStrings(ktCall, property::append, ignore -> {
					});
					builder.append("${").append(properties.getOrDefault(property.toString(), property.toString()))
							.append("}");
				}
			}
		});

		return builder.toString();
	}

}
