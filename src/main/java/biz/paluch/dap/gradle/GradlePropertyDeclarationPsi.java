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
import biz.paluch.dap.support.PsiPropertyValueElement;
import biz.paluch.dap.util.PsiVisitors;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Locates PSI for Gradle property declarations referenced by name (e.g.
 * {@code gradle.properties}, {@code extra["…"]} in Groovy/Kotlin build
 * scripts).
 *
 * @author Mark Paluch
 */
class GradlePropertyDeclarationPsi {

	private final Project project;

	private final @Nullable ProjectState projectState;

	GradlePropertyDeclarationPsi(Project project, @Nullable ProjectState projectState) {
		this.project = project;
		this.projectState = projectState;
	}

	@Nullable
	PsiPropertyValueElement findPropertyValueElement(String propertyName) {

		if (projectState == null) {
			return null;
		}

		ProjectProperty pp = projectState.findProjectProperty(propertyName, Property::isDeclared);
		if (pp == null) {
			return null;
		}

		String path = pp.id().buildFile();
		if (StringUtils.isEmpty(path)) {
			return null;
		}

		VirtualFile virtualFile = LocalFileSystem.getInstance().findFileByPath(path);
		if (virtualFile == null) {
			return null;
		}

		PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
		if (psiFile == null) {
			return null;
		}

		return GradlePropertyResolver.forFile(psiFile).getElement(propertyName);
		/*
		 * if (psiFile instanceof PropertiesFile props) { IProperty ip =
		 * props.findPropertyByKey(propertyName); if (ip != null) { PsiElement element =
		 * ip.getPsiElement().getLastChild(); return new
		 * PsiPropertyValueElement(element, propertyName, element.getText()); } return
		 * null; }
		 * 
		 * if (GradleUtils.isGroovyDsl(virtualFile)) { return
		 * findExPropertyLocation(propertyName, psiFile); }
		 * 
		 * if (GradleUtils.isKotlinDsl(virtualFile) && GradleUtils.KOTLIN_AVAILABLE) {
		 * return KotlinDslExtraParser.findExtraPropertyLocation(psiFile, propertyName);
		 * }
		 * 
		 * return null;
		 */
	}

	private @Nullable PsiPropertyValueElement findExPropertyLocation(String propertyName, PsiFile psiFile) {

		@Nullable
		PsiElement[] found = {null};
		psiFile.accept(PsiVisitors.visitTreeUntil(GrLiteral.class, literal -> {

			PsiPropertyValueElement pl = GroovyDslUtils.findGroovyExtPropertyVersionElement(literal);
			if (pl != null && propertyName.equals(pl.propertyKey())) {
				found[0] = literal;
				return true;
			}

			return false;
		}));

		PsiElement psiElement = found[0];
		if (psiElement == null) {
			return null;
		}

		return new PsiPropertyValueElement(found[0], propertyName,
				psiElement instanceof GrLiteral literal ? GroovyDslUtils.toString(literal) : psiElement.getText());
	}

}
