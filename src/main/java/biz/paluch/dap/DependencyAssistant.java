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
import com.intellij.psi.PsiFile;

/**
 * SPI for build-tool integrations such as Maven and Gradle.
 *
 * <p>Implementations are contributed through the
 * {@code biz.paluch.dap.assistant} extension point and are shared across
 * projects. They should therefore be stateless or hold only immutable
 * configuration.
 *
 * <p>Support checks are expected to be cheap. Expensive parsing and state
 * access belongs in project initialization, dependency scanning, or a
 * {@link ProjectDependencyContext}.
 *
 * @author Mark Paluch
 * @see ProjectDependencyContext
 */
public interface DependencyAssistant {

	/**
	 * Return the stable integration id.
	 * @return the stable id, for example {@code "maven"} or {@code "gradle"}.
	 */
	String getId();

	/**
	 * Return the human-readable integration name.
	 */
	String getDisplayName();

	/**
	 * Return whether this integration applies to the given project.
	 * <p>This predicate must not trigger I/O or PSI access.
	 * @param project the IntelliJ project to inspect.
	 */
	boolean supports(Project project);

	/**
	 * Return whether this integration owns the given file.
	 * <p>This predicate must not trigger I/O or PSI access.
	 * @param file the file to inspect.
	 */
	boolean supports(PsiFile file);

	/**
	 * Initialize project-scoped dependency state for this integration.
	 * @param project the project to initialize.
	 * @param indicator the progress indicator to report to.
	 */
	void initializeState(Project project, ProgressIndicator indicator);

	/**
	 * Scan all build files owned by this integration.
	 * @param project the project to scan.
	 * @param indicator the progress indicator to report to.
	 * @return the aggregated dependency data.
	 */
	DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator);

	/**
	 * Create the file-scoped dependency context for the given anchor file.
	 * <p>Callers are expected to invoke this method only after
	 * {@link #supports(PsiFile)} returned {@literal true}.
	 * @param anchor the build file or catalog file that anchors the operation.
	 * @return a file-scoped context.
	 * @throws IllegalStateException if this integration does not own the file.
	 */
	default ProjectDependencyContext createContext(PsiFile anchor) {
		return createContext(anchor.getProject(), anchor);
	}

	/**
	 * Create the file-scoped dependency context for the given anchor file.
	 * <p>Callers are expected to invoke this method only after
	 * {@link #supports(PsiFile)} returned {@literal true}.
	 * @param project the IntelliJ project.
	 * @param anchor the build file or catalog file that anchors the operation.
	 * @return a file-scoped context.
	 * @throws IllegalStateException if this integration does not own the file.
	 */
	ProjectDependencyContext createContext(Project project, PsiFile anchor);

}
