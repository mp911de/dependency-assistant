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

import java.util.List;

import biz.paluch.dap.artifact.ReleaseSource;

/**
 * Build-tool-agnostic context for the build file currently open in the editor
 * (e.g. {@code pom.xml}, {@code build.gradle}, {@code build.gradle.kts}).
 * <p>Provides project identity, remote-repository release sources, and property
 * resolution without coupling callers to Maven-specific or Gradle-specific
 * APIs.
 *
 * @author Mark Paluch
 */
public interface ProjectBuildContext {

	/**
	 * Return whether this context is backed by a known, importable project.
	 *
	 * @return {@literal true} if the project context is available; {@literal false}
	 * otherwise.
	 */
	boolean isAvailable();

	/**
	 * Return the build-tool-agnostic project identity (groupId + artifactId).
	 *
	 * @return the project identity; guaranteed to be not {@literal null}.
	 * @throws IllegalStateException if the build context is not
	 * {@link #isAvailable()}.
	 */
	ProjectId getProjectId();

	/**
	 * Return the remote-repository {@link ReleaseSource}s for the bound project so
	 * that version resolution can query the correct repositories.
	 *
	 * @return the release sources; guaranteed to be not {@literal null} but may be
	 * empty.
	 * @throws IllegalStateException if the build context is not
	 * {@link #isAvailable()}.
	 */
	List<ReleaseSource> getReleaseSources();

}
