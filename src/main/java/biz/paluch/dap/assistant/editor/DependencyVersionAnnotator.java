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

package biz.paluch.dap.assistant.editor;

import java.util.HashSet;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.severity.DependencyAssistantSeverities;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.upgrade.UpgradeAvailable;
import biz.paluch.dap.upgrade.UpgradeSuggestion;
import biz.paluch.dap.upgrade.UpgradeSuggestionGroup;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInsight.daemon.HighlightDisplayKey;
import com.intellij.codeInsight.intention.PriorityAction;
import com.intellij.lang.annotation.AnnotationBuilder;
import com.intellij.lang.annotation.AnnotationHolder;
import com.intellij.lang.annotation.Annotator;
import com.intellij.lang.annotation.HighlightSeverity;
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

		DependencyUpgradeContext context = DependencyUpgradeContext.from(element);

		if (context.isAbsent()) {
			return;
		}

		UpgradeSuggestions suggestions = context.getSuggestions();
		ArtifactDeclaration declaration = context.getArtifactReference().getDeclaration();

		Vulnerabilities vulnerabilities = context.getCurrentVulnerabilities();
		VulnerabilitiesPresentation vulnerability = null;
		if (vulnerabilities.isVulnerable()) {
			vulnerability = VulnerabilitiesPresentation.of(vulnerabilities);
		}

		if (suggestions.isEmpty()) {

			if (vulnerability != null) {

				AnnotationBuilder builder = holder
						.newAnnotation(HighlightSeverity.WARNING,
								declaration.getArtifactId() + ": " + vulnerability.getText())
						.range(context.getHighlightRange(element));

				builder.textAttributes(vulnerability.getTextAttributes());
				builder.create();
			}

			return;
		}

		UpgradeSuggestion bestOption = suggestions.getSuggestion();
		String message = vulnerability != null ? vulnerability.getText()
				: context.hasRule() ? bestOption.getSuggestionMessage() : bestOption.getMessage();

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
				.range(context.getHighlightRange(element));

		if (vulnerability != null) {
			builder.textAttributes(vulnerability.getTextAttributes());
		} else if (context.hasRule()) {
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

			PriorityAction.Priority priority = PriorityAction.Priority.HIGH;
			for (UpgradeSuggestion suggestion : suggestions.getSuggestions()) {
				if (seen.contains(suggestion.getVersion())) {
					continue;
				}

				DependencyUpdate update = DependencyUpdate.from(context.getArtifactReference(), suggestion);
				UpdateDependencyVersionQuickFix fix = new UpdateDependencyVersionQuickFix(versionLiteral,
						suggestion.getStrategy(), context.getReferenceContext(), update);
				builder = builder.newFix(fix).key(key).registerFix();
				seen.add(suggestion.getVersion());
				if (!suggestion.getStrategy().isRemediation()
						&& priority.ordinal() < PriorityAction.Priority.LOW.ordinal()) {
					priority = PriorityAction.Priority.values()[priority.ordinal() + 1];
				}
			}
		}

		builder.create();
	}

}
