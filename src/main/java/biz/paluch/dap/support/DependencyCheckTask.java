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

import biz.paluch.dap.DependencyCheckDialog;
import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.artifact.DependencyUpdates;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Background task that checks dependency and plugin updates for one context and
 * shows {@link DependencyCheckDialog} on success.
 *
 * @author Mark Paluch
 */
class DependencyCheckTask extends Task.Backgroundable {

	private static final Logger LOG = Logger.getInstance(DependencyCheckTask.class);

	private final Project project;

	private final VirtualFile buildFile;

	private final ProjectDependencyContext context;

	private volatile @Nullable DependencyUpdates resultRef;

	DependencyCheckTask(Project project, VirtualFile buildFile, ProjectDependencyContext context) {
		super(project, MessageBundle.message("action.check.dependencies.progress"), true);
		this.project = project;
		this.buildFile = buildFile;
		this.context = context;
	}

	@Override
	public void run(ProgressIndicator indicator) {
		resultRef = new DependencyCheck(project).getDependencyUpdates(indicator, context,
				DependencyCheck.cached());
	}

	@Override
	public void onSuccess() {

		DependencyUpdates result = resultRef;
		if (result != null) {
			new DependencyCheckDialog(project, buildFile, result, new BuildActionDelegate(project, context, buildFile))
					.show();
		}
	}

	@Override
	public void onThrowable(Throwable error) {
		LOG.warn("Dependency check failed", error);

		// TODO: use balloon notifications
		Messages.showMessageDialog(project,
				MessageBundle.message("action.check.dependencies.task.error", error.getMessage()),
				MessageBundle.message("error.title"), Messages.getErrorIcon());
	}

}
