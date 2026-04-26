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
 * Interface to be implemented by Dependency Assistant extensions such as Maven
 * or Gradle to provide Dependency Assistant functionality for retrieving
 * project dependencies, checking for updates, and retrieving release metadata.
 * <p>Implementations are project-independent extension instances. They expose a
 * stable identity, cheap applicability predicates, and factory methods for
 * creating per-project {@link ProjectDependencyContext} handles.
 *
 * @author Mark Paluch
 * @see ProjectDependencyContext
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
	 *
	 * @param project the IntelliJ project to inspect; must not be {@literal null}.
	 * @return {@literal true} if this integration applies; {@literal false}
	 * otherwise.
	 */
	boolean supports(Project project);

	/**
	 * Return whether this integration owns the given file.
	 * <p>This predicate must be cheap and suitable for editor dispatch and action
	 * visibility checks.
	 *
	 * @param file the file to inspect; must not be {@literal null}.
	 * @return {@literal true} if this integration owns the file; {@literal false}
	 * otherwise.
	 */
	boolean supports(VirtualFile file);

	/**
	 * Return whether this integration owns the given file.
	 * <p>This predicate must be cheap and suitable for editor dispatch and action
	 * visibility checks.
	 *
	 * @param file the file to inspect; must not be {@literal null}.
	 * @return {@literal true} if this integration owns the file; {@literal false}
	 * otherwise.
	 */
	boolean supports(PsiFile file);

	/**
	 * Bring the in-memory dependency state up to date.
	 *
	 * @param indicator the progress indicator; must not be {@literal null}.
	 */
	void initializeState(Project project, ProgressIndicator indicator);

	DependencyCollector getAllDependencies(Project project, ProgressIndicator indicator);

	/**
	 * Create the file-scoped dependency context for the given anchor file.
	 * <p>Callers are expected to invoke this method only after
	 * {@link #supports(PsiFile)} returned {@literal true}.
	 *
	 * @param project the IntelliJ project; must not be {@literal null}.
	 * @param anchor the build file or catalog file that owns the operation; must
	 * not be {@literal null}.
	 * @return a file-scoped context; guaranteed to be not {@literal null}.
	 * @throws IllegalStateException if this integration does not own the file.
	 */
	ProjectDependencyContext createContext(Project project, PsiFile anchor);


}
