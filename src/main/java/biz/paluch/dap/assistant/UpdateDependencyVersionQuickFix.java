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
import biz.paluch.dap.checker.CheckerIcons;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInsight.intention.FileModifier;
import com.intellij.codeInsight.intention.HighPriorityAction;
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
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

/**
 * Dependency update quick fix that rewrites the associated {@link PsiElement}
 * version literal to the target of a {@link DependencyUpdate}.
 *
 * @author Mark Paluch
 */
public class UpdateDependencyVersionQuickFix extends LocalQuickFixOnPsiElement
		implements Iconable, IntentionAction, IntentionActionWithOptions, HighPriorityAction, DependencyUpdateFix {

	private final UpgradeStrategy strategy;

	private final ProjectDependencyContext dependencyContext;

	private final DependencyUpdate update;

	private final ArtifactDeclaration declaration;

	private final Vulnerabilities vulnerabilities;

	private final VersionStatus status;

	private final Priority priority;

	private final Icon icon;

	protected UpdateDependencyVersionQuickFix(UpgradeStrategy strategy,
			ArtifactReferenceContext context, DependencyUpdate update) {
		this(context.getArtifactReference().getDeclaration().getRequiredVersionLiteral(), strategy, context, update);
	}

	protected UpdateDependencyVersionQuickFix(PsiElement element, UpgradeStrategy strategy,
			ArtifactReferenceContext context, DependencyUpdate update) {
		this(element, strategy, context.getDependencyContext(), update, context.getDeclaration(),
				context.getStatus(update.version()));
	}

	protected UpdateDependencyVersionQuickFix(PsiElement element, UpgradeStrategy strategy,
			ProjectDependencyContext dependencyContext, DependencyUpdate update, ArtifactDeclaration declaration,
			VersionStatus status) {
		super(element);
		this.priority = Priority.HIGH;
		this.strategy = strategy;
		this.dependencyContext = dependencyContext;
		this.update = update;
		this.declaration = declaration;
		this.status = status;
		this.vulnerabilities = status.getVulnerabilities();
		this.icon = createIcon();
	}

	private Icon createIcon() {
		if (strategy == UpgradeStrategy.SAFE) {
			return CheckerIcons.SAFE;
		}
		Icon overlay = status.getFilledIcon();
		if (vulnerabilities.isVulnerable()) {
			return overlay;
		}
		Icon dependencyIcon = dependencyContext.getInterfaceAssistant().getGutterIcon(declaration);
		return DependencyAssistantIcons.upgradeIcon(dependencyIcon, overlay);
	}

	@Override
	public @IntentionName String getText() {

		if (strategy == UpgradeStrategy.SAFE) {
			return MessageBundle.message("UpgradeDependencyAction.safe-name", update.versionAsString());
		}

		String label = MessageBundle.message("UpgradeDependencyAction.name", update.versionAsString());
		if (vulnerabilities.isVulnerable()) {
			return MessageBundle.message("UpgradeDependencyAction.vulnerable-suffix", label,
					VulnerabilitiesPresentation.of(vulnerabilities).getDetail());
		}
		return label;
	}

	@Override
	public @IntentionFamilyName String getFamilyName() {
		return strategy.getDisplayName();
	}

	@Override
	public Priority getPriority() {
		return strategy.isRemediation() ? Priority.TOP : priority;
	}

	@Override
	public boolean isAvailable(Project project, Editor editor, PsiFile psiFile) {
		return super.isAvailable() && getStartElement().getContainingFile()
				.isEquivalentTo(psiFile);
	}


	@Override
	public Icon getIcon(int flags) {
		return getIcon();
	}

	protected Icon getIcon() {
		return this.icon;
	}

	public DependencyUpdate getUpdate() {
		return this.update;
	}

	public boolean hasStrategy(UpgradeStrategy strategy) {
		return this.strategy == strategy;
	}

	@Override
	public @Unmodifiable List<IntentionAction> getOptions() {
		return List.of(new ApplyAllUpgradesIntention(dependencyContext, strategy),
				UpgradeDependenciesIntention.INSTANCE);
	}

	@Override
	public CombiningPolicy getCombiningPolicy() {
		return CombiningPolicy.IntentionOptionsOnly;
	}

	@Override
	public boolean startInWriteAction() {
		return true;
	}

	@Override
	public void invoke(Project project, PsiFile file, PsiElement startElement, PsiElement endElement) {
		dependencyContext.applyUpdate(startElement, update);
	}

	@Override
	public void invoke(Project project, Editor editor, PsiFile psiFile) throws IncorrectOperationException {
		dependencyContext.applyUpdates(psiFile, List.of(update));
	}

	@Override
	public @Nullable FileModifier getFileModifierForPreview(PsiFile target) {
		return new UpdateDependencyIntention(dependencyContext, update, declaration, target);
	}

}
