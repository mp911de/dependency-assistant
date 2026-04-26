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

import biz.paluch.dap.artifact.DependencyCollector;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * SPI for build-tool integrations that provide dependency-assistant
 * functionality — dependency collection, release-metadata retrieval, and
 * in-editor version updates — for a specific build tool such as Maven or
 * Gradle.
 * <p>Implementations are registered as IntelliJ extension-point contributions
 * under the name {@code biz.paluch.dap.assistant} and are discovered at runtime
 * by {@link biz.paluch.dap.support.DependencyAssistantDispatcher}. Each
 * registered instance is shared across all projects (extension-point lifetime),
 * so implementations must be stateless or hold only immutable configuration.
 * <p>The interface exposes four groups of methods:
 * <ul>
 * <li><em>Identity</em> — {@link #getId()} and {@link #getDisplayName()}
 * provide a stable programmatic key and a human-readable label.</li>
 * <li><em>Applicability predicates</em> — {@link #supports(Project)},
 * {@link #supports(VirtualFile)}, and {@link #supports(PsiFile)} allow the
 * dispatcher to route operations without allocating a context. These methods
 * must be cheap (no I/O, no PSI access) and are called on every editor
 * event.</li>
 * <li><em>State management</em> — {@link #initializeState} and
 * {@link #getAllDependencies} drive background scanning and project-level state
 * population, typically invoked during post-startup or on user action.</li>
 * <li><em>Context factory</em> — {@link #createContext(Project, PsiFile)}
 * creates a {@link ProjectDependencyContext} scoped to the given file. Callers
 * must guard the call behind a matching {@link #supports(PsiFile)} check.</li>
 * </ul>
 *
 * @author Mark Paluch
 * @see ProjectDependencyContext
 * @see biz.paluch.dap.support.DependencyAssistantDispatcher
 */
public interface DependencyAssistant {

	/**
	 * Return the stable integration id.
	 *
	 * @return the stable id, such as {@code "maven"} or {@code "gradle"}.
	 */
	String getId();

	/**
	 * Return the human-readable integration name.
	 *
	 * @return the display name.
	 */
	String getDisplayName();

	/**
	 * Return whether this integration applies to the given project.
	 * <p>This predicate must be cheap and must not trigger I/O or PSI access. It is
	 * called on every editor event and action-visibility check.
	 *
	 * @param project the IntelliJ project to inspect; must not be {@literal null}.
	 * @return {@literal true} if this integration applies; {@literal false}
	 * otherwise.
	 */
	boolean supports(Project project);

	/**
	 * Return whether this integration owns the given file.
	 * <p>This predicate must be cheap and must not trigger I/O or PSI access. It is
	 * called on every editor event and action-visibility check.
	 *
	 * @param file the file to inspect; must not be {@literal null}.
	 * @return {@literal true} if this integration owns the file; {@literal false}
	 * otherwise.
	 */
	boolean supports(VirtualFile file);

	/**
	 * Return whether this integration owns the given file.
	 * <p>This predicate must be cheap and must not trigger I/O or PSI access. It is
	 * called on every editor event and action-visibility check.
	 *
	 * @param file the file to inspect; must not be {@literal null}.
	 * @return {@literal true} if this integration owns the file; {@literal false}
	 * otherwise.
	 */
	boolean supports(PsiFile file);

	/**
	 * Bring the in-memory dependency state up to date for the given project.
	 * <p>Invoked from a background thread during post-startup indexing or on an
	 * explicit user refresh action. Implementations should populate the
	 * project-level {@link biz.paluch.dap.state.DependencyAssistantService} cache
	 * and must report progress through the supplied indicator.
	 *
	 * @param project the project to initialize; must not be {@literal null}.
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 */
	void initializeState(Project project, ProgressIndicator indicator);

	/**
	 * Scan all build files owned by this integration in the given project and
	 * return the aggregated dependency data.
	 * <p>Invoked from a background thread. The returned {@link DependencyCollector}
	 * contains all versioned usages, version-management declarations, property
	 * names, and release sources discovered across the project's build files.
	 *
	 * @param project the project to scan; must not be {@literal null}.
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 * @return the aggregated dependency data; guaranteed to be not {@literal null}.
	 */
	DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator);

	/**
	 * Create the file-scoped dependency context for the given anchor file.
	 * <p>Callers are expected to invoke this method only after
	 * {@link #supports(PsiFile)} returned {@literal true}.
	 *
	 * @param project the IntelliJ project; must not be {@literal null}.
	 * @param anchor the build file or catalog file that anchors the operation; must
	 * not be {@literal null}.
	 * @return a file-scoped context; guaranteed to be not {@literal null}.
	 * @throws IllegalStateException if this integration does not own the file.
	 */
	ProjectDependencyContext createContext(Project project, PsiFile anchor);

}
