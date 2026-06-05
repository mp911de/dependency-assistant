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

import biz.paluch.dap.artifact.DependencyCollector;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * SPI for build-tool integrations such as Maven and Gradle.
 *
 * <p>Implementations are contributed through the
 * {@code biz.paluch.dap.assistant} extension point and are shared across
 * projects. They should therefore be stateless or hold only immutable
 * configuration; per-run state lives in the {@link IntrospectedDependencies}
 * instance returned by {@link #introspect(Project)}.
 *
 * <p>An assistant provides integration points for {@link ProjectStateIndexer}
 * drives to enumerate files an ecosystem owns and collects each into a
 * store-ready {@link DependencyCollector}. The indexer derives the build
 * context for each anchor on demand through
 * {@link #createContext(Project, PsiFile)}.
 *
 * <p>Support checks are expected to be cheap. Expensive parsing and state
 * access belongs in {@link #prepare(Project) preparation}, dependency scanning,
 * or a {@link ProjectDependencyContext}.
 *
 * @author Mark Paluch
 * @see ProjectStateIndexer
 * @see IntrospectedDependencies
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
	 * <p>This conditional must not trigger I/O or PSI access.
	 * @param project the IntelliJ project to inspect; must not be {@literal null}.
	 */
	boolean supports(Project project);

	/**
	 * Return whether this integration owns the given file.
	 * <p>This conditional must not trigger I/O or PSI access.
	 * @param file the file to inspect; must not be {@literal null}.
	 */
	boolean supports(PsiFile file);

	/**
	 * Initialization hook after project startup.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 */
	default void prepare(Project project) {
	}

	/**
	 * Enumerate the anchor files owned by this integration for the given project.
	 * <p>Implementations must apply their own file-scope filters here. The indexer
	 * derives the build context for each anchor on demand.
	 * @param project the IntelliJ project to enumerate against; must not be
	 * {@literal null}.
	 * @return the anchor files to be processed by the indexer.
	 */
	List<PsiFile> enumerate(Project project);

	/**
	 * Collect dependencies for the given anchor file into the provided collector.
	 * <p>The collector is the same instance the indexer later passes to
	 * {@link IntrospectedDependencies#complete(DependencyCollector)} and stores in
	 * the {@link biz.paluch.dap.state.ProjectState}. Implementations must mutate
	 * the provided collector directly and must not replace it with a new instance.
	 * @param anchor the anchor file to collect for; must not be {@literal null}.
	 * @param collector the collector to populate in place; must not be
	 * {@literal null}.
	 */
	void collect(PsiFile anchor, DependencyCollector collector);

	/**
	 * Collect dependencies for the given anchor file into the provided collector,
	 * giving the integration access to the run-scoped
	 * {@link IntrospectedDependencies}.
	 * <p>The default delegates to {@link #collect(PsiFile, DependencyCollector)}
	 * and is suitable for integrations that do not accumulate per-entry
	 * introspection state. Integrations that need to feed phase-one state into
	 * their {@link IntrospectedDependencies} should override this method.
	 * @param anchor the anchor file to collect for; must not be {@literal null}.
	 * @param collector the collector to populate in place; must not be
	 * {@literal null}.
	 * @param introspected the introspection handle for the current indexer run;
	 * must not be {@literal null}.
	 */
	default void collect(PsiFile anchor, DependencyCollector collector, IntrospectedDependencies introspected) {
		collect(anchor, collector);
	}

	/**
	 * Return a fresh {@link IntrospectedDependencies} instance scoped to one
	 * indexer run.
	 * <p>The default returns the empty instance, suitable for integrations that do
	 * not derive scan-wide metadata.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 */
	default IntrospectedDependencies introspect(Project project) {
		return IntrospectedDependencies.empty();
	}

	/**
	 * Create the file-scoped dependency context for the given anchor file.
	 * <p>Invoke only after {@link #supports(PsiFile)} returned {@literal true}.
	 * @param anchor the build file or catalog file that anchors the operation; must
	 * not be {@literal null}.
	 * @return a file-scoped context.
	 * @throws IllegalStateException if this integration does not own the file.
	 */
	default ProjectDependencyContext createContext(PsiFile anchor) {
		return createContext(anchor.getProject(), anchor);
	}

	/**
	 * Create the file-scoped dependency context for the given anchor file.
	 * <p>Invoke only after {@link #supports(PsiFile)} returned {@literal true}.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 * @param anchor the build file or catalog file that anchors the operation; must
	 * not be {@literal null}.
	 * @return a file-scoped context.
	 * @throws IllegalStateException if this integration does not support the file.
	 */
	ProjectDependencyContext createContext(Project project, PsiFile anchor);

}
