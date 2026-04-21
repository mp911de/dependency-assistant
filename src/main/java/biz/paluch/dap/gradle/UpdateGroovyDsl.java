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
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.PsiVisitors;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Updates a Gradle build file with a new version.
 *
 * @author Mark Paluch
 */
class UpdateGroovyDsl {

	private final GroovyVersionSiteLocator siteLocator;

	UpdateGroovyDsl(PropertyResolver propertyResolver) {
		this.siteLocator = new GroovyVersionSiteLocator(propertyResolver);
	}

	/**
	 * Update {@code ext.propertyKey = 'newVersion'} or
	 * {@code set('propertyKey', 'newVersion')}.
	 */
	void updateExtProperty(PsiFile file, String propertyKey, String newVersion) {

		file.accept(PsiVisitors.visitTreeUntil(PsiElement.class, element -> {

			if (element instanceof GrVariableDeclaration decl && decl.getParent() instanceof GroovyFile) {
				for (GrVariable variable : decl.getVariables()) {
					if (propertyKey.equals(variable.getName())
							&& variable.getInitializerGroovy() instanceof GrLiteral lit) {
						GroovyDslUtils.updateText(lit, newVersion);
						return true;
					}
				}
			}

			if (element instanceof GrAssignmentExpression assign && !assign.isOperatorAssignment()) {
				String key = extractExtPropertyKey(assign.getLValue());
				if (propertyKey.equals(key) && assign.getRValue() instanceof GrLiteral lit) {
					GroovyDslUtils.updateText(lit, newVersion);
					return true;
				}
			}

			if (element instanceof GrMethodCall call
					&& "set".equals(GroovyDslUtils.getGroovyMethodName(call))) {
				PsiElement[] args = call.getArgumentList().getAllArguments();
				if (args.length >= 2 && args[0] instanceof GrLiteral keyLit && propertyKey.equals(keyLit.getValue())
						&& args[1] instanceof GrLiteral valLit) {
					GroovyDslUtils.updateText(valLit, newVersion);
					return true;
				}
			}

			return false;
		}));
	}

	public void updateDeclaration(PsiFile file, ArtifactId id, String newVersion) {
		file.accept(PsiVisitors.visitTreeUntil(GrMethodCall.class, call -> {

			GradleDeclarationSite site = siteLocator.locateDeclaration(call);
			if (site == null || !site.isUpdateable() || !site.matches(id)) {
				return false;
			}

			return site.updateVersion(newVersion);
		}));
	}

	/**
	 * Extracts the property key from an {@code ext} assignment LHS, or {@code null}
	 * if the expression is not an ext property assignment.
	 * <p>Recognises:
	 * <ul>
	 * <li>{@code springVersion} (plain ref inside an {@code ext { }} closure)</li>
	 * <li>{@code ext.springVersion} (dot-qualified assignment)</li>
	 * </ul>
	 */
	static @Nullable String extractExtPropertyKey(GrExpression lhs) {

		if (!(lhs instanceof GrReferenceExpression ref)) {
			return null;
		}

		GrExpression qualifier = ref.getQualifierExpression();

		// Plain name (inside ext {} closure)
		if (qualifier == null) {
			return ref.getReferenceName();
		}

		// ext.name
		if (qualifier instanceof GrReferenceExpression qualRef && "ext".equals(qualRef.getReferenceName())) {
			return ref.getReferenceName();
		}
		return null;
	}

}
