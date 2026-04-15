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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;
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

	public KotlinDslParser(DependencyCollector collector) {
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
		getCollector().addProperties(getPropertyMap().keySet());

		SyntaxTraverser.psiTraverser(file).filter(KtCallElement.class).forEach(this::handleKotlinCall);
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
			String rendered = expr instanceof KtStringTemplateExpression st ? renderKotlinStringTemplate(st) : null;
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

	private @Nullable String resolveKotlinStringTemplate(KtStringTemplateExpression st) {

		StringBuilder builder = new StringBuilder();
		boolean[] failed = new boolean[1];
		KotlinDslUtils.doWithStrings(st, builder::append, expression -> {

			String resolved = KotlinDslUtils.resolveKotlinExpression(expression, this);
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
		return this.resolvePlaceholders(merged);
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
					String key = property.toString();
					builder.append("${").append(key).append("}");
				}
			}
		});

		return builder.toString();
	}

}
