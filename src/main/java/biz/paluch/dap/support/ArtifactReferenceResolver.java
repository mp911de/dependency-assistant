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

package biz.paluch.dap.support;

import com.intellij.psi.PsiElement;

/**
 * Build-tool-specific component that parses the element under the caret and resolves
 * its current version into an {@link ArtifactReference}.
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
 * which locates a {@code GradleVersionSite}; this component resolves a fully formed
 * {@link ArtifactReference} across all ecosystems.
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

}
