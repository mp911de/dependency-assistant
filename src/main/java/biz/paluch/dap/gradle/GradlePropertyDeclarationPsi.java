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

import biz.paluch.dap.state.ProjectProperty;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.Property;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiRecursiveElementVisitor;

/**
 * Locates PSI for Gradle property declarations referenced by name (e.g. {@code gradle.properties}, {@code extra["…"]}
 * in Groovy/Kotlin build scripts).
 *
 * @author Mark Paluch
 */
final class GradlePropertyDeclarationPsi {

	private final Project project;

	private final @Nullable ProjectState projectState;

	GradlePropertyDeclarationPsi(Project project, @Nullable ProjectState projectState) {
		this.project = project;
		this.projectState = projectState;
	}

	@Nullable
	PsiElement findPropertyValuePsi(String propertyName) {

		if (projectState == null) {
			return null;
		}

		ProjectProperty pp = projectState.findProjectProperty(propertyName, Property::isDeclared);
		if (pp == null) {
			return null;
		}

		String path = pp.id().buildFile();
		if (!StringUtils.hasText(path)) {
			return null;
		}

		VirtualFile vf = LocalFileSystem.getInstance().findFileByPath(path);
		if (vf == null) {
			return null;
		}

		PsiFile psiFile = PsiManager.getInstance(project).findFile(vf);
		if (psiFile == null) {
			return null;
		}

		if (psiFile instanceof PropertiesFile props) {
			IProperty ip = props.findPropertyByKey(propertyName);
			if (ip != null) {
				return ip.getPsiElement().getLastChild();
			}
			return null;
		}

		if (GradleUtils.isGroovyDsl(vf)) {
			PsiElement[] found = { null };
			psiFile.accept(new PsiRecursiveElementVisitor() {
				@Override
				public void visitElement(PsiElement e) {
					super.visitElement(e);
					if (found[0] != null) {
						return;
					}
					if (e instanceof GrLiteral lit) {
						GroovyDslUtils.PropertyVersionLocation pl = GroovyDslUtils.findGroovyExtPropertyVersionElement(lit);
						if (pl != null && propertyName.equals(pl.propertyKey())) {
							found[0] = lit;
						}
					}
				}
			});
			return found[0];
		}

		if (GradleUtils.isKotlinDsl(vf) && GradleUtils.KOTLIN_AVAILABLE) {
			PsiElement kotlinExtra = KotlinDslExtraSupport.findExtraPropertyValuePsi(psiFile, propertyName);
			if (kotlinExtra != null) {
				return kotlinExtra;
			}
		}

		return null;
	}

}
