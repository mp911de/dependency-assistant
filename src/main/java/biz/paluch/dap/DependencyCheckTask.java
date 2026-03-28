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

import biz.paluch.dap.artifact.DependencyCheckService;
import biz.paluch.dap.artifact.DependencyUpdates;

import java.io.IOException;

import org.jspecify.annotations.Nullable;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Task to check Maven dependency and plugin updates.
 *
 * @author Mark Paluch
 */
class DependencyCheckTask extends Task.Backgroundable {

	private static final Logger LOG = Logger.getInstance(DependencyCheckTask.class);

	private final Project project;
	private final String pomContent;
	private final VirtualFile pomFile;

	private volatile @Nullable DependencyUpdates resultRef;

	public DependencyCheckTask(Project project, VirtualFile pomFile, String pomContent) {
		super(project, MessageBundle.message("action.check.dependencies.progress"), true);
		this.project = project;
		this.pomContent = pomContent;
		this.pomFile = pomFile;
	}

	@Override
	public void run(ProgressIndicator indicator) {
		try {
			resultRef = new DependencyCheckService(project).runCheck(indicator, pomContent, pomFile);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public void onSuccess() {
		DependencyUpdates result = resultRef;
		if (result != null) {
			new DependencyCheckDialog(project, pomFile, result).show();
		}
	}

	@Override
	public void onThrowable(Throwable error) {
		LOG.warn("Dependency check failed", error);
		Messages.showMessageDialog(project, MessageBundle.message("action.check.dependencies.error", error.getMessage()),
				MessageBundle.message("action.check.dependencies.error.title"), Messages.getErrorIcon());
	}
}
