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

package biz.paluch.dap.metadata;

import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.state.ProjectMetadataInspector;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread;
import org.jspecify.annotations.Nullable;

/**
 * Metadata indexer.
 *
 * @author Mark Paluch
 * @see ProjectMetadataInspector
 * @see ProjectMetadataService
 */
public class ProjectMetadataIndexer {

	private final Project project;

	private final StateService stateService;

	private final List<ProjectMetadataInspector> metadataInspectors;

	public ProjectMetadataIndexer(Project project) {
		this(project, StateService.getInstance(project), ProjectMetadataInspector.EP_NAME.getExtensionList());
	}

	ProjectMetadataIndexer(Project project, StateService stateService,
			List<ProjectMetadataInspector> metadataInspectors) {
		this.project = project;
		this.stateService = stateService;
		this.metadataInspectors = metadataInspectors;
	}

	/**
	 * Post-fetch enrichment stage: run metadata inspection for the checked
	 * artifacts whose metadata is stale, absent, or version-drifted, and mirror the
	 * tag names of git-backed artifacts into the cached tag list.
	 *
	 * @param indicator the progress indicator used for cancellation.
	 * @param dependencyVersions the artifacts of the current check.
	 */
	@RequiresBackgroundThread
	public void update(ProgressIndicator indicator, Map<PackageIdentity, ArtifactVersion> dependencyVersions) {

		Cache cache = stateService.getCache();

		for (Map.Entry<PackageIdentity, ArtifactVersion> entry : dependencyVersions.entrySet()) {

			indicator.checkCanceled();
			CachedArtifact cachedArtifact = cache.findCachedArtifact(entry.getKey());
			if (cachedArtifact == null) {
				continue;
			}

			if (!cache.requiresMetadataRefresh(cachedArtifact)) {
				continue;
			}

			CachedMetadata cachedMetadata = refreshMetadata(entry.getKey(), entry.getValue(), indicator);

			if (cachedMetadata == null) {
				continue;

			}
			cache.doWithArtifact(entry.getKey(), it -> {
				it.updateProjectMetadata(cachedMetadata, cache.now());
			});
		}
	}

	private @Nullable CachedMetadata refreshMetadata(PackageIdentity pkg,
			ArtifactVersion version, ProgressIndicator indicator) {

		for (ProjectMetadataInspector inspector : metadataInspectors) {
			if (inspector.supports(pkg.getPackageSystem())) {
				return inspector.inspect(project, pkg.getArtifactId(), version, indicator);
			}
		}
		return null;
	}

}
