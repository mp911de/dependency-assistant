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
import java.util.Map;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Parses {@code settings.gradle} to discover version-catalog declarations in a
 * {@code dependencyResolutionManagement { versionCatalogs { ... } }} block.
 *
 * <p>Only {@code from(files("path/to/catalog.toml"))} forms are handled. Other
 * {@code from} forms, such as {@code from(uri(...))}, are skipped.
 *
 * @author Mark Paluch
 */
class GroovyDslSettingsParser {

	/**
	 * Parse the given {@code settings.gradle} file into a catalog registry.
	 *
	 * <p>The configured default alias is always registered. If it has no explicit
	 * catalog declaration, it uses {@code gradle/libs.versions.toml}.
	 * @param file the Groovy DSL settings file to parse.
	 * @return the configured catalogs and default alias.
	 */
	public static VersionCatalogRegistry parseRegistry(PsiFile file) {

		Map<String, String> catalogs = new LinkedHashMap<>();
		String alias = TomlParser.LIBS;

		JBIterable<GrMethodCall> managementCalls = SyntaxTraverser.psiTraverser(file)
				.expand(it -> !(it instanceof GrMethodCall))
				.filter(GrMethodCall.class)
				.filter(it -> "dependencyResolutionManagement".equals(GroovyDslUtils.getGroovyMethodName(it)));

		for (GrMethodCall managementCall : managementCalls) {
			for (GrClosableBlock closure : managementCall.getClosureArguments()) {
				for (PsiElement statement : closure.getChildren()) {

					if (statement instanceof GrMethodCall catalogsCall
							&& "versionCatalogs".equals(GroovyDslUtils.getGroovyMethodName(catalogsCall))) {
						parseVersionCatalogsBlock(catalogsCall, catalogs);
					}

					if (statement instanceof GrAssignmentExpression assign && !assign.isOperatorAssignment()
							&& "defaultLibrariesExtensionName".equals(GroovyDslUtils.getText(assign.getLValue()))
							&& assign.getRValue() instanceof GrLiteral literal) {
						alias = GroovyDslUtils.getRequiredText(literal);
					}
				}
			}
		}

		if (!catalogs.containsKey(alias)) {
			catalogs.put(alias, GradleUtils.DEFAULT_TOML_LOCATION);
		}

		return new VersionCatalogRegistry(catalogs, alias);
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
			if (filesArgs.length > 0 && filesArgs[0] instanceof GrLiteral literal) {
				return GroovyDslUtils.getText(literal);
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
