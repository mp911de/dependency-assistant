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
package biz.paluch.dap.support;

import biz.paluch.dap.MessageBundle;

import javax.swing.Icon;

import org.jspecify.annotations.Nullable;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * Gutter line marker that indicates a newer dependency or plugin version in a build file.
 * <p>
 * The marker appears on the line of the version value and the icon reflects the highest available upgrade tier: patch,
 * minor, or major.
 * <p>
 * Version resolution is delegated to {@link biz.paluch.dap.support.VersionUpgradeLookupSupport}. Clicking the gutter
 * icon invokes the update action.
 */
public abstract class NewerVersionLineMarkerProviderSupport implements LineMarkerProvider {

	private final String actionId;
	private final Icon icon;
	private final Icon navigate;

	protected NewerVersionLineMarkerProviderSupport(String actionId, Icon icon, Icon navigate) {
		this.actionId = actionId;
		this.icon = icon;
		this.navigate = navigate;
	}

	@Override
	public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {

		VersionUpgradeLookupSupport service = getVersionLookupSupport(element);
		VersionUpgradeLookupSupport.UpgradeAvailable availableUpgrade = service.findAvailableUpgrade(element);
		if (availableUpgrade == null) {
			return null;
		}

		VersionUpgradeLookupSupport.UpgradeSuggestion upgradeSuggestion = availableUpgrade.suggestion();

		String tooltip = upgradeSuggestion.getMessage();
		String accessibleName = MessageBundle.message("gutter.newer.accessible");
		PsiElement anchor = PsiTreeUtil.getDeepestFirst(element);

		if (availableUpgrade.metadata() != null && !availableUpgrade.metadata().localVersionDeclared()
				&& availableUpgrade.metadata().versionLocation() != null) {

			LocalFileSystem lfs = LocalFileSystem.getInstance();
			VirtualFile virtualFile = lfs.findFileByPath(availableUpgrade.metadata().versionLocation());
			if (virtualFile != null) {

				String tooltipToUse = MessageBundle.message("gutter.declaration.file", virtualFile.getName())
						+ System.lineSeparator() + tooltip;

				return new LineMarkerInfo<>(anchor, getTextRange(anchor), navigate, e -> tooltipToUse,
						(mouseEvent, psiElement) -> {
							FileEditorManager.getInstance(psiElement.getProject()).openFile(virtualFile, true);
						}, GutterIconRenderer.Alignment.LEFT, () -> accessibleName);
			}
		}

		return new LineMarkerInfo<>(anchor, getTextRange(anchor), icon, e -> tooltip, (mouseEvent, psiElement) -> {
			AnAction action = ActionManager.getInstance().getAction(actionId);
			if (action != null) {
				ActionManager.getInstance().tryToExecute(action, mouseEvent, null, null, true);
			}
		}, GutterIconRenderer.Alignment.LEFT, () -> accessibleName);
	}

	protected abstract VersionUpgradeLookupSupport getVersionLookupSupport(PsiElement element);

	protected TextRange getTextRange(PsiElement element) {
		return element.getTextRange();
	}

}
