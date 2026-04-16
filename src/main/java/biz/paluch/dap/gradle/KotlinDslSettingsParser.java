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
import java.util.concurrent.atomic.AtomicReference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.kotlin.psi.KtBinaryExpression;
import org.jetbrains.kotlin.psi.KtBlockExpression;
import org.jetbrains.kotlin.psi.KtCallExpression;
import org.jetbrains.kotlin.psi.KtExpression;
import org.jetbrains.kotlin.psi.KtLambdaArgument;
import org.jetbrains.kotlin.psi.KtLambdaExpression;
import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.kotlin.psi.ValueArgument;
import org.jspecify.annotations.Nullable;

/**
 * Parses {@code settings.gradle.kts} to discover version-catalog declarations
 * in a {@code dependencyResolutionManagement { versionCatalogs { … } }} block.
 *
 * <p>Only {@code from(files("path/to/catalog.toml"))} forms are handled; other
 * {@code from} forms (e.g. {@code from(uri(…))}) are silently skipped.
 *
 * @author Mark Paluch
 */
class KotlinDslSettingsParser {

	/**
	 * Parses the given {@code settings.gradle.kts} file and returns a
	 * {@link VersionCatalogRegistry}. Falls back to
	 * {@link VersionCatalogRegistry#defaults()} if no
	 * {@code dependencyResolutionManagement} block is found.
	 */
	static VersionCatalogRegistry parseRegistry(PsiFile file) {

		Map<String, String> catalogs = new LinkedHashMap<>();
		AtomicReference<String> defaultAlias = new AtomicReference<>(TomlParser.LIBS);

		SyntaxTraverser.psiTraverser(file).filter(KtCallExpression.class).forEach(call -> {

			if (!"dependencyResolutionManagement".equals(KotlinDslUtils.getKotlinCallName(call))) {
				return;
			}

			KtLambdaExpression outerLambda = getLambdaArgument(call);
			if (outerLambda == null) {
				return;
			}

			KtBlockExpression body = outerLambda.getBodyExpression();
			if (body == null) {
				return;
			}

			for (KtExpression stmt : body.getStatements()) {
				if (stmt instanceof KtCallExpression catalogsCall
						&& "versionCatalogs".equals(KotlinDslUtils.getKotlinCallName(catalogsCall))) {
					parseVersionCatalogsBlock(catalogsCall, catalogs);
				}
				if (stmt instanceof KtBinaryExpression binary) {
					String lhs = KotlinDslUtils.getText(binary.getLeft());
					if ("defaultLibrariesExtensionName".equals(lhs) && binary.getRight() instanceof KtExpression) {
						defaultAlias.set(KotlinDslUtils.getRequiredText(binary.getRight()));
					}
				}
			}
		});

		String alias = defaultAlias.get();

		if (!catalogs.containsKey(TomlParser.LIBS)) {
			catalogs.put(alias, GradleUtils.DEFAULT_TOML_LOCATION);
		}

		return new VersionCatalogRegistry(Map.copyOf(catalogs), alias);
	}

	private static void parseVersionCatalogsBlock(KtCallExpression catalogsCall, Map<String, String> catalogs) {

		KtLambdaExpression lambda = getLambdaArgument(catalogsCall);
		if (lambda == null) {
			return;
		}

		for (KtExpression stmt : lambda.getBodyExpression().getStatements()) {
			if (!(stmt instanceof KtCallExpression createCall)) {
				continue;
			}

			if (!"create".equals(KotlinDslUtils.getKotlinCallName(createCall))) {
				continue;
			}

			String alias = extractFirstStringArgument(createCall);
			if (alias == null) {
				continue;
			}

			String path = extractFromFilesPath(createCall);
			if (path != null) {
				catalogs.put(alias, path);
			}
		}
	}

	private static @Nullable String extractFromFilesPath(KtCallExpression createCall) {

		KtLambdaExpression lambda = getLambdaArgument(createCall);
		if (lambda == null) {
			return null;
		}

		for (KtExpression stmt : lambda.getBodyExpression().getStatements()) {
			if (stmt instanceof KtCallExpression fromCall
					&& "from".equals(KotlinDslUtils.getKotlinCallName(fromCall))) {
				return extractFilesArgument(fromCall);
			}
		}

		return null;
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

	private static @Nullable KtLambdaExpression getLambdaArgument(KtCallExpression call) {

		KtLambdaArgument lambdaArg = call.getLambdaArguments().isEmpty() ? null : call.getLambdaArguments().get(0);
		if (lambdaArg == null) {
			return null;
		}

		KtExpression expr = lambdaArg.getArgumentExpression();
		return expr instanceof KtLambdaExpression lambda ? lambda : null;
	}

}
