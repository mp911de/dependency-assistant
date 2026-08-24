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
import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.assistant.check.VulnerabilityScanner;
import biz.paluch.dap.metadata.ProjectMetadataIndexer;
import biz.paluch.dap.metadata.RepositoryTagScanner;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.PowerSaveMode;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.openapi.util.Predicates;
import com.intellij.util.progress.StepsProgressIndicator;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jspecify.annotations.Nullable;

/**
 * Project startup activity that waits for smart mode and queues initial
 * dependency-state population for each applicable integration.
 *
 * <p>The background pass prepares and indexes the integrations, resolves Bill
 * of Materials membership, and restarts highlighting. Outside power-save mode
 * it also performs the configured vulnerability and project-metadata scans.
 * Missing or stale release metadata is reported after initialization when the
 * dependency state has been used.
 *
 * <p>Repository-tag discovery runs as a separate background task and does not
 * delay completion of the startup pass.
 *
 * @author Mark Paluch
 */
public class PostStartup implements ProjectActivity {

	@Override
	public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {

		DumbService.getInstance(project).runWhenSmart(() -> {

			new Task.Backgroundable(project, MessageBundle.message("post-startup.loading"), true) {

						@Override
						public void run(ProgressIndicator indicator) {
							postStartup(indicator, project);
						}

			}.queue();
		});

		return null;
	}

	private void postStartup(ProgressIndicator indicator, Project project) {

		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll(project);
		VulnerabilityScanner scanner = VulnerabilityScanner.create(project);
		StepsProgressIndicator steps = new StepsProgressIndicator(indicator,
				assistants.size() + (scanner.isPresent() ? 1 : 0));
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
			if (!PowerSaveMode.isEnabled()) {
				scanVulnerabilities(scanner, indicator, project, service);
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
			Notifications.releaseMetadataUnavailable(project, RefreshReleaseMetadata::new, cache::doNotNag);
			return;
		}

		Instant lastUpdate = cache.getLastUpdate();
		if (lastUpdate != null && cache.shouldNag()) {
			Notifications.releaseMetadataStale(project, lastUpdate,
					RefreshReleaseMetadata::new, cache::doNotNag);
		}
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

	private void scanVulnerabilities(VulnerabilityScanner scanner, ProgressIndicator indicator, Project project,
			StateService service) {

		if (!service.getCache().hasReleases()) {
			return;
		}

		indicator.setText(MessageBundle.message("post-startup.checker-startup.loading"));
		scanner.scanUsedVersions(indicator);
	}

}
