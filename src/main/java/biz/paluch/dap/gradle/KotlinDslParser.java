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
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import org.jetbrains.kotlin.psi.KtArrayAccessExpression;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtNameReferenceExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;
import org.jspecify.annotations.Nullable;

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
class KotlinDslParser extends GradleParser {

	private final Map<String, String> properties;

	public KotlinDslParser(DependencyCollector collector) {
		this(collector, new LinkedHashMap<>());
	}

	public KotlinDslParser(Map<String, String> properties) {
		super(properties);
		this.properties = properties;
	}

	public KotlinDslParser(DependencyCollector collector, Map<String, String> properties) {
		super(collector, properties);
		this.properties = properties;
	}

	// -------------------------------------------------------------------------
	// Extra properties
	// -------------------------------------------------------------------------

	/**
	 * Collects all {@code extra["key"] = "value"} property declarations from a Kotlin DSL file.
	 * <p>
	 * This is the Kotlin DSL equivalent of {@code gradle.properties}: instead of a separate file, versions can be
	 * declared inline in a {@code build.gradle.kts} script and referenced elsewhere via {@code property("key")} or
	 * {@code extra["key"]}.
	 *
	 * @param file the Kotlin build script ({@code .kts})
	 * @return a map of property key to literal value; entries whose value contains interpolation are omitted
	 */
	public static Map<String, String> parseExtraProperties(PsiFile file) {
		Map<String, String> result = new HashMap<>();
		file.accept(new PsiRecursiveElementVisitor() {
			@Override
			public void visitElement(PsiElement element) {
				super.visitElement(element);
				if (element instanceof KtBinaryExpression binExpr) {
					result.putAll(parseExtra(binExpr));
				}
			}
		});
		return result;
	}

	/**
	 * If {@code expr} is an assignment of the form {@code extra["key"] = "literalValue"}, adds the key-value pair to
	 * {@code result}.
	 */
	private static Map<String, String> parseExtra(KtBinaryExpression expr) {

		// Must be an assignment: operator text is "="
		if (!"=".equals(expr.getOperationReference().getText())) {
			return Map.of();
		}

		KtExpression left = expr.getLeft();
		KtExpression right = expr.getRight();

		// Left must be extra["key"]
		if (!(left instanceof KtArrayAccessExpression arrayAccess)) {
			return Map.of();
		}
		KtExpression receiver = arrayAccess.getArrayExpression();
		if (!(receiver instanceof KtNameReferenceExpression nameRef) || !"extra".equals(nameRef.getReferencedName())) {
			return Map.of();
		}

		// Index must be a plain string literal
		if (arrayAccess.getIndexExpressions().isEmpty()) {
			return Map.of();
		}
		KtExpression indexExpr = arrayAccess.getIndexExpressions().get(0);
		if (!(indexExpr instanceof KtStringTemplateExpression keyTemplate)) {
			return Map.of();
		}
		String key = extractStringLiteral(keyTemplate);
		if (key == null) {
			return Map.of();
		}

		// Right side must be a plain string literal (no interpolation)
		if (!(right instanceof KtStringTemplateExpression valueTemplate)) {
			return Map.of();
		}
		String value = extractStringLiteral(valueTemplate);
		if (value != null) {
			return Map.of(key, value);
		}

		return Map.of();
	}

	/**
	 * Extracts the literal string value from a {@link KtStringTemplateExpression} that contains no template entries (no
	 * {@code $name} or {@code ${expr}} interpolation). Returns {@code null} for interpolated or empty strings.
	 */
	private static @Nullable String extractStringLiteral(KtStringTemplateExpression template) {
		String text = template.getText();
		if (text == null || text.length() < 2) {
			return null;
		}
		// Strip surrounding quotes
		char open = text.charAt(0);
		char close = text.charAt(text.length() - 1);
		if (!((open == '"' && close == '"') || (open == '\'' && close == '\''))) {
			return null;
		}
		String value = text.substring(1, text.length() - 1);
		// Reject interpolated values — they would need recursive resolution
		if (value.contains("${") || (value.contains("$") && value.matches(".*\\$[A-Za-z_].*"))) {
			return null;
		}
		return value.isEmpty() ? null : value;
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

		file.accept(new PsiRecursiveElementVisitor() {
			@Override
			public void visitElement(PsiElement element) {
				super.visitElement(element);
				if (element instanceof KtCallElement call) {
					handleKotlinCall(call);
				}
			}
		});
	}

	private void handleKotlinCall(KtCallElement call) {

		String methodName = KotlinDslUtils.getKotlinCallName(call);
		if (methodName == null) {
			return;
		}

		boolean isDependencyConfig = GradleUtils.DEPENDENCY_CONFIGS.contains(methodName);
		boolean isPlatform = GradleUtils.PLATFORM_FUNCTIONS.contains(methodName);
		boolean isPlugin = KotlinDslUtils.isInsideKotlinBlock(call, "plugins");

		GroovyDslUtils.VersionLocation versionLocation = KotlinDslUtils.findKotlinVersionElement(call);

		if (!isDependencyConfig && !isPlatform && !isPlugin) {
			return;
		}

		if (isPlugin) {
			if (versionLocation == null) {
				return;
			}

			register(toGradleDependency(versionLocation), DeclarationSource.plugin());
			return;
		}

		if (!KotlinDslUtils.isInsideKotlinBlock(call, "dependencies") && !isPlatform) {
			return;
		}

		DeclarationSource declarationSource = isPlatform ? DeclarationSource.managed() : DeclarationSource.dependency();
		if (versionLocation != null) {
			register(toGradleDependency(versionLocation), declarationSource);
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

		register(ArtifactId.of(group, artifact), version, src);
	}

	private String resolveKotlinStringTemplate(KtStringTemplateExpression st) {

		StringBuilder builder = new StringBuilder();
		KotlinDslUtils.doWithStrings(st, builder::append, expression -> {

			if (expression instanceof KtCallExpression ktCall) {
				String name = KotlinDslUtils.getKotlinCallName(ktCall);
				StringBuilder property = new StringBuilder();
				if ("property".equals(name)) {
					KotlinDslUtils.doWithStrings(ktCall, property::append, ignore -> {});
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
					KotlinDslUtils.doWithStrings(ktCall, property::append, ignore -> {});
					builder.append("${").append(properties.getOrDefault(property.toString(), property.toString())).append("}");
				}
			}
		});

		return builder.toString();
	}

}
