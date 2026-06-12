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

package biz.paluch.dap.assistant;

import java.util.HashSet;
import java.util.Set;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.RuleService;
import biz.paluch.dap.severity.DependencyAssistantSeverities;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.AvailableUpgrades;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpgradeAvailable;
import biz.paluch.dap.support.UpgradeSuggestion;
import biz.paluch.dap.support.UpgradeSuggestionGroup;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptorBase;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Annotator that marks outdated dependency versions in supported build files.
 *
 * @author Mark Paluch
 */
public class DependencyVersionAnnotator implements Annotator {

	@Override
	public void annotate(PsiElement element, AnnotationHolder holder) {

		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(element);
		if (context.isAbsent()) {
			return;
		}

		AvailableUpgrades upgrades = UpgradeSuggestions.suggest(context, element);
		if (!upgrades.isPresent()) {
			return;
		}

		VirtualFile containingFile = element.getContainingFile().getVirtualFile();
		RuleService ruleService = RuleService.getInstance(element.getProject());
		DependencyRule rule = ruleService.resolve(upgrades.getArtifactDeclaration()
				.getArtifactId(), element.getProject(), containingFile, context.getProjectVersion());
		upgrades = upgrades.filterSuggestions(rule::isEnabled);

		if (!upgrades.isPresent()) {
			return;
		}

		UpgradeSuggestion bestOption = upgrades.getUpgradeSuggestion();
		String message = rule.isPresent() ? bestOption.getSuggestionMessage() : bestOption.getMessage();
		ArtifactDeclaration declaration = upgrades.getArtifactDeclaration();
		PsiElement versionLiteral = declaration.getVersionLiteral();
		boolean sameFileDeclaration = true;

		if (versionLiteral == null) {
			return;
		}

		PsiFile versionLiteralFile = versionLiteral.getContainingFile();
		if (!element.getContainingFile().equals(versionLiteralFile)) {

			sameFileDeclaration = false;
			VirtualFile virtualFile = versionLiteralFile.getVirtualFile();
			if (virtualFile != null) {

				message = MessageBundle.message("gutter.declaration.file", virtualFile.getName())
						+ System.lineSeparator()
						+ message;
			}
		}

		AnnotationBuilder builder = holder
				.newAnnotation(DependencyAssistantSeverities.UPGRADE_AVAILABLE,
						declaration.getArtifactId() + ": " + message)
				.range(context.getInterfaceAssistant().getHighlightRange(element));

		if (rule.isPresent()) {
			builder.problemGroup(UpgradeSuggestionGroup.problemGroup())
					.textAttributes(DependencyAssistantSeverities.UPGRADE_SUGGESTION_KEY);
		} else {
			builder.problemGroup(UpgradeAvailable.problemGroup())
					.textAttributes(DependencyAssistantSeverities.UPGRADE_AVAILABLE_KEY);
		}

		HighlightDisplayKey key = HighlightDisplayKey.findOrRegister(MessageBundle.message("plugin.name"),
				MessageBundle.message("plugin.name"));
		if (sameFileDeclaration) {

			Set<ArtifactVersion> seen = new HashSet<>();

			UpdateDependencyVersionQuickFix fix = new UpdateDependencyVersionQuickFix(versionLiteral, context,
					bestOption);
			ProblemDescriptorBase d = new ProblemDescriptorBase(versionLiteral, versionLiteral, message,
					new LocalQuickFix[] {fix}, ProblemHighlightType.INFORMATION, false, null, true, true, null);
			builder = builder.newFix(fix).key(key).registerFix();
			seen.add(bestOption.getRelease().getVersion());

			for (UpgradeSuggestion suggestion : upgrades.getUpgrades().values()) {
				if (suggestion == bestOption || seen.contains(suggestion.getRelease().getVersion())) {
					continue;
				}
				builder = builder.newFix(new UpdateDependencyVersionQuickFix(versionLiteral, context, suggestion))
						.key(key)
						.registerFix();
				seen.add(bestOption.getRelease().getVersion());
			}
		}

		builder.create();
	}

}
