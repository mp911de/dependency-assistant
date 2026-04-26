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
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * File-scoped operational handle for one build-tool integration, obtained from
 * a {@link DependencyAssistant} after it has confirmed ownership of the anchor
 * file.
 * <p>A context is bound to a specific build file (e.g. {@code pom.xml},
 * {@code build.gradle}, or {@code build.gradle.kts}) and exposes the operations
 * required by the shared IDE-integration layer:
 * <ul>
 * <li><em>State invalidation</em> — {@link #invalidateState(PsiFile)} triggers
 * a targeted re-scan when a file changes on save, so that annotators and
 * completion contributors reflect the latest content without a full project
 * reload.</li>
 * <li><em>Dependency scanning</em> — {@link #scanDependencies} parses the build
 * files reachable from the anchor file and returns a populated
 * {@link DependencyCollector} for version-resolution and update purposes.</li>
 * <li><em>Editor reference resolution</em> — {@link #resolveReference} and
 * {@link #getLookup} map a {@link PsiElement} at the cursor position to the
 * artifact it represents, enabling annotators, line-marker providers, and
 * completion contributors to operate without build-tool-specific
 * knowledge.</li>
 * <li><em>Update application</em> — {@link #applyUpdates} writes selected
 * version changes back to the appropriate build files via the IntelliJ document
 * API.</li>
 * </ul>
 * <p>Contexts are lightweight and created on demand by the dispatcher; they
 * must not cache mutable project state themselves.
 *
 * @author Mark Paluch
 * @see DependencyAssistant
 * @see DependencyCollector
 */
public interface ProjectDependencyContext extends ProjectBuildContext {

	/**
	 * Invalidate and re-collect the state affected by the changed file.
	 * <p>Called from a save-listener after a build file is written. Implementations
	 * should update only the portion of the in-memory state that the file
	 * contributes, rather than re-scanning the entire project.
	 *
	 * @param file the PSI file that was saved; must not be {@literal null}.
	 */
	void invalidateState(PsiFile file);

	/**
	 * Scan the build files reachable from the anchor file and return the aggregated
	 * dependency data.
	 * <p>Invoked from a background thread (e.g. during a user-triggered dependency
	 * check). The returned {@link DependencyCollector} contains all versioned
	 * usages, version-management declarations, property names, and release sources
	 * discovered across the scanned files.
	 *
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 * @return the collected dependency data; guaranteed to be not {@literal null}.
	 */
	DependencyCollector scanDependencies(ProgressIndicator indicator);

	/**
	 * Resolve the artifact reference at the given PSI element.
	 * <p>Used by annotators and line-marker providers to map the element under the
	 * cursor to its artifact coordinates without build-tool-specific knowledge.
	 * Returns {@link ArtifactReference#unresolved()} when the element does not
	 * correspond to a known dependency version string.
	 *
	 * @param element the PSI element under inspection; must not be {@literal null}.
	 * @return the resolved reference or {@link ArtifactReference#unresolved()};
	 * guaranteed to be not {@literal null}.
	 */
	ArtifactReference resolveReference(PsiElement element);

	/**
	 * Return the version-upgrade lookup for the given PSI element.
	 * <p>Provides annotators, line-marker providers, and completion contributors
	 * with access to the cached release data and upgrade-strategy logic for the
	 * artifact at the given element's position.
	 *
	 * @param element the PSI element at the cursor position; must not be
	 * {@literal null}.
	 * @return the lookup for the element; guaranteed to be not {@literal null}.
	 */
	VersionUpgradeLookupSupport getLookup(PsiElement element);

	/**
	 * Return the remote-repository {@link ReleaseSource}s for the bound project.
	 * <p>Unlike the inherited {@link ProjectBuildContext#getReleaseSources()}, a
	 * {@link ProjectDependencyContext} is always associated with a valid,
	 * applicable project, so this method never throws.
	 *
	 * @return the release sources; guaranteed to be not {@literal null} but may be
	 * empty.
	 */
	@Override
	List<ReleaseSource> getReleaseSources();

	/**
	 * Apply the given dependency updates to the appropriate build files.
	 * <p>Writes version changes back to the build files via the IntelliJ document
	 * API inside a write action. Each update carries the PSI location of the
	 * version string to replace, so implementations can apply changes without
	 * re-parsing the files.
	 *
	 * @param psiFile the anchor PSI file in whose write action the updates are
	 * applied; must not be {@literal null}.
	 * @param updates the updates to apply; must not be {@literal null}.
	 */
	void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates);

}
