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
import biz.paluch.dap.state.ProjectId;

/**
 * Build-tool agnostic context for the build file currently open in the editor.
 *
 * <p>Provides project identity and release sources without exposing Maven or
 * Gradle-specific APIs to callers.
 *
 * @author Mark Paluch
 */
public interface ProjectBuildContext {

	/**
	 * Return whether this context is backed by a known, importable project.
	 */
	boolean isAvailable();

	/**
	 * Return whether this context is not {@link #isAvailable() available}.
	 * @see #isAvailable()
	 */
	default boolean isAbsent() {
		return !isAvailable();
	}

	/**
	 * Return the build-tool agnostic project identity.
	 * @throws IllegalStateException if the build context is not available.
	 */
	ProjectId getProjectId();

	/**
	 * Return release sources associated with the bound project.
	 * @throws IllegalStateException if the build context is not available.
	 */
	List<ReleaseSource> getReleaseSources();

}
