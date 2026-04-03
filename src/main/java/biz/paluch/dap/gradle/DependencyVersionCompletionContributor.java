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

import biz.paluch.dap.SuggestionProviderUtil;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.state.DependencyAssistantService;

import java.util.Comparator;
import java.util.List;

import org.jetbrains.kotlin.psi.KtStringTemplateExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;

import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Completion contributor that suggests available versions for the version segment of a Gradle dependency string literal
 * in Groovy DSL or Kotlin DSL files.
 *
 * @author Mark Paluch
 */
public class DependencyVersionCompletionContributor extends CompletionContributor {

	@Override
	public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {

		PsiFile file = parameters.getOriginalFile();
		if (!GradleUtils.isGradleFile(file.getVirtualFile())) {
			return;
		}

		PsiElement element = parameters.getOriginalPosition();
		if (element == null) {
			element = parameters.getPosition();
		}

		VersionUpgradeLookupService lookupService = new VersionUpgradeLookupService(file.getProject(), file);
		Dependency dependency = lookupService.findDependency(element);
		if (dependency == null) {
			return;
		}

		Project project = file.getProject();
		DependencyAssistantService service = DependencyAssistantService.getInstance(project);

		// Show all cached versions on a second invocation (Ctrl+Space twice)
		CompletionResultSet versionsResult = parameters.getInvocationCount() > 1 ? result.withPrefixMatcher("") : result;

		List<ArtifactRelease> options = SuggestionProviderUtil.findOptions(dependency.getArtifactId(), service.getCache());
		if (options.isEmpty()) {
			return;
		}
		options.sort(Comparator.reverseOrder());

		SuggestionProviderUtil.addSuggestions(options, versionsResult, id -> "", null);
	}

	private static GroovyDslUtils.VersionLocation resolveVersionLocation(PsiFile file, PsiElement element) {

		if (element instanceof GrLiteral || (element.getParent() instanceof GrLiteral)) {
			PsiElement target = element instanceof GrLiteral ? element : element.getParent();
			return GroovyDslUtils.findGroovyVersionElement(target);
		}

		if (GradleUtils.KOTLIN_AVAILABLE) {

			if (element instanceof KtStringTemplateExpression || element.getParent() instanceof KtStringTemplateExpression) {
				PsiElement target = element instanceof KtStringTemplateExpression ? element : element.getParent();
				return KotlinDslUtils.findKotlinVersionElement(target);
			}
		}

		return null;
	}

}
