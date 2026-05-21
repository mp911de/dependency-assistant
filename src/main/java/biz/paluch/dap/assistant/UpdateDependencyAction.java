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

import javax.swing.Icon;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpgradeSuggestion;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.psi.PsiElement;
import com.intellij.ui.LayeredIcon;

/**
 * Dependency upgrade action to upgrade the associated {@link PsiElement}
 * version literal to a {@link UpgradeSuggestion}.
 *
 * @author Mark Paluch
 */
public class UpdateDependencyAction extends PsiUpdateModCommandAction<PsiElement> implements LocalQuickFix {

	private final ProjectDependencyContext dependencyContext;

	private final UpgradeSuggestion suggestion;

	protected UpdateDependencyAction(PsiElement element, ProjectDependencyContext dependencyContext,
			UpgradeSuggestion suggestion) {
		super(element);
		this.dependencyContext = dependencyContext;
		this.suggestion = suggestion;
	}

	@Override
	protected void invoke(ActionContext context, PsiElement element, ModPsiUpdater updater) {
		// TODO: move caret behind the updated version
		dependencyContext.applyUpdate(element, suggestion.toDependencyUpdate());
	}

	@Override
	public void applyFix(Project project, ProblemDescriptor descriptor) {

		// todo: for properties, apply the update to the property. consider same/other
		// file.
		dependencyContext.applyUpdate(descriptor.getPsiElement(), suggestion.toDependencyUpdate());
	}

	@Override
	protected Presentation getPresentation(ActionContext context, PsiElement element) {

		String strategy = MessageBundle.message("upgrade-strategy.%s".formatted(suggestion.getStrategy().name()));
		String name = MessageBundle.message("UpgradeDependencyAction.name", strategy,
				suggestion.getRelease().getVersion());

		InterfaceAssistant ia = dependencyContext.getInterfaceAssistant();
		Icon dependencyIcon = ia.getGutterIcon(suggestion.getArtifactDeclaration());
		VersionAge age = VersionAge.fromTarget(suggestion.getStrategy());
		Icon ageIcon = age.getIcon();

		LayeredIcon icon = new LayeredIcon(2);
		Icon scaled = ((ScalableIcon) dependencyIcon).scale(0.7f);
		icon.setIcon(scaled, 0, dependencyIcon.getIconHeight() - scaled.getIconHeight(), 0);
		icon.setIcon(((ScalableIcon) ageIcon).scale(0.7f), 1, dependencyIcon.getIconWidth() / 2,
				dependencyIcon.getIconHeight() / 2);

		return Presentation.of(name).withIcon(icon);
	}

	@Override
	public @IntentionFamilyName String getFamilyName() {
		return MessageBundle.message("problemgroup.upgrade-available");
	}


}
