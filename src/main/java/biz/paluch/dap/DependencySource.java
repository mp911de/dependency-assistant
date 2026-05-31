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
 * SPI implemented by each build-tool integration to feed the shared
 * {@link ProjectStateIndexer}.
 *
 * <p>Implementations are expected to be stateless. Per-run state lives in the
 * {@link IntrospectedDependencies} instance returned by
 * {@link #introspect(Project)} and is shared with the indexer for the duration
 * of one run only.
 *
 * @author Mark Paluch
 * @see ProjectStateIndexer
 * @see IntrospectedDependencies
 */
public interface DependencySource {

	/**
	 * Enumerate the build or dependency files owned by this source for the given
	 * project.
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
	 * giving the source access to the run-scoped {@link IntrospectedDependencies}.
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
	default void collect(PsiFile anchor, DependencyCollector collector,
			IntrospectedDependencies introspected) {
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

}
