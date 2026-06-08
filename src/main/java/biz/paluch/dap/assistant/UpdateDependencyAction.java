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
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpgradeSuggestion;
import biz.paluch.dap.util.PsiElements;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.codeInspection.util.IntentionFamilyName;
import com.intellij.modcommand.ActionContext;
import com.intellij.modcommand.ModPsiUpdater;
import com.intellij.modcommand.Presentation;
import com.intellij.modcommand.PsiUpdateModCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ScalableIcon;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.ui.LayeredIcon;

/**
 * Dependency update action that rewrites the associated {@link PsiElement}
 * version literal to the target of a {@link DependencyUpdate}.
 *
 * <p>The presentation impact category (the gutter age icon) is derived from the
 * relationship between the {@link ArtifactDeclaration declaration}'s current
 * version and the update target, not from a preselected strategy.
 *
 * @author Mark Paluch
 */
public class UpdateDependencyAction extends PsiUpdateModCommandAction<PsiElement> implements LocalQuickFix {

	private final ProjectDependencyContext dependencyContext;

	private final DependencyUpdate update;

	private final ArtifactDeclaration declaration;

	protected UpdateDependencyAction(PsiElement element, ProjectDependencyContext dependencyContext,
			UpgradeSuggestion suggestion) {
		this(element, dependencyContext, suggestion.toDependencyUpdate(), suggestion.getArtifactDeclaration());
	}

	protected UpdateDependencyAction(ArtifactDeclaration declaration, ProjectDependencyContext dependencyContext,
			DependencyUpdate update) {
		this(declaration.getVersionLiteral(), dependencyContext, update, declaration);
	}

	protected UpdateDependencyAction(PsiElement element, ProjectDependencyContext dependencyContext,
			DependencyUpdate update, ArtifactDeclaration declaration) {
		super(element);
		this.dependencyContext = dependencyContext;
		this.update = update;
		this.declaration = declaration;
	}

	@Override
	public void invoke(ActionContext context, PsiElement element, ModPsiUpdater updater) {

		int caretOffset = context.offset();
		String previousText = null;
		PsiElement container = null;
		if (element.getTextRange().containsOffset(caretOffset)) {
			container = PsiElements.unleaf(element);
			previousText = container.getText();
		}
		dependencyContext.applyUpdate(element, update);

		if (container != null && updater != null) {
			String currentText = container.getText();
			int cpl = StringUtil.commonSuffixLength(previousText, currentText);
			int newOffset = container.getTextRange().getEndOffset() - cpl;
			updater.moveCaretTo(newOffset);
		}
	}

	@Override
	public void applyFix(Project project, ProblemDescriptor descriptor) {
		super.perform(ActionContext.from(descriptor));
	}

	@Override
	protected Presentation getPresentation(ActionContext context, PsiElement element) {

		String name = MessageBundle.message("UpgradeDependencyAction.name", update.versionAsString());
		return Presentation.of(name).withIcon(getIcon());
	}

	protected Icon getIcon() {
		InterfaceAssistant ia = dependencyContext.getInterfaceAssistant();
		Icon dependencyIcon = ia.getGutterIcon(declaration);
		Icon ageIcon = getVersionAgeIcon();

		LayeredIcon icon = new LayeredIcon(2);
		Icon scaled = ((ScalableIcon) dependencyIcon).scale(0.7f);
		icon.setIcon(scaled, 0, dependencyIcon.getIconHeight() - scaled.getIconHeight(), 0);
		icon.setIcon(((ScalableIcon) ageIcon).scale(0.7f), 1, dependencyIcon.getIconWidth() / 2,
				dependencyIcon.getIconHeight() / 2);
		return icon;
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
	public @IntentionFamilyName String getFamilyName() {
		return MessageBundle.message("problemgroup.upgrade-available");
	}

}
