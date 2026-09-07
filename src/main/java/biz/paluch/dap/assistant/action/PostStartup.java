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

package biz.paluch.dap.assistant.action;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.BomMembershipResolver;
import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.assistant.check.VulnerabilityScanner;
import biz.paluch.dap.metadata.ProjectMetadataIndexer;
import biz.paluch.dap.metadata.RepositoryTagScanner;
import biz.paluch.dap.notify.NotificationActions;
import biz.paluch.dap.notify.NotificationBuilder;
import biz.paluch.dap.notify.Notifications;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.Predicates;
import com.intellij.util.JavaCoroutines;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jspecify.annotations.Nullable;

/**
 * Initializes dependency state and installs build-file edit tracking.
 * <p>Initial collection waits for smart mode. Optional remote scans are skipped
 * in power-save mode. Repository-tag discovery runs separately.
 *
 * @author Mark Paluch
 */
public class PostStartup implements ProjectActivity {

	@Override
	public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {

		return JavaCoroutines.suspendJava(jc -> {
			FlushStateOnEdit.install(project);

			DumbService.getInstance(project).runWhenSmart(() -> {

				new Task.Backgroundable(project, MessageBundle.message("post-startup.loading"), true) {

					@Override
					public void run(ProgressIndicator indicator) {
						postStartup(indicator, project);
					}

				}.queue();
			});
			jc.resume(Unit.INSTANCE);
		}, continuation);
	}

	private void postStartup(ProgressIndicator indicator, Project project) {

		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll(project);
		VulnerabilityScanner scanner = VulnerabilityScanner.create(project);
		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator,
				assistants.size() + 1 + (scanner.isPresent() ? 1 : 0));
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
			if (!PowerSaveMode.isEnabled() && service.getCache().hasReleases()) {
				scanVulnerabilities(scanner, indicator);
			}
			steps.nextStep();
		}

		if (!PowerSaveMode.isEnabled()) {
			ProjectMetadataIndexer metadataIndexer = new ProjectMetadataIndexer(project);
			Map<PackageIdentity, ArtifactVersion> versionMap = new HashMap<>();
			service.doWithDependencies(Predicates.alwaysTrue(), dependency -> {
				CachedArtifact cachedArtifact = service.getCache()
						.findCachedArtifact(dependency.getArtifactId());
				if (cachedArtifact != null && cachedArtifact.getPackageSystem() != null) {
					versionMap.put(cachedArtifact.toPackageIdentity(), dependency.getCurrentVersion());
				}
			});
			metadataIndexer.update(indicator, versionMap);
			scanRepositoryTags(project, service);
		}

		if (!service.hasBeenUsed()) {
			return;
		}

		// MessageBundle.message("post-startup.loading")
		DaemonCodeAnalyzer.getInstance(project).restart();

		Cache cache = service.getCache();

		if (!cache.hasReleases() && cache.shouldNag()) {
			withReminderActions(Notifications.releaseMetadataUnavailable(), project, cache).notify(project);
			return;
		}

		Instant lastUpdate = cache.getLastUpdate();
		if (lastUpdate != null && cache.shouldNag()) {
			withReminderActions(Notifications.releaseMetadataStale(lastUpdate), project, cache).notify(project);
		}
	}

	private static NotificationBuilder withReminderActions(NotificationBuilder builder, Project project,
			Cache cache) {

		return builder
				.action(NotificationActions.refreshReleaseMetadata(() -> refreshReleaseMetadata(project)))
				.action(NotificationActions.notNow(() -> cache.doNotNag(Cache.PLEASE_BE_SILENT_FOR)))
				.action(NotificationActions.notThisWeek(() -> cache.doNotNag(Cache.PLEASE_BE_SILENT_LONGER)))
				.action(NotificationActions.stopNagging(cache::stopNagging));
	}

	private static void refreshReleaseMetadata(Project project) {
		ProgressManager.getInstance().run(new RefreshReleaseMetadata(project));
	}

	private void scanRepositoryTags(Project project, StateService service) {

		RepositoryTagScanner scanner = new RepositoryTagScanner(project, service.getCache());
		new Task.Backgroundable(project, MessageBundle.message("post-startup.repository-scan.loading"), true) {

			@Override
			public void run(ProgressIndicator indicator) {
				scanner.scan(indicator);
			}

		}.queue();
	}

	private void scanVulnerabilities(VulnerabilityScanner scanner, ProgressIndicator indicator) {

		indicator.setText(MessageBundle.message("post-startup.checker-startup.loading"));
		scanner.scanUsedVersions(indicator);
	}

}
