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

package biz.paluch.dap.metadata;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedMetadata;
import biz.paluch.dap.state.CachedRepository;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.psi.util.CachedValuesManager;

/**
 * Cache-only access to upstream metadata and platform links.
 * <p>This service performs no inspection or network access. Its modification
 * count follows the project cache so consumers can invalidate derived metadata.
 *
 * @author Mark Paluch
 * @see ProjectMetadata
 */
@Service(Service.Level.PROJECT)
public final class ProjectMetadataService implements ModificationTracker {

	private final Project project;

	private final Cache cache;

	public ProjectMetadataService(Project project) {
		this.project = project;
		this.cache = StateService.getInstance(project).getCache();
	}

	public ProjectMetadataService(Project project, Cache cache) {
		this.project = project;
		this.cache = cache;
	}

	public static ProjectMetadataService getInstance(Project project) {
		return project.getService(ProjectMetadataService.class);
	}

	/**
	 * Return metadata cached on the declaration.
	 * <p>File and project-cache changes invalidate the result.
	 */
	public static ProjectMetadata getMetadata(ArtifactDeclaration declaration) {

		PsiElement psiElement = declaration.getDeclarationElement();
		ProjectMetadataService service = ProjectMetadataService.getInstance(psiElement.getProject());

		return CachedValuesManager.getCachedValue(psiElement, () -> CachedValueProvider.Result
				.create(service.getMetadata(declaration.getArtifactId()), psiElement, service));
	}

	@Override
	public long getModificationCount() {
		return cache.getModificationCount();
	}

	/**
	 * Look up metadata by artifact coordinates, ignoring the package system.
	 * @see #getMetadata(ArtifactId)
	 */
	public ProjectMetadata getMetadata(PackageIdentity identity) {
		return getMetadata(identity.getArtifactId());
	}

	/**
	 * Resolve captured metadata and platform links.
	 * <p>A name repeating the artifact ID is replaced by the captured description.
	 * @return the snapshot, or {@link ProjectMetadata#absent()} if no metadata is
	 * captured.
	 */
	public ProjectMetadata getMetadata(ArtifactId artifactId) {

		CachedArtifact cachedArtifact = cache.findCachedArtifact(artifactId);

		if (cachedArtifact == null) {
			return ProjectMetadata.absent();
		}

		CachedMetadata cachedMetadata = cachedArtifact.getProjectMetadata();

		if (cachedMetadata == null) {
			return ProjectMetadata.absent();
		}

		RepositoryConnection connection = null;
		IssueTracker tracker = null;
		if (StringUtils.hasText(cachedMetadata.getRepositoryUrl())) {
			connection = Platform.findConnection(cachedMetadata.getRepositoryUrl(), null);
		}

		String issueTrackerUrl = cachedMetadata.getIssueTrackerUrl();
		if (StringUtils.hasText(issueTrackerUrl)) {
			tracker = IssueTracker.parse(issueTrackerUrl);
		}
		if (tracker == null && connection != null) {
			tracker = Platform.findIssueTracker(connection, null);
		}

		List<String> tags = List.of();

		if (connection != null) {
			CachedRepository repository = cache.findRepository(connection.getKey());
			if (repository != null) {
				tags = repository.getTags();
			}
		}

		String name = artifactId.artifactId()
				.equals(cachedMetadata.getProjectName()) ? cachedMetadata.getProjectDescription()
						: cachedMetadata.getProjectName();

		ProjectRepository repository = connection != null ? connection
				.createRepository(project) : null;
		return ProjectMetadata.from(name, connection, repository, tracker, tags);
	}

	/**
	 * Return the captured name, or an empty {@link ProjectName} if absent.
	 */
	public ProjectName getProjectName(ArtifactId artifactId) {

		CachedArtifact cachedArtifact = cache.findCachedArtifact(artifactId);
		if (cachedArtifact == null) {
			return ProjectName.empty(artifactId);
		}

		CachedMetadata cachedMetadata = cachedArtifact.getProjectMetadata();
		if (cachedMetadata == null) {
			return ProjectName.empty(artifactId);
		}
		return ProjectName.of(artifactId, cachedMetadata.getProjectName());
	}

}
