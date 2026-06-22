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

package biz.paluch.dap.lookup;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.state.VersionProperty;
import biz.paluch.dap.support.DependencyFileDelegate;
import biz.paluch.dap.support.ProjectBuildContext;
import com.intellij.openapi.project.Project;
import org.jspecify.annotations.Nullable;

/**
 * Value object capturing resolution environment.
 *
 * @param project the IntelliJ project that owns the lookup; must not be
 * {@literal null}.
 * @param buildContext the build context for the anchored file; must not be
 * {@literal null}.
 * @param cache the shared release cache.
 * @param projectState the cached project state.
 * @param versionResolver the Git-ref resolver bound to {@code cache} and
 * {@code projectState}.
 * @author Mark Paluch
 * @see VersionUpgradeLookup
 * @see ArtifactReferenceResolver
 */
public record LookupContext(Project project, ProjectBuildContext buildContext, Cache cache,
		ProjectState projectState, GitVersionResolver versionResolver) {

	/**
	 * Create a {@code LookupContext} for the given project and build context,
	 * resolving the shared cache and project state from the {@link StateService}.
	 * @param project the IntelliJ project.
	 * @param buildContext the build context.
	 * @return a context bound to the project's shared cache and, when the context
	 * is available, its project state.
	 */
	public static LookupContext create(Project project, ProjectBuildContext buildContext) {

		StateService service = StateService.getInstance(project);
		Cache cache = service.getCache();
		ProjectState projectState = service.getProjectState(buildContext.getProjectId());
		return new LookupContext(project, buildContext, cache, projectState,
				new GitVersionResolver(cache));
	}

	/**
	 * Create a {@code LookupContext} for the project behind the given
	 * {@link DependencyFileDelegate} and build context.
	 * @param delegate the delegate whose project owns the lookup; must not be
	 * {@literal null}.
	 * @param buildContext the build context.
	 * @return a context bound to the project's shared cache and, when the context
	 * is available, its project state.
	 * @see #create(Project, ProjectBuildContext)
	 */
	public static LookupContext create(DependencyFileDelegate delegate, ProjectBuildContext buildContext) {
		return create(delegate.getProject(), buildContext);
	}

	public @Nullable ArtifactVersion findCurrentVersion(ArtifactId artifactId) {
		Dependency dependency = projectState.findDependency(artifactId);
		return dependency != null ? dependency.getCurrentVersion() : null;
	}

	public @Nullable VersionProperty findProperty(String propertyName) {
		return projectState.findProperty(propertyName);
	}

}
