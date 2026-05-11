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
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.AvailableUpgrades;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.support.UpgradeSuggestion;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterName;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Gutter line marker that indicates dependency upgrade availability.
 *
 * @author Mark Paluch
 */
public class UpgradeAvailableLineMarkerProvider extends LineMarkerProviderDescriptor {

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

		ProjectDependencyContext context = DependencyAssistantDispatcher.findFirstContext(element);
		if (context.isAbsent()) {
			return null;
		}

		AvailableUpgrades upgrades = UpgradeSuggestions.suggest(context, element);
		if (!upgrades.isPresent()) {
			return null;
		}

		UpgradeSuggestion suggestion = upgrades.getUpgradeSuggestion();
		if (!suggestion.isPresent()) {
			return null;
		}

		String tooltip = suggestion.getMessage();
		String accessibleName = MessageBundle.message("gutter.newer.accessible");
		PsiElement anchor = PsiTreeUtil.getDeepestFirst(element);
		InterfaceAssistant ui = context.getInterfaceAssistant();

		ArtifactDeclaration declaration = suggestion.getArtifactDeclaration();
		PsiElement versionLiteral = declaration.getVersionLiteral();
		if (!declaration.isVersionDefinedInSameFile() && versionLiteral != null) {

			VirtualFile virtualFile = versionLiteral.getContainingFile().getVirtualFile();
			if (virtualFile != null) {

				String tooltipToUse = MessageBundle.message("gutter.declaration.file", virtualFile.getName())
						+ System.lineSeparator() + tooltip;

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

		return new LineMarkerInfo<>(anchor, ui.getHighlightRange(anchor),
				ui.getGutterIcon(declaration), e -> tooltip,
				new ActionNavigationHandler("biz.paluch.dap.UpgradeDependencies"),
				GutterIconRenderer.Alignment.LEFT, () -> accessibleName);
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
