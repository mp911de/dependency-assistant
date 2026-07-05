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

package biz.paluch.dap.assistant.action;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import biz.paluch.dap.BomMembershipResolver;
import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.assistant.check.VulnerabilityScanner;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.progress.StepsProgressIndicator;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jspecify.annotations.Nullable;

/**
 * Startup activity that initializes all registered dependency integrations.
 *
 * @author Mark Paluch
 */
public class PostStartup implements ProjectActivity {

	@Override
	public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {

		DumbService.getInstance(project).runWhenSmart(() -> {

			ProgressManager.getInstance()
					.run(new Task.Backgroundable(project, MessageBundle.message("post-startup.loading"), false) {

						@Override
						public void run(ProgressIndicator indicator) {
							postStartup(indicator, project);
						}

					});
		});

		return null;
	}

	private void postStartup(ProgressIndicator indicator, Project project) {

		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll(project);
		VulnerabilityScanner scanner = VulnerabilityScanner.create(project);
		StepsProgressIndicator steps = new StepsProgressIndicator(indicator,
				assistants.size() + (scanner.isPresent() ? 1 : 0) + 1);
		ProjectStateIndexer indexer = new ProjectStateIndexer(project, steps);
		steps.setIndeterminate(false);

		for (DependencyAssistant assistant : assistants) {

			steps.setText(MessageBundle.message("post-startup.indexing", assistant.getDisplayName()));
			assistant.prepare(project);
			indexer.readAndUpdateAll(assistant);
			steps.nextStep();
		}

		StateService service = indexer.getService();

		new BomMembershipResolver(project, assistants, service.getCache()).resolveAll(indicator);
		steps.nextStep();

		if (scanner.isPresent()) {
			scanVulnerabilities(scanner, indicator, project, service);
			steps.nextStep();
		}

		if (!service.hasBeenUsed()) {
			return;
		}

		Cache cache = service.getCache();

		if (!cache.hasReleases()) {
			Notifications.releaseMetadataUnavailable(project, RefreshReleaseMetadata::new);
			return;
		}

		Duration age = cache.getAge();
		Instant lastUpdate = cache.getLastUpdate();
		if (age != null && lastUpdate != null && age.compareTo(Duration.ofDays(2)) > 0) {
			Notifications.releaseMetadataStale(project, lastUpdate,
					RefreshReleaseMetadata::new);
		}
	}

	private void scanVulnerabilities(VulnerabilityScanner scanner, ProgressIndicator indicator, Project project,
			StateService service) {

		if (!service.getCache().hasReleases()) {
			return;
		}

		indicator.setText(MessageBundle.message("checker-startup.loading"));
		scanner.scanUsedVersions(indicator);

		if (!project.isDisposed()) {
			DaemonCodeAnalyzer.getInstance(project).restart(MessageBundle.message("post-startup.loading"));
		}
	}

}
