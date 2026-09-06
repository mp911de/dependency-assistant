/*
 * Copyright 2026-present the original author or authors.
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

import java.net.URI;

import biz.paluch.dap.metadata.ProjectMetadata;
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.util.HttpClientUtil;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Open release notes for the first selected item's target version. URL lookup
 * uses cached metadata without fetching.
 *
 * @author Mark Paluch
 */
public class ShowReleaseNotesAction extends DumbAwareAction {

	@Override
	public void update(AnActionEvent e) {

		Project project = e.getProject();
		UpgradePlanItem item = PlanSelection.from(e).first();

		e.getPresentation().setEnabled(project != null && item != null && releaseNotesUrl(project, item) != null);
	}

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void actionPerformed(AnActionEvent e) {

		Project project = e.getProject();
		if (project == null) {
			return;
		}

		PlanSelection.from(e).doWithFirst(item -> {
			URI url = releaseNotesUrl(project, item);
			if (url != null) {
				HttpClientUtil.openBrowser(url);
			}
		});
	}

	private static @Nullable URI releaseNotesUrl(Project project, UpgradePlanItem item) {

		ProjectMetadata metadata = ProjectMetadataService.getInstance(project)
				.getMetadata(item.getMembers().getFirst().getArtifactId());
		return metadata.findReleaseNotesUrl(item.getToVersion());
	}

}
