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

import java.util.List;

import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.state.ProjectId;

/**
 * Build-tool-agnostic view of the dependency context bound to a project build
 * file.
 *
 * <p>This contract is intentionally narrower than a full integration context:
 * it exposes only the stable project identity and release-source topology
 * needed by shared state, lookup, and dependency-check infrastructure.
 * Build-tool specific services such as parsing, PSI rewriting, and UI behavior
 * remain with the concrete integration layer.
 *
 * <p>A context may be unavailable when the anchor file is outside an imported
 * or otherwise supported project model. Callers that operate opportunistically
 * over arbitrary PSI should check {@link #isAvailable()} before accessing
 * project state or release sources.
 *
 * <p>The returned {@link ProjectId} identifies where dependency state is
 * stored, while {@link ReleaseSource release sources} describe where version
 * candidates should be obtained for dependencies declared in that context. Both
 * are scoped to the same logical dependency domain, which may be a Maven
 * project, a Gradle build file, a {@code package.json}, or a workflow
 * descriptor.
 *
 * @author Mark Paluch
 * @see ProjectId
 * @see ReleaseSource
 */
public interface ProjectBuildContext {

	/**
	 * Return whether this context is backed by a supported project model.
	 * <p>Unavailable contexts are sentinels used by integrations that inspect files
	 * before a build model, package descriptor, or repository context can be
	 * established.
	 */
	boolean isAvailable();

	/**
	 * Return whether this context cannot provide project state or release-source
	 * metadata.
	 * @see #isAvailable()
	 */
	default boolean isAbsent() {
		return !isAvailable();
	}

	/**
	 * Return the build-tool-agnostic identity used for dependency state.
	 * <p>The identity must use the same scoping rules as
	 * {@link #getReleaseSources()} so cached dependencies, property mappings, and
	 * release lookups refer to the same dependency domain.
	 * @throws IllegalStateException if the build context is not available.
	 */
	ProjectId getProjectId();

	/**
	 * Return the version of the project or build file.
	 * <p>The value is the project's own declared version, not the version of any
	 * dependency. Integrations that cannot determine a self-version return
	 * {@link Versioned#unversioned()}.
	 *
	 * @return the project version, or {@link Versioned#unversioned()} when the
	 * version can not be determined.
	 */
	default Versioned getProjectVersion() {
		return Versioned.unversioned();
	}

	/**
	 * Return the release sources associated with this dependency domain.
	 * <p>The returned sources are integration-specific adapters hidden behind the
	 * common {@link ReleaseSource} contract. They should reflect the repositories,
	 * registries, or hosting services that apply to dependencies owned by
	 * {@link #getProjectId()}.
	 * @throws IllegalStateException if the build context is not available.
	 */
	List<ReleaseSource> getReleaseSources();

}
