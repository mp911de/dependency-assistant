/*
 * Copyright 2026-present the original author or authors.
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

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;
import org.jspecify.annotations.Nullable;

/**
 * Parses {@code settings.gradle.kts} to discover version-catalog declarations
 * in a {@code dependencyResolutionManagement { versionCatalogs { ... } }}
 * block.
 *
 * <p>Only {@code from(files("path/to/catalog.toml"))} forms are handled. Other
 * {@code from} forms, such as {@code from(uri(...))}, are skipped.
 *
 * @author Mark Paluch
 */
class KotlinDslSettingsParser {

	/**
	 * Parse the given {@code settings.gradle.kts} file into a catalog registry.
	 *
	 * <p>The configured default alias is always registered. If it has no explicit
	 * catalog declaration, it uses {@code gradle/libs.versions.toml}.
	 * @param file the Kotlin DSL settings file to parse.
	 * @return the configured catalogs and default alias.
	 */
	static VersionCatalogRegistry parseRegistry(PsiFile file) {

		Map<String, String> catalogs = new LinkedHashMap<>();
		String alias = TomlParser.LIBS;

		JBIterable<KtCallExpression> managementCalls = SyntaxTraverser.psiTraverser(file)
				.filter(KtCallExpression.class)
				.filter(it -> "dependencyResolutionManagement".equals(KotlinDslUtils.getKotlinCallName(it)));

		for (KtCallExpression managementCall : managementCalls) {
			for (KtExpression statement : getLambdaStatements(managementCall)) {

				if (statement instanceof KtCallExpression catalogsCall
						&& "versionCatalogs".equals(KotlinDslUtils.getKotlinCallName(catalogsCall))) {
					parseVersionCatalogsBlock(catalogsCall, catalogs);
				}

				if (statement instanceof KtBinaryExpression binary
						&& "defaultLibrariesExtensionName".equals(KtLiterals.nameOf(binary.getLeft()))
						&& binary.getRight() != null) {
					alias = KtLiterals.getText(binary.getRight());
				}
			}
		}

		if (!catalogs.containsKey(alias)) {
			catalogs.put(alias, GradleUtils.DEFAULT_TOML_LOCATION);
		}

		return new VersionCatalogRegistry(catalogs, alias);
	}

	private static void parseVersionCatalogsBlock(KtCallExpression catalogsCall, Map<String, String> catalogs) {

		for (KtExpression statement : getLambdaStatements(catalogsCall)) {

			if (!(statement instanceof KtCallExpression createCall)
					|| !"create".equals(KotlinDslUtils.getKotlinCallName(createCall))) {
				continue;
			}

			String alias = extractFirstStringArgument(createCall);
			String path = alias != null ? extractFromFilesPath(createCall) : null;
			if (path != null) {
				catalogs.put(alias, path);
			}
		}
	}

	private static @Nullable String extractFromFilesPath(KtCallExpression createCall) {

		for (KtExpression statement : getLambdaStatements(createCall)) {
			if (statement instanceof KtCallExpression fromCall
					&& "from".equals(KotlinDslUtils.getKotlinCallName(fromCall))) {
				return extractFilesArgument(fromCall);
			}
		}

		return null;
	}

	/**
	 * Return the statements of the call's trailing lambda, or none.
	 */
	private static List<KtExpression> getLambdaStatements(KtCallExpression call) {

		KtLambdaExpression lambda = KotlinDslUtils.getLambdaArgument(call);
		KtBlockExpression body = lambda != null ? lambda.getBodyExpression() : null;
		return body != null ? body.getStatements() : List.of();
	}

	private static @Nullable String extractFilesArgument(KtCallExpression fromCall) {

		List<? extends ValueArgument> args = fromCall.getValueArguments();
		if (args.isEmpty()) {
			return null;
		}

		KtExpression argExpr = args.get(0).getArgumentExpression();
		if (!(argExpr instanceof KtCallExpression filesCall)) {
			return null;
		}

		if (!"files".equals(KotlinDslUtils.getKotlinCallName(filesCall))) {
			return null;
		}

		return extractFirstStringArgument(filesCall);
	}

	private static @Nullable String extractFirstStringArgument(KtCallExpression call) {

		List<? extends ValueArgument> args = call.getValueArguments();
		if (args.isEmpty()) {
			return null;
		}

		return extractStringExpression(args.get(0).getArgumentExpression());
	}

	private static @Nullable String extractStringExpression(@Nullable KtExpression expression) {

		if (!(expression instanceof KtStringTemplateExpression st)) {
			return null;
		}

		PsiElement[] children = st.getChildren();
		if (children.length != 1) {
			return null;
		}

		return children[0].getText();
	}



}
