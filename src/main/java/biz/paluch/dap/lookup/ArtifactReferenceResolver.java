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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SyntaxTraverser;

/**
 * Build-tool-specific component that parses the element under the caret and
 * resolves its current version into an {@link ArtifactReference}.
 *
 * <p>Each build ecosystem (Maven, Gradle, GitHub Actions, NPM, Antora, and the
 * two wrappers) supplies one resolver. The resolver owns the
 * build-tool-specific collaborators (property-expression chains, version
 * catalogs, the Maven project model, Git-ref resolution) needed to resolve a
 * version, and receives its shared services through the {@link LookupContext}
 * it is constructed with.
 *
 * <p>This is the single variation point of the version-upgrade lookup: the
 * surrounding orchestration ({@link VersionUpgradeLookup}) is the same concrete
 * type for every ecosystem. Implementations should treat every element as
 * optional input and return {@link ArtifactReference#unresolved()} for
 * unsupported locations, incomplete PSI, unavailable project state, and
 * unresolved property or catalog indirection rather than throwing.
 *
 * <p>Not to be confused with the Gradle-internal {@code VersionSiteLocator},
 * which locates a {@code GradleVersionSite}; this component resolves a fully
 * formed {@link ArtifactReference} across all ecosystems.
 *
 * @author Mark Paluch
 * @see VersionUpgradeLookup
 * @see LookupContext
 * @see ArtifactReference
 */
public interface ArtifactReferenceResolver {

	/**
	 * Resolve the given PSI element into artifact declaration metadata, including
	 * its current version.
	 * @param element the PSI element under inspection; must not be {@literal null}.
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

	/**
	 * Inline-only search that derives a result purely from per-element resolution:
	 * traverse {@code root}, resolve each element, and keep those resolving to a
	 * query artifact as {@link SiteRole#DECLARATION} hits on the version literal.
	 *
	 * <p>This is the reusable fallback for ecosystems that have no version property
	 * to follow (NPM, Antora, GitHub Actions) and therefore do not override
	 * {@link #search(DependencySiteQuery)}. Hits are deduplicated by target
	 * element.
	 *
	 * @param root the file (or subtree) to scan; must not be {@literal null}.
	 * @param query the version this find is centered on; must not be
	 * {@literal null}.
	 * @param resolve resolves an element into an {@link ArtifactReference}; must
	 * not be {@literal null}.
	 * @return the inline definition hits; never {@literal null}, possibly empty.
	 */
	static DependencySearchResults inlineDefinitions(PsiElement root, DependencySiteQuery query,
			Function<PsiElement, ArtifactReference> resolve) {

		if (query.artifacts().isEmpty()) {
			return DependencySearchResults.empty();
		}

		List<DependencySiteSearchHit> hits = new ArrayList<>();
		Set<PsiElement> seen = new HashSet<>();
		for (PsiElement element : SyntaxTraverser.psiTraverser(root)) {

			ArtifactReference reference = resolve.apply(element);
			if (!reference.isResolved() || !query.artifacts().contains(reference.getArtifactId())) {
				continue;
			}

			ArtifactDeclaration declaration = reference.getDeclaration();
			PsiElement target = declaration.getVersionLiteral() != null ? declaration.getVersionLiteral()
					: declaration.getDeclarationElement();
			if (seen.add(target)) {
				hits.add(DependencySiteSearchHit.declaration(target,
						declaration.getVersion() != null ? declaration.getVersion().toString() : target.getText()));
			}
		}

		return DependencySearchResults.of(hits);
	}

}
