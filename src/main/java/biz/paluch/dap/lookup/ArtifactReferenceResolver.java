/*
 * Copyright 2026-present the original author or authors.
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
 * Build-tool strategy for resolving PSI elements and locating dependency sites
 * in one file.
 *
 * <p>{@link VersionUpgradeLookup} supplies the shared lookup facade and
 * delegates build-tool-specific resolution and search behavior to this
 * strategy. An unrecognized element or a declaration that cannot be resolved is
 * represented by {@link ArtifactReference#unresolved()}.
 *
 * <p>Dependency Site Find support is optional. The default search returns empty
 * results, so an empty result does not distinguish an unsupported search from a
 * supported search with no matching sites.
 *
 * @author Mark Paluch
 * @see VersionUpgradeLookup
 * @see ArtifactReference
 */
public interface ArtifactReferenceResolver {

	/**
	 * Resolve the given PSI element into artifact declaration metadata.
	 *
	 * <p>A resolved reference may carry an unversioned declaration.
	 *
	 * @param element the PSI element under inspection.
	 * @return the resolved artifact reference, or
	 * {@link ArtifactReference#unresolved()} if the element is not recognized or
	 * its declaration cannot be resolved.
	 */
	ArtifactReference resolveArtifactReference(PsiElement element);

	/**
	 * Locate the dependency sites in this resolver's file that match the given
	 * query's artifact coordinates or version-property names.
	 *
	 * <p>The caller owns broader file-scope traversal and result aggregation. The
	 * default returns {@link DependencySearchResults#empty() empty results}.
	 *
	 * @param query the Dependency Site Find criteria.
	 * @return the matching hits in this resolver's file, possibly empty.
	 */
	default DependencySearchResults search(DependencySiteQuery query) {
		return DependencySearchResults.empty();
	}

	/**
	 * Return a resolver that never resolves an element and uses the default empty
	 * search.
	 *
	 * @return the unresolved resolver.
	 */
	static ArtifactReferenceResolver unresolved() {
		return element -> ArtifactReference.unresolved();
	}

}
