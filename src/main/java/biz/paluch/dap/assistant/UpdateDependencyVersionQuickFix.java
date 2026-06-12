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

import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpgradeSuggestion;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.IntentionActionWithOptions;
import com.intellij.codeInspection.LocalQuickFixOnPsiElement;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.codeInspection.util.IntentionName;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Iconable;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Dependency update quick fix that rewrites the associated {@link PsiElement}
 * version literal to the target of a {@link DependencyUpdate}.
 *
 * @author Mark Paluch
 * @see UpdateDependencyAction
 */
public class UpdateDependencyVersionQuickFix extends LocalQuickFixOnPsiElement
		implements Iconable, IntentionAction, IntentionActionWithOptions {

	private final UpgradeStrategy strategy;

	private final ProjectDependencyContext dependencyContext;

	private final DependencyUpdate update;

	private final ArtifactDeclaration declaration;

	protected UpdateDependencyVersionQuickFix(PsiElement element, ProjectDependencyContext dependencyContext,
			UpgradeSuggestion suggestion) {
		this(element, suggestion.getStrategy(), dependencyContext, suggestion.toDependencyUpdate(),
				suggestion.getArtifactDeclaration());
	}

	protected UpdateDependencyVersionQuickFix(ArtifactDeclaration declaration,
			ProjectDependencyContext dependencyContext,
			DependencyUpdate update, UpgradeStrategy strategy) {
		this(declaration.getVersionLiteral(), strategy, dependencyContext, update, declaration);
	}

	protected UpdateDependencyVersionQuickFix(PsiElement element, UpgradeStrategy strategy,
			ProjectDependencyContext dependencyContext,
			DependencyUpdate update, ArtifactDeclaration declaration) {
		super(element);
		this.strategy = strategy;
		this.dependencyContext = dependencyContext;
		this.update = update;
		this.declaration = declaration;
	}

	@Override
	public @IntentionName String getText() {
		return MessageBundle.message("UpgradeDependencyAction.name", update.versionAsString());
	}

	@Override
	public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile psiFile) {
		return super.isAvailable() && getStartElement().getContainingFile()
				.isEquivalentTo(psiFile);
	}

	@Override
	public boolean startInWriteAction() {
		return true;
	}

	@Override
	public @IntentionFamilyName String getFamilyName() {
		return MessageBundle.displayName(strategy);
	}

	@Override
	public void invoke(Project project, PsiFile file, PsiElement startElement, PsiElement endElement) {
		dependencyContext.applyUpdate(startElement, update);
	}

	@Override
	public void invoke(@NotNull Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
		dependencyContext.applyUpdates(psiFile, List.of(update));
	}

	@Override
	public @Nullable FileModifier getFileModifierForPreview(@NotNull PsiFile target) {
		return new UpdateDependencyIntention(dependencyContext, update, declaration, target);
	}

	@Override
	public Icon getIcon(int flags) {
		return getIcon();
	}

	protected Icon getIcon() {
		Icon dependencyIcon = dependencyContext.getInterfaceAssistant().getGutterIcon(declaration);
		return DependencyAssistantIcons.upgradeIcon(dependencyIcon, getVersionAgeIcon());
	}

	protected Icon getVersionAgeIcon() {
		return versionAge().getIcon();
	}

	private VersionAge versionAge() {

		if (!declaration.isVersionDefined()) {
			return VersionAge.SAME_OR_UNKNOWN;
		}

		ArtifactVersion current = declaration.getVersion();
		return VersionAge.between(current, update.version());
	}

	@Override
	public @NotNull @Unmodifiable List<@NotNull IntentionAction> getOptions() {
		return List.of(new ApplyAllUpgradesIntention(dependencyContext, strategy, getIcon()),
				UpgradeDependenciesIntention.INSTANCE);
	}

	@Override
	public @NotNull CombiningPolicy getCombiningPolicy() {
		return CombiningPolicy.IntentionOptionsOnly;
	}

}
