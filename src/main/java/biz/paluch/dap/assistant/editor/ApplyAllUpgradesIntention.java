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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.assistant.DependencyUpgradeIcons;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeSuggestion;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx;
import com.intellij.codeInsight.daemon.impl.HighlightInfo;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionDelegate;
import com.intellij.codeInsight.intention.preview.IntentionPreviewInfo;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementWalkingVisitor;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Intention that applies all dependency upgrades of one {@link UpgradeStrategy}
 * within the current file in a single batch.
 *
 * <p>Counterpart to the platform's {@code FixAllHighlightingProblems} option
 * for {@link UpdateDependencyVersionQuickFix}: it first gathers registered
 * daemon fixes of the same strategy, falls back to recomputing the annotator's
 * upgrade contexts when no highlights are available, and applies each update at
 * its version literal through
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

	ApplyAllUpgradesIntention(ProjectDependencyContext dependencyContext, UpgradeStrategy strategy) {
		this.dependencyContext = dependencyContext;
		this.strategy = strategy;
		this.icon = DependencyUpgradeIcons.resolveIcon(strategy);
	}

	@Override
	public @IntentionName String getText() {
		return MessageBundle.message("UpgradeDependencyAction.fix-all", strategy.getDisplayName());
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
		applyUpdates(collectUpdates(project, psiFile));
	}

	@Override
	public IntentionPreviewInfo generatePreview(Project project, Editor editor, PsiFile psiFile) {

		Map<PsiElement, DependencyUpdate> updates = collectUpdates(project, psiFile.getOriginalFile());
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
		versionLiterals.sort((left, right) -> Integer.compare(right.getTextOffset(), left.getTextOffset()));

		for (PsiElement versionLiteral : versionLiterals) {
			dependencyContext.applyUpdate(versionLiteral, updates.get(versionLiteral));
		}
	}

	/**
	 * Collect one {@link DependencyUpdate} per version literal in {@code file} from
	 * the daemon highlights already registered by the annotator.
	 */
	private Map<PsiElement, DependencyUpdate> collectUpdates(Project project, PsiFile file) {

		Map<PsiElement, DependencyUpdate> updates = new LinkedHashMap<>();
		collectHighlightedUpdates(project, file, updates);
		if (updates.isEmpty()) {
			collectContextUpdates(file, updates);
		}

		return updates;
	}

	private void collectHighlightedUpdates(Project project, PsiFile file, Map<PsiElement, DependencyUpdate> updates) {

		Document document = file.getFileDocument();

		DaemonCodeAnalyzerEx.processHighlights(document, project, null, 0, document.getTextLength(), info -> {
			DependencyUpdateFix fix = findFix(info);
			if (fix == null) {
				return true;
			}

			PsiElement versionLiteral = fix.getStartElement();
			if (versionLiteral != null && file.equals(versionLiteral.getContainingFile())) {
				updates.put(versionLiteral, fix.getUpdate());
			}

			return true;
		});
	}

	private @Nullable DependencyUpdateFix findFix(HighlightInfo info) {

		return info.findRegisteredQuickFix((descriptor, range) -> {
			IntentionAction action = IntentionActionDelegate.unwrap(descriptor.getAction());
			if (action instanceof DependencyUpdateFix fix && fix.hasStrategy(this.strategy)) {
				return fix;
			}
			return null;
		});
	}

	/**
	 * Fallback in case no highlights are available.
	 * @param file the file to walk for dependency declarations.
	 * @param updates the map collecting one update per version literal, keyed by
	 * the version literal element.
	 */
	private void collectContextUpdates(PsiFile file, Map<PsiElement, DependencyUpdate> updates) {

		file.accept(new PsiRecursiveElementWalkingVisitor() {

			@Override
			public void visitElement(PsiElement element) {

				super.visitElement(element);

				ArtifactReferenceContext context = ArtifactReferenceContext.from(element);
				if (context.isAbsent()) {
					return;
				}

				ArtifactDeclaration declaration = context.getDeclaration();
				PsiElement versionLiteral = declaration.getVersionLiteral();
				if (versionLiteral == null || !file.equals(versionLiteral.getContainingFile())) {
					return;
				}

				for (UpgradeSuggestion suggestion : context.getSuggestions().getSuggestions()) {
					if (suggestion.getStrategy() == strategy) {
						DependencyUpdate update = DependencyUpdate.from(declaration, suggestion);
						updates.putIfAbsent(versionLiteral, update);
						return;
					}
				}
			}

		});
	}

	@Override
	public Icon getIcon(int flags) {
		return this.icon;
	}

}
