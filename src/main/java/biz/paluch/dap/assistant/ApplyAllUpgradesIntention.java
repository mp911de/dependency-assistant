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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.support.AvailableUpgrades;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpgradeSuggestion;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

/**
 * Intention that applies all dependency upgrades of one {@link UpgradeStrategy}
 * within the current file in a single batch.
 *
 * <p>Counterpart to the platform's {@code FixAllHighlightingProblems} option
 * for {@link UpdateDependencyVersionQuickFix}: instead of scanning daemon
 * highlights (an implementation-only API), the intention recomputes the upgrade
 * suggestions the annotator would offer, keeps the suggestion matching this
 * intention's strategy per declaration, and applies each update at its version
 * literal through
 * {@link ProjectDependencyContext#applyUpdate(PsiElement, DependencyUpdate)}.
 *
 * <p>Preview gathers the updates from the physical file and replays them onto
 * the non-physical preview copy, rendering a whole-file diff.
 *
 * @author Mark Paluch
 * @see UpdateDependencyVersionQuickFix#getOptions()
 */
class ApplyAllUpgradesIntention implements IntentionAction, Iconable {

	private final ProjectDependencyContext dependencyContext;

	private final UpgradeStrategy strategy;

	private final Icon icon;

	ApplyAllUpgradesIntention(ProjectDependencyContext dependencyContext, UpgradeStrategy strategy, Icon icon) {
		this.dependencyContext = dependencyContext;
		this.strategy = strategy;
		this.icon = icon;
	}

	@Override
	public @IntentionName String getText() {
		return MessageBundle.message("UpgradeDependencyAction.fix-all", MessageBundle.displayName(strategy));
	}

	@Override
	public @IntentionFamilyName String getFamilyName() {
		return MessageBundle.message("UpgradeDependencyAction.fix-all.family");
	}

	@Override
	public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
		return dependencyContext.isAvailable();
	}

	@Override
	public boolean startInWriteAction() {
		return true;
	}

	@Override
	public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
		applyUpdates(collectUpdates(psiFile));
	}

	@Override
	public IntentionPreviewInfo generatePreview(Project project, Editor editor, PsiFile psiFile) {

		Map<PsiElement, DependencyUpdate> updates = collectUpdates(psiFile.getOriginalFile());
		if (updates.isEmpty()) {
			return IntentionPreviewInfo.EMPTY;
		}

		Map<PsiElement, DependencyUpdate> updatesInCopy = new LinkedHashMap<>();
		updates.forEach((versionLiteral, update) -> updatesInCopy
				.put(PsiTreeUtil.findSameElementInCopy(versionLiteral, psiFile), update));

		applyUpdates(updatesInCopy);
		return IntentionPreviewInfo.DIFF;
	}

	/**
	 * Apply each update at its version literal, bottom-up to keep untouched anchors
	 * stable while earlier replacements change the document.
	 */
	private void applyUpdates(Map<PsiElement, DependencyUpdate> updates) {

		List<PsiElement> versionLiterals = new ArrayList<>(updates.keySet());
		for (PsiElement versionLiteral : versionLiterals.reversed()) {
			dependencyContext.applyUpdate(versionLiteral, updates.get(versionLiteral));
		}
	}

	/**
	 * Collect one {@link DependencyUpdate} per version literal in {@code file}
	 * whose upgrade suggestions contain a rule-enabled suggestion for
	 * {@link #strategy}. Declarations resolving to other files are skipped.
	 */
	private Map<PsiElement, DependencyUpdate> collectUpdates(PsiFile file) {

		Map<PsiElement, DependencyUpdate> updates = new LinkedHashMap<>();
		DependencyfileService ruleService = DependencyfileService.getInstance(file.getProject());
		VirtualFile virtualFile = file.getVirtualFile();

		file.accept(new PsiRecursiveElementWalkingVisitor() {

			@Override
			public void visitElement(PsiElement element) {

				super.visitElement(element);

				AvailableUpgrades upgrades = UpgradeSuggestions.suggest(dependencyContext, element);
				if (!upgrades.isPresent()) {
					return;
				}

				DependencyRule rule = ruleService.resolve(upgrades.getArtifactDeclaration().getArtifactId(),
						file.getProject(), virtualFile, dependencyContext.getProjectVersion());
				UpgradeSuggestion suggestion = upgrades.filterSuggestions(rule::isEnabled).getUpgrades().get(strategy);
				if (suggestion == null) {
					return;
				}

				PsiElement versionLiteral = suggestion.getArtifactDeclaration().getVersionLiteral();
				if (versionLiteral == null || !file.equals(versionLiteral.getContainingFile())) {
					return;
				}

				updates.putIfAbsent(versionLiteral, suggestion.toDependencyUpdate());
			}

		});

		return updates;
	}

	@Override
	public Icon getIcon(int flags) {
		return this.icon;
	}

}
