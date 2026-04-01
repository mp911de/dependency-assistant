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
package biz.paluch.dap;

import javax.swing.Icon;

import org.jspecify.annotations.Nullable;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProvider;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Gutter line marker that indicates a newer Maven dependency or plugin version in a {@code pom.xml}.
 * <p>
 * The marker appears on the line of the version value — either a literal {@code <version>} tag inside a
 * {@code <dependency>} or {@code <plugin>}, or a {@code <properties>} child tag whose name maps to a known artifact in
 * the cache. The icon reflects the highest available upgrade tier: patch, minor, or major.
 * <p>
 * Version resolution is delegated to {@link VersionUpgradeLookupService}. Clicking the gutter icon invokes the
 * {@link UpdateMavenDependenciesAction}.
 */
public class NewerVersionLineMarkerProvider implements LineMarkerProvider {

	@Override
	public @Nullable LineMarkerInfo<?> getLineMarkerInfo(PsiElement element) {

		VersionUpgradeLookupService service = new VersionUpgradeLookupService(element.getProject(),
				element.getContainingFile());
		VersionUpgradeLookupService.UpgradeSuggestion upgradeSuggestion = service.determineUpgrade(element);
		if (upgradeSuggestion == null) {
			return null;
		}

		Icon icon = DependencyAssistantIcons.TRANSPARENT_ICON;
		String tooltip = upgradeSuggestion.getMessage();
		String accessibleName = MessageBundle.message("gutter.newer.accessible");

		return new LineMarkerInfo<>(element,
				new TextRange(element.getTextRange().getStartOffset(), element.getTextRange().getStartOffset()), icon,
				e -> tooltip, (mouseEvent, psiElement) -> {
					AnAction action = ActionManager.getInstance().getAction("biz.paluch.dap.UpdateMavenDependencies");
					if (action != null) {
						ActionManager.getInstance().tryToExecute(action, mouseEvent, null, null, true);
					}
				}, GutterIconRenderer.Alignment.LEFT, () -> accessibleName);
	}
}
