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

package biz.paluch.dap.lookup;

import biz.paluch.dap.support.ArtifactReference;
import com.intellij.psi.PsiElement;

/**
 * Build-tool-specific component that parses the element under the caret and
 * resolves its current version into an {@link ArtifactReference}.
 *
 * <p>Each build ecosystem (Maven, Gradle, GitHub Actions, NPM, Antora, and the
 * two wrappers) supplies one resolver. The resolver owns the
 * build-tool-specific collaborators (property-expression chains, version
 * catalogs, the Maven project model, Git-ref resolution) needed to resolve a
 * version, and receives only the state or resolution services it directly uses.
 *
 * <p>This is the single variation point of the version-upgrade lookup: the
 * surrounding orchestration ({@link VersionUpgradeLookup}) is the same concrete
 * type for every ecosystem. Implementations should treat every element as
 * optional input and return {@link ArtifactReference#unresolved()} for
 * unsupported locations, incomplete PSI, unavailable project state, and
 * unresolved property or catalog indirection rather than throwing.
 *
 * @author Mark Paluch
 * @see VersionUpgradeLookup
 * @see ArtifactReference
 */
public interface ArtifactReferenceResolver {

	/**
	 * Resolve the given PSI element into artifact declaration metadata, including
	 * its current version.
	 * @param element the PSI element under inspection.
	 * @return a resolved artifact reference, or
	 * {@link ArtifactReference#unresolved()}.
	 */
	ArtifactReference resolveArtifactReference(PsiElement element);

	/**
	 * Locate every site within this resolver's file that participates in the given
	 * {@link DependencySiteQuery query}'s version: where the version is defined and
	 * where its version property or catalog accessor is referenced.
	 *
	 * <p>This is a per-file search; the surrounding orchestration drives one
	 * resolver per file across a narrowed set and aggregates the results. Each
	 * returned {@link DependencySiteSearchHit} carries the role its element plays,
	 * assigned by the ecosystem. The default returns
	 * {@link DependencySearchResults#empty() empty} results for ecosystems that do
	 * not yet support the find.
	 *
	 * @param query the version this find is centered on; must not be
	 * {@literal null}.
	 * @return the hits in this resolver's file; never {@literal null}, possibly
	 * empty.
	 */
	default DependencySearchResults search(DependencySiteQuery query) {
		return DependencySearchResults.empty();
	}

}
