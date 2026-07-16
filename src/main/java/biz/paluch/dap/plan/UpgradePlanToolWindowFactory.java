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

package biz.paluch.dap.plan;

import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactVersion;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.openapi.wm.ex.ToolWindowEx;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;

/**
 * Factory for the bottom-anchored Upgrade Plan tool window, and the entry point
 * for {@link #openWith opening it} with upgrades transferred from the review
 * dialog.
 *
 * @author Mark Paluch
 */
public class UpgradePlanToolWindowFactory implements ToolWindowFactory, DumbAware {

	/**
	 * Matches the {@code toolWindow id} registered in {@code plugin.xml}.
	 */
	static final String ID = "Upgrade Plan";

	/**
	 * Capture the armed upgrades into a fresh plan and reveal the Upgrade Plan tool
	 * window showing them. Any previously planned upgrade is discarded so the
	 * transfer defines the plan afresh.
	 *
	 * @param project the project owning the plan.
	 * @param upgrades the armed upgrades to transfer; they become the whole plan.
	 * @param files the build-file paths that become the plan's scope.
	 */
	// TODO: If two upgradeCandidates share the same property and the same
	// assistant, keep only one of them.
	public static void openWith(Project project, Map<? extends UpgradePlanCapture, ArtifactVersion> upgrades,
			List<VirtualFile> files) {

		UpgradePlanService.getInstance(project).planUpgrades(upgrades, files);

		ToolWindow toolWindow = ToolWindowManager.getInstance(project).getToolWindow(ID);
		if (toolWindow != null) {
			toolWindow.activate(null);
		}
	}

	@Override
	public void createToolWindowContent(Project project, ToolWindow toolWindow) {

		UpgradePlanService service = UpgradePlanService.getInstance(project);
		service.refreshTicketSystem();
		UpgradePlanPanel panel = new UpgradePlanPanel(service);
		Content content = ContentFactory.getInstance().createContent(panel, null, false);
		content.setDisposer(panel);
		// anchor keyboard focus (and with it the $Copy/$Paste action context) in
		// the panel on activation, even while the plan is empty
		content.setPreferredFocusableComponent(panel.getPreferredFocusableComponent());
		toolWindow.getContentManager().addContent(content);

		// the New UI fades header toolbars (including tab actions) when the tool
		// window deactivates; the milestone/label selectors are persistent
		// controls, so opt out via the documented client property
		toolWindow.getComponent().putClientProperty(ToolWindowContentUi.DONT_HIDE_TOOLBAR_IN_HEADER, true);

		if (toolWindow instanceof ToolWindowEx toolWindowEx) {
			toolWindowEx.setAdditionalGearActions(panel.createGearActions());
			toolWindowEx.setTabActions(panel.createTabActions());
		}

		panel.restore();
	}

}
