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
import biz.paluch.dap.artifact.DependencyUpdates;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jspecify.annotations.Nullable;

/**
 * Suppot class for a background task that refreshes the release state for each
 * used dependency.
 *
 * @author Mark Paluch
 */
public abstract class ReleasesRetrievalTaskSupport extends Task.Backgroundable {

	private final Project project;

	public ReleasesRetrievalTaskSupport(Project project) {
		super(project, MessageBundle.message("action.update.releases.task"), true);
		this.project = project;
	}

	@Override
	public void onSuccess() {

		DependencyUpdates result = getUpdates();
		if (result == null || project.isDisposed()) {
			return;
		}

		Notifications.releaseMetadataRefreshed(project, result, getDuration());
		DaemonCodeAnalyzer.getInstance(project).restart(MessageBundle.message("action.update.releases.done.title"));
	}

	@Override
	public void onThrowable(Throwable error) {
		Messages.showMessageDialog(project,
				MessageBundle.message("action.check.dependencies.error", error.getMessage()),
				MessageBundle.message("action.check.dependencies.error.title"), Messages.getErrorIcon());
	}

	protected abstract @Nullable DependencyUpdates getUpdates();

	protected abstract long getDuration();

}
