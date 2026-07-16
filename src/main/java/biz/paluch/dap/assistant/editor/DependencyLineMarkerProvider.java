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

import java.awt.event.MouseEvent;
import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.assistant.ArtifactReferenceContext;
import biz.paluch.dap.assistant.action.DependencyCheckTask;
import biz.paluch.dap.assistant.action.UpgradeRequest;
import biz.paluch.dap.checker.SecurityShieldIcons;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeSuggestion;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInsight.daemon.GutterIconNavigationHandler;
import com.intellij.codeInsight.daemon.GutterName;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.codeInsight.daemon.LineMarkerProviderDescriptor;
import com.intellij.openapi.editor.markup.GutterIconRenderer;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Gutter line marker for dependency declarations, rendering upgrade
 * availability as well as known vulnerabilities and governing rule state, with
 * navigation to the upgrade action.
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

		ArtifactReferenceContext context = ArtifactReferenceContext.from(element, this::getContext);
		if (context.isAbsent()) {
			return null;
		}

		InterfaceAssistant ui = context.getDependencyContext()
				.getInterfaceAssistant();

		PsiElement anchor = PsiTreeUtil.getDeepestFirst(element);
		UpgradeSuggestions suggestions = context.getSuggestions();
		DependencyRuleEvaluator evaluated = context.getEvaluator();
		ArtifactDeclaration declaration = context.getDeclaration();
		Vulnerabilities vulnerabilities = context.getCurrentVulnerabilities();
		boolean vulnerable = vulnerabilities.isVulnerable();
		VulnerabilitiesPresentation vulnerability = vulnerable ? VulnerabilitiesPresentation.of(vulnerabilities)
				: null;

		Icon gutterIcon = ui.getGutterIcon(declaration);
		Icon transparentIcon = evaluated.isPresent() ? gutterIcon : IconLoader.getTransparentIcon(gutterIcon, 0.7f);

		ArtifactId artifactId = context.getArtifactId();

		if (suggestions.isEmpty()) {

			if (vulnerable) {

				return new LineMarkerInfo<>(anchor, context.getHighlightRange(anchor),
						getRuleIcon(transparentIcon, evaluated), e -> vulnerability.getText(),
						new UpgradeDialogNavigationHandler(artifactId),
						GutterIconRenderer.Alignment.LEFT, vulnerability::getText);

			} else if (evaluated.isPresent() && evaluated.isLocked()) {

				return new LineMarkerInfo<>(anchor, context.getHighlightRange(anchor),
						getRuleIcon(transparentIcon, evaluated), e -> evaluated.getToolTipText(),
						new UpgradeDialogNavigationHandler(artifactId),
						GutterIconRenderer.Alignment.LEFT, evaluated::getAccessibleName);
			}
			return null;
		}

		UpgradeSuggestion suggestion = suggestions.getSuggestion();
		String tooltip;
		String accessibleName;
		if (vulnerable) {

			tooltip = vulnerability.getText();
			if (suggestions.contains(UpgradeStrategy.SAFE)) {
				tooltip += "<br>" + MessageBundle.message("gutter.vulnerable.upgrade-hint");
				accessibleName = MessageBundle.message("gutter.vulnerable.accessible");
			} else {
				accessibleName = tooltip;
			}
		} else {
			tooltip = context.hasRule() ? suggestion.getSuggestionMessage() : suggestion.getMessage();
			accessibleName = context.hasRule() ? MessageBundle.message("gutter.suggestion.accessible")
					: MessageBundle.message("gutter.newer.accessible");
		}

		if (evaluated.isPresent()) {
			String evaluatedToolTip = evaluated.getToolTipText();
			if (StringUtils.hasText(evaluatedToolTip)) {
				tooltip += "<br>" + evaluated.getToolTipText();
			}
		}

		PsiElement versionLiteral = declaration.getVersionLiteral();
		if (!declaration.isVersionDefinedInSameFile() && versionLiteral != null) {

			VirtualFile virtualFile = versionLiteral.getContainingFile().getVirtualFile();
			if (virtualFile != null) {

				String tooltipToUse = MessageBundle.message("gutter.declaration.file", virtualFile.getName())
						+ "<br/>" + tooltip;
				Icon navigateIcon = vulnerable
						? getVulnerableIcon(vulnerabilities)
						: ui.getNavigateIcon(declaration);

				return new LineMarkerInfo<>(anchor, context.getHighlightRange(anchor),
						navigateIcon,
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

		if (vulnerable) {
			icon = getVulnerableIcon(vulnerabilities);
		} else if (evaluated.isPresent()) {
			icon = getRuleIcon(transparentIcon, evaluated);
		} else {
			icon = transparentIcon;
		}

		return new LineMarkerInfo<>(anchor, context.getHighlightRange(anchor),
				icon, e -> tooltipToUse,
				new UpgradeDialogNavigationHandler(artifactId),
				GutterIconRenderer.Alignment.LEFT, () -> accessibleName);
	}

	private Icon getVulnerableIcon(Vulnerabilities vulnerabilities) {
		return SecurityShieldIcons.outline(vulnerabilities.getHighestSeverity())
				.getIcon();
	}

	private Icon getRuleIcon(Icon transparentIcon, DependencyRuleEvaluator evaluated) {
		if (evaluated.isPassed() && evaluated.isLocked() || !evaluated.isPassed()) {
			return evaluated.getIcon();
		}
		return transparentIcon;
	}

	protected ProjectDependencyContext getContext(PsiElement element) {
		return DependencyAssistantDispatcher.findFirstContext(element);
	}

	/**
	 * Navigation handler that opens the Dependency Check dialog scoped to the
	 * clicked declaration's build file, with the artifact's row selected and
	 * revealed.
	 */
	public record UpgradeDialogNavigationHandler(ArtifactId artifactId)
			implements GutterIconNavigationHandler<PsiElement> {

		@Override
		public void navigate(MouseEvent mouseEvent, PsiElement psiElement) {

			PsiFile file = psiElement.getContainingFile();
			ProgressManager.getInstance().run(new DependencyCheckTask(file.getProject(),
					new UpgradeRequest(List.of(), file, artifactId)));
		}

	}

}
