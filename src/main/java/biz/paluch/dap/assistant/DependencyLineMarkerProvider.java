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

import java.awt.event.MouseEvent;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.AvailableUpgrades;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpgradeSuggestion;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterName;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Gutter line marker that indicates dependency upgrade availability.
 *
 * @author Mark Paluch
 */
public class DependencyLineMarkerProvider extends LineMarkerProviderDescriptor {

	@Override
	public @GutterName String getName() {
		return MessageBundle.message("gutter.newer.accessible");
	}

	@Override
	public Icon getIcon() {
		return DependencyAssistantIcons.ICON;
	}

	@Override
	public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {

		ProjectDependencyContext context = getContext(element);
		if ((context.isAbsent())) {
			return null;
		}

		VersionUpgradeLookup lookup = context.getLookup(element, element.getContainingFile().getVirtualFile());

		ArtifactReference artifactReference = UpgradeSuggestions.resolveArtifact(context, element);
		if (!artifactReference.isResolved() || !artifactReference.getDeclaration().isVersionDefined()) {
			return null;
		}

		VirtualFile containingFile = element.getContainingFile().getVirtualFile();
		DependencyfileService ruleService = DependencyfileService.getInstance(element.getProject());
		ArtifactId artifactId = artifactReference.getArtifactId();
		DependencyRule rule = ruleService.resolve(artifactId, containingFile, context.getProjectVersion());
		EvaluatedDependencyRule evaluated = EvaluatedDependencyRule.of(rule, artifactId,
				artifactReference.getDeclaration().getVersion().getVersion(),
				context.getInterfaceAssistant());
		InterfaceAssistant ui = context.getInterfaceAssistant();
		PsiElement anchor = PsiTreeUtil.getDeepestFirst(element);
		AvailableUpgrades upgrades = lookup.suggestUpgrades(artifactReference).filterSuggestions(rule::isEnabled);

		if (!upgrades.isPresent()) {
			if (evaluated.isPresent() && evaluated.isLocked()) {
				return new LineMarkerInfo<>(anchor, ui.getHighlightRange(anchor),
						evaluated.getIcon(), e -> evaluated.getToolTipText(),
						new ActionNavigationHandler("biz.paluch.dap.UpgradeDependencies"),
						GutterIconRenderer.Alignment.LEFT, evaluated::getAccessibleName);
			}
			return null;
		}

		UpgradeSuggestion suggestion = upgrades.getUpgradeSuggestion();
		if (!suggestion.isPresent()) {
			return null;
		}

		String tooltip = rule.isPresent() ? suggestion.getSuggestionMessage() : suggestion.getMessage();
		String accessibleName = rule.isPresent() ? MessageBundle.message("gutter.suggestion.accessible")
				: MessageBundle.message("gutter.newer.accessible");

		if (evaluated.isPresent()) {
			String evaluatedToolTip = evaluated.getToolTipText();
			if (StringUtils.hasText(evaluatedToolTip)) {
				tooltip += "<br>" + evaluated.getToolTipText();
			}
		}

		ArtifactDeclaration declaration = suggestion.getArtifactDeclaration();
		PsiElement versionLiteral = declaration.getVersionLiteral();
		if (!declaration.isVersionDefinedInSameFile() && versionLiteral != null) {

			VirtualFile virtualFile = versionLiteral.getContainingFile().getVirtualFile();
			if (virtualFile != null) {

				String tooltipToUse = MessageBundle.message("gutter.declaration.file", virtualFile.getName())
						+ "<br/>" + tooltip;

				return new LineMarkerInfo<>(anchor, ui.getHighlightRange(anchor),
						ui.getNavigateIcon(declaration),
						e -> tooltipToUse,
						(mouseEvent, psiElement) -> {

							OpenFileDescriptor descriptor = new OpenFileDescriptor(versionLiteral.getProject(),
									virtualFile, versionLiteral.getTextOffset());
							descriptor.navigate(true);
						}, GutterIconRenderer.Alignment.LEFT, () -> accessibleName);
			}
		}

		String tooltipToUse = tooltip;
		Icon icon;

		if (evaluated.isPresent()) {
			icon = evaluated.getIcon();
		} else {
			icon = IconLoader.getTransparentIcon(ui.getGutterIcon(declaration), 0.7f);
		}

		return new LineMarkerInfo<>(anchor, ui.getHighlightRange(anchor),
				icon, e -> tooltipToUse,
				new ActionNavigationHandler("biz.paluch.dap.UpgradeDependencies"),
				GutterIconRenderer.Alignment.LEFT, () -> accessibleName);
	}

	protected ProjectDependencyContext getContext(PsiElement element) {
		return DependencyAssistantDispatcher.findFirstContext(element);
	}

	/**
	 * Navigation handler that invokes the configured action id.
	 */
	public record ActionNavigationHandler(String actionId) implements GutterIconNavigationHandler<PsiElement> {

		@Override
		public void navigate(MouseEvent mouseEvent, PsiElement psiElement) {
			AnAction action = ActionManager.getInstance().getAction(actionId);
			if (action != null) {
				ActionManager.getInstance().tryToExecute(action, mouseEvent, null, null, true);
			}
		}

	}

}
