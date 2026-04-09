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
package biz.paluch.dap;

import biz.paluch.dap.artifact.ReleaseSource;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.intellij.openapi.project.Project;

/**
 * Build-tool-agnostic context for the build file currently open in the editor (e.g. {@code pom.xml},
 * {@code build.gradle}, {@code build.gradle.kts}).
 * <p>
 * Provides project identity, remote-repository release sources, and property resolution without coupling callers to
 * Maven-specific or Gradle-specific APIs.
 *
 * @author Mark Paluch
 */
public interface ProjectBuildContext {

	/**
	 * Returns whether this context is backed by a known, importable project.
	 */
	boolean isAvailable();

	/**
	 * Returns the build-tool-agnostic project identity (groupId + artifactId).
	 *
	 * @throws IllegalStateException if {@link #isAvailable()} is {@code false}
	 */
	ProjectId getProjectId();

	/**
	 * Returns the remote-repository {@link ReleaseSource}s for this project so that version resolution can query the
	 * correct repositories.
	 *
	 * @param project the IntelliJ project (needed for credential loading)
	 * @throws IllegalStateException if {@link #isAvailable()} is {@code false}
	 */
	List<ReleaseSource> getReleaseSources(Project project);

	/**
	 * Resolves a project-level property by name (e.g. from Maven {@code <properties>} or {@code gradle.properties}).
	 *
	 * @return the property value, or {@code null} if not defined
	 * @throws IllegalStateException if {@link #isAvailable()} is {@code false}
	 */
	@Nullable
	String getPropertyValue(String name);

}
