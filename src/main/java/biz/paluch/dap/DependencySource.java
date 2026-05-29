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
import org.jspecify.annotations.Nullable;

/**
 * SPI implemented by each build-tool integration to feed the shared
 * {@link ProjectStateUpdater}.
 *
 * <p>
 * Implementations are expected to be stateless. Per-run state lives in the
 * {@link IntrospectedDependencies} instance returned by
 * {@link #introspect(Project)} and is shared with the updater for the duration
 * of one run only.
 *
 * @author Mark Paluch
 * @see ProjectStateUpdater
 * @see DependencyScanEntry
 * @see IntrospectedDependencies
 */
public interface DependencySource {

	/**
	 * Enumerate the scan entries owned by this source for the given project.
	 * <p>
	 * Implementations must apply their own file-scope filters here, including
	 * exclusions such as the NPM {@code node_modules} directory.
	 * @param project the IntelliJ project to enumerate against; must not be
	 * {@literal null}.
	 * @return the entries to be processed by the updater.
	 */
	List<DependencyScanEntry> enumerate(Project project);

	/**
	 * Build an entry for the given anchor file or return {@literal null} if this
	 * source does not own the file.
	 * <p>
	 * Used by the updater to route file-scoped invalidation through the same
	 * carrier shape as enumeration.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 * @param file the PSI file to wrap; must not be {@literal null}.
	 */
	@Nullable
	DependencyScanEntry createEntry(Project project, PsiFile file);

	/**
	 * Collect dependencies for the given entry into the provided collector.
	 * <p>
	 * The collector is the same instance the updater later passes to
	 * {@link IntrospectedDependencies#complete(DependencyCollector)} and stores in
	 * the {@link biz.paluch.dap.state.ProjectState}. Implementations must mutate
	 * the provided collector directly and must not replace it with a new
	 * instance.
	 * @param entry the scan entry to collect for; must not be {@literal null}.
	 * @param collector the collector to populate in place; must not be
	 * {@literal null}.
	 */
	void collect(DependencyScanEntry entry, DependencyCollector collector);

	/**
	 * Collect dependencies for the given entry into the provided collector,
	 * giving the source access to the run-scoped
	 * {@link IntrospectedDependencies}.
	 * <p>
	 * The default delegates to {@link #collect(DependencyScanEntry, DependencyCollector)}
	 * and is suitable for integrations that do not accumulate per-entry
	 * introspection state. Integrations that need to feed phase-one state into
	 * their {@link IntrospectedDependencies} should override this method.
	 * @param entry the scan entry to collect for; must not be {@literal null}.
	 * @param collector the collector to populate in place; must not be
	 * {@literal null}.
	 * @param introspected the introspection handle for the current updater run;
	 * must not be {@literal null}.
	 */
	default void collect(DependencyScanEntry entry, DependencyCollector collector,
			IntrospectedDependencies introspected) {
		collect(entry, collector);
	}

	/**
	 * Return a fresh {@link IntrospectedDependencies} instance scoped to one
	 * updater run.
	 * <p>
	 * The default returns the empty instance, suitable for integrations that do
	 * not derive scan-wide metadata.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 */
	default IntrospectedDependencies introspect(Project project) {
		return IntrospectedDependencies.empty();
	}

}
