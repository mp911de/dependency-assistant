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

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.severity.DependencyAssistantSeverities;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.AvailableUpgrades;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpgradeAvailable;
import biz.paluch.dap.support.UpgradeSuggestion;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;

/**
 * Annotator that marks outdated dependency versions in supported build files.
 *
 * @author Mark Paluch
 */
public class NewerVersionAnnotator implements Annotator {

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

		IntentionAction action = UpgradeDependenciesIntention.INSTANCE;
		UpgradeSuggestion bestOption = upgrades.getUpgradeSuggestion();
		String message = bestOption.getMessage();
		ArtifactDeclaration declaration = upgrades.getArtifactDeclaration();

		if (!declaration.isVersionDefinedInSameFile()) {
			PsiElement versionLiteral = declaration.getVersionLiteral();
			if (versionLiteral != null && versionLiteral.getContainingFile() != null) {
				VirtualFile virtualFile = versionLiteral.getContainingFile().getVirtualFile();
				if (virtualFile != null) {

					message = MessageBundle.message("gutter.declaration.file", virtualFile.getName())
							+ System.lineSeparator()
							+ message;
					action = null;
				}
			}
		}

		AnnotationBuilder builder = holder
				.newAnnotation(DependencyAssistantSeverities.UPGRADE_AVAILABLE,
						declaration.getArtifactId() + ": " + message)
				.problemGroup(UpgradeAvailable.problemGroup())
				.range(context.getInterfaceAssistant().getHighlightRange(element))
				.textAttributes(DependencyAssistantSeverities.UPGRADE_AVAILABLE_KEY);

		if (action != null) {

			builder = builder.newFix(new UpdateDependencyAction(element, context, bestOption)).registerFix();

			for (UpgradeSuggestion suggestion : upgrades.getUpgrades().values()) {
				if (suggestion == bestOption) {
					continue;
				}
				builder = builder.newFix(new UpdateDependencyAction(element, context, suggestion)).registerFix();
			}
		}

		builder.create();
	}

}
