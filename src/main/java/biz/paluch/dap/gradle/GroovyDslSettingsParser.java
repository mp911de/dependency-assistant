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
import java.util.concurrent.atomic.AtomicReference;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Parses {@code settings.gradle} to discover version-catalog declarations in a
 * {@code dependencyResolutionManagement { versionCatalogs { … } }} block.
 *
 * <p>Only {@code from(files("path/to/catalog.toml"))} forms are handled; other
 * {@code from} forms (e.g. {@code from(uri(…))}) are silently skipped.
 *
 * @author Mark Paluch
 */
class GroovyDslSettingsParser {

	/**
	 * Parses the given {@code settings.gradle} file and returns a
	 * {@link VersionCatalogRegistry}. Falls back to
	 * {@link VersionCatalogRegistry#defaults()} if no
	 * {@code dependencyResolutionManagement} block is found.
	 */
	public static VersionCatalogRegistry parseRegistry(PsiFile file) {

		Map<String, String> catalogs = new LinkedHashMap<>();
		AtomicReference<String> defaultAlias = new AtomicReference<>(TomlParser.LIBS);

		SyntaxTraverser.psiTraverser(file).filter(GrMethodCall.class)
				.filter(it -> "dependencyResolutionManagement".equals(GroovyDslUtils.getGroovyMethodName(it)))
				.flatMap(it -> JBIterable.of(it.getClosureArguments()))
				.flatMap(it -> it instanceof GrClosableBlock ? JBIterable.of(it.getChildren()) : JBIterable.empty())
				.filter(GroovyPsiElement.class)
				.forEach(child -> {

					if (child instanceof GrMethodCall catalogsCall
							&& "versionCatalogs".equals(GroovyDslUtils.getGroovyMethodName(catalogsCall))) {
						parseVersionCatalogsBlock(catalogsCall, catalogs);
					}

					if (child instanceof GrAssignmentExpression assign && !assign.isOperatorAssignment()) {
						String name = GroovyDslUtils.getText(assign.getLValue());
						if ("defaultLibrariesExtensionName".equals(name)
								&& assign.getRValue() instanceof GrLiteral literal) {
							defaultAlias.set(GroovyDslUtils.getRequiredText(literal));
						}
					}
				});

		String alias = defaultAlias.get();

		if (!catalogs.containsKey(TomlParser.LIBS)) {
			catalogs.put(alias, GradleUtils.DEFAULT_TOML_LOCATION);
		}

		return new VersionCatalogRegistry(Map.copyOf(catalogs), alias);
	}

	private static void parseVersionCatalogsBlock(GrMethodCall catalogsCall, Map<String, String> catalogs) {

		JBIterable.of(catalogsCall.getClosureArguments())
				.flatMap(it -> JBIterable.of(it.getChildren()))
				.filter(GrMethodCall.class)
				.forEach(aliasCall -> {

					GrExpression[] args = aliasCall.getExpressionArguments();
					String alias;
					if (args.length == 1) {
						alias = GroovyDslUtils.getText(args[0]);
					} else {
						alias = GroovyDslUtils.getGroovyMethodName(aliasCall);
					}
					String path = extractFromFilesPath(aliasCall);
					if (path != null) {
						catalogs.put(alias, path);
					}
				});
	}

	private static @Nullable String extractFromFilesPath(GrMethodCall aliasCall) {

		for (GrClosableBlock closure : aliasCall.getClosureArguments()) {
			for (PsiElement child : closure.getChildren()) {
				if (child instanceof GrMethodCall fromCall
						&& "from".equals(GroovyDslUtils.getGroovyMethodName(fromCall))) {
					return extractFilesArgument(fromCall);
				}
			}
		}

		return null;
	}

	private static @Nullable String extractFilesArgument(GrMethodCall fromCall) {

		for (PsiElement arg : fromCall.getArgumentList().getAllArguments()) {

			GrMethodCall filesCall = resolveFilesCall(arg);
			if (filesCall == null) {
				continue;
			}
			PsiElement[] filesArgs = filesCall.getArgumentList().getAllArguments();
			if (filesArgs.length > 0 && filesArgs[0] instanceof GrLiteral lit) {
				return GroovyDslUtils.getText(lit);
			}
		}

		return null;
	}

	private static @Nullable GrMethodCall resolveFilesCall(PsiElement arg) {

		if (arg instanceof GrMethodCall call && "files".equals(GroovyDslUtils.getGroovyMethodName(call))) {
			return call;
		}

		if (arg instanceof GrReferenceExpression ref) {
			PsiElement qualifier = ref.getQualifierExpression();
			if (qualifier instanceof GrMethodCall call && "files".equals(GroovyDslUtils.getGroovyMethodName(call))) {
				return call;
			}
		}
		return null;
	}

}
