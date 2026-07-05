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
 * @param project the IntelliJ project that owns the lookup.
 * @param buildContext the build context for the anchored file.
 * @param service the state service exposing the shared cache.
 * @param projectState the cached project state; can be {@literal null} when no
 * state is available for the build context.
 * @param versionResolver the Git-ref resolver bound to the shared cache.
 * @author Mark Paluch
 * @see VersionUpgradeLookup
 * @see ArtifactReferenceResolver
 */
public record LookupContext(Project project, ProjectBuildContext buildContext, StateService service,
		@Nullable ProjectState projectState, GitVersionResolver versionResolver) {

	/**
	 * Return the shared release cache backing {@link #service()}.
	 */
	public Cache cache() {
		return service.getCache();
	}

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
		ProjectState projectState = service.getProjectState(buildContext.getProjectId());
		return new LookupContext(project, buildContext, service, projectState,
				new GitVersionResolver(service.getCache()));
	}

	/**
	 * Create a {@code LookupContext} for the project behind the given
	 * {@link DependencyFileDelegate} and build context.
	 * @param delegate the delegate whose project owns the lookup {@literal null}.
	 * @param buildContext the build context.
	 * @return a context bound to the project's shared cache and, when the context
	 * is available, its project state.
	 * @see #create(Project, ProjectBuildContext)
	 */
	public static LookupContext create(DependencyFileDelegate delegate, ProjectBuildContext buildContext) {
		return create(delegate.getProject(), buildContext);
	}

	public @Nullable ArtifactVersion findCurrentVersion(ArtifactId artifactId) {
		if (projectState == null) {
			return null;
		}
		Dependency dependency = projectState.findDependency(artifactId);
		return dependency != null ? dependency.getCurrentVersion() : null;
	}

	public @Nullable VersionProperty findProperty(String propertyName) {
		if (projectState == null) {
			return null;
		}
		return projectState.findProperty(propertyName);
	}

}
