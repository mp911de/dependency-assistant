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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.progress.StepsProgressIndicator;
import org.jspecify.annotations.Nullable;

/**
 * Background task that refreshes release metadata for registered integrations.
 *
 * @author Mark Paluch
 */
class RefreshReleaseMetadata extends Task.Backgroundable {

	private static final Logger LOG = Logger.getInstance(RefreshReleaseMetadata.class);

	private final Project project;

	private volatile @Nullable List<ArtifactId> updates;

	private volatile long duration;

	RefreshReleaseMetadata(Project project) {
		super(project, MessageBundle.message("action.refresh-releases.task"), true);
		this.project = project;
	}

	@Override
	public void run(ProgressIndicator indicator) {

		long startNs = System.nanoTime();
		DependencyCheck dependencyCheck = new DependencyCheck(project);
		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll(project);
		StepsProgressIndicator steps = new StepsProgressIndicator(indicator, 1 + assistants.size());
		steps.setText(MessageBundle.message("action.index-dependencies.analyzing"));
		steps.setIndeterminate(false);

		List<ReleaseSources> sources = new ArrayList<>();

		ReadAction.nonBlocking(() -> {
			for (DependencyAssistant assistant : assistants) {
				steps.setText2(MessageBundle.message("action.index-dependencies.analyzing.assistant",
						assistant.getDisplayName()));
				if (steps.isCanceled()) {
					return null;
				}
				sources.addAll(dependencyCheck.collectDependencies(steps, assistant));
				steps.nextStep();
			}

			return null;
		}).inSmartMode(project).executeSynchronously();

		steps.setText2("");
		if (steps.isCanceled()) {
			return;
		}

		Map<ArtifactId, Releases> result = dependencyCheck.getReleases(steps, sources,
				ReleaseResolver.refresh());
		steps.nextStep();
		updates = result.keySet().stream().toList();
		duration = TimeoutUtil.getDurationMillis(startNs);
	}

	@Override
	public void onSuccess() {

		List<ArtifactId> result = getUpdates();
		if (result == null || project.isDisposed()) {
			return;
		}

		Notifications.releaseMetadataRefreshed(project, result, getDuration());
		DaemonCodeAnalyzer.getInstance(project)
				.restart(MessageBundle.message("action.refresh-releases.task.done.title"));
	}

	@Override
	public void onThrowable(Throwable error) {
		LOG.warn("Dependency release metadata refresh failed", error);
		Notifications.error(project,
				MessageBundle.message("action.refresh-releases.task.error", Notifications.errorMessage(error)));
	}

	private @Nullable List<ArtifactId> getUpdates() {
		return updates;
	}

	private long getDuration() {
		return duration;
	}

}
