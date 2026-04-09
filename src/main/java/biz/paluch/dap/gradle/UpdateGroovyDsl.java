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

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrAssignmentExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * Updates a Gradle build file with a new version.
 *
 * @author Mark Paluch
 */
class UpdateGroovyDsl {

	private final GroovyPsiElementFactory factory;

	UpdateGroovyDsl(Project project) {
		this.factory = GroovyPsiElementFactory.getInstance(project);
	}

	/**
	 * Update {@code ext.propertyKey = 'newVersion'} or {@code set('propertyKey', 'newVersion')}.
	 */
	public void updateExtProperty(PsiFile file, String propertyKey, String newVersion) {

		boolean[] found = { false };

		file.accept(new PsiRecursiveElementVisitor() {
			@Override
			public void visitElement(PsiElement element) {
				if (found[0]) {
					return;
				}
				super.visitElement(element);
				// Assignment form: key = 'value' or ext.key = 'value'
				if (element instanceof GrAssignmentExpression assign && !assign.isOperatorAssignment()) {
					String key = extractExtPropertyKey(assign.getLValue());
					if (propertyKey.equals(key) && assign.getRValue() instanceof GrLiteral lit) {
						replaceGroovyLiteralValue(lit, newVersion);
						found[0] = true;
					}
				}
				// set() call form: set('key', 'value') inside ext {} closure
				if (!found[0] && element instanceof GrMethodCall call
						&& "set".equals(GroovyDslUtils.getGroovyMethodName(call))) {
					PsiElement[] args = call.getArgumentList().getAllArguments();
					if (args.length >= 2 && args[0] instanceof GrLiteral keyLit && propertyKey.equals(keyLit.getValue())
							&& args[1] instanceof GrLiteral valLit) {
						replaceGroovyLiteralValue(valLit, newVersion);
						found[0] = true;
					}
				}
			}
		});
	}

	public void applyUpdate(PsiFile file, ArtifactId id, String newVersion) {

		if (isPlugin(id)) {

			String pluginId = id.groupId();

			file.accept(new PsiRecursiveElementVisitor() {
				@Override
				public void visitElement(PsiElement element) {
					super.visitElement(element);
					if (!(element instanceof GrMethodCall call)) {
						return;
					}
					if (!GradleUtils.isPlugin(GroovyDslUtils.getGroovyMethodName(call))) {
						return;
					}
					if (!GroovyDslUtils.isInsidePluginsBlock(call)) {
						return;
					}

					PsiElement[] ownArgs = call.getArgumentList().getAllArguments();
					String foundPlugin = GroovyDslUtils.firstLiteralString(ownArgs);
					if (!pluginId.equals(foundPlugin)) {
						return;
					}

					// Try to find and replace the version literal.
					GrLiteral versionLit = findVersionLiteralAfterRef(ownArgs);
					if (versionLit == null) {
						PsiElement parent = call.getParent();
						if (parent instanceof GrReferenceExpression versionRef && "version".equals(versionRef.getReferenceName())
								&& versionRef.getParent() instanceof GrMethodCall outerApp) {
							PsiElement[] outerArgs = outerApp.getArgumentList().getAllArguments();
							for (PsiElement outerArg : outerArgs) {
								if (outerArg instanceof GrLiteral lit) {
									versionLit = lit;
									break;
								}
							}
						}
						// Fallback: direct GrMethodCall parent with version_ref + literal pattern.
						else if (parent instanceof GrMethodCall outerApp) {
							versionLit = findVersionLiteralAfterRef(outerApp.getArgumentList().getAllArguments());
						}
					}
					if (versionLit != null) {
						replaceGroovyLiteralValue(versionLit, newVersion);
					}
				}
			});
		} else {
			String groupId = id.groupId();
			String artifactId = id.artifactId();

			file.accept(new PsiRecursiveElementVisitor() {
				@Override
				public void visitElement(PsiElement element) {
					super.visitElement(element);
					if (element instanceof GrLiteral lit) {
						String gavText = GroovyDslUtils.toString(lit);
						String[] parts = gavText.split(":");
						if (parts.length >= 3 && groupId.equals(parts[0].trim()) && artifactId.equals(parts[1].trim())
								&& !parts[2].contains("$")) {
							String newGav = GradleUtils.updateGavVersion(gavText, newVersion);
							if (newGav != null) {
								replaceGroovyLiteralValue(lit, newGav);
							}
						}
					}
				}
			});
		}
	}

	private static boolean isPlugin(ArtifactId id) {
		return id.artifactId().equals(id.groupId());
	}

	/**
	 * Replaces the string content of a Groovy literal while preserving its quote style. The literal is replaced in-place
	 * using a new PSI node created by {@link GroovyPsiElementFactory}.
	 */
	void replaceGroovyLiteralValue(GrLiteral lit, String newContent) {

		String text = lit.getText();

		if (text == null || text.length() < 2) {
			return;
		}

		char quote = text.charAt(0);

		// Use the same quote character (single or double) that was originally used.
		String newLiteralText = quote + newContent + quote;
		GrExpression newLiteral = factory.createExpressionFromText(newLiteralText);
		lit.replace(newLiteral);
	}

	/**
	 * Extracts the property key from an {@code ext} assignment LHS, or {@code null} if the expression is not an ext
	 * property assignment.
	 * <p>
	 * Recognises:
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

	/**
	 * Finds the {@link GrLiteral} that represents the version in a {@code version 'x.y.z'} suffix. Returns {@code null}
	 * if not present.
	 */
	private static @Nullable GrLiteral findVersionLiteralAfterRef(PsiElement[] args) {

		boolean sawVersion = false;

		for (PsiElement arg : args) {
			if (!sawVersion && arg instanceof GrReferenceExpression ref && "version".equals(ref.getText())) {
				sawVersion = true;
			} else if (sawVersion && arg instanceof GrLiteral lit) {
				return lit;
			}
		}

		return null;
	}

}
