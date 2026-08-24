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

package biz.paluch.dap.assistant.presentation;

import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.metadata.ProjectName;
import biz.paluch.dap.rule.DependencyRule;
import org.jspecify.annotations.Nullable;

/**
 * Detached display facts for a dependency package.
 *
 * <p>A presentation retains the {@link PackageIdentity}, its package-system
 * coordinate labels, an optional curated dependency name, and a possibly empty
 * {@link ProjectName}. The dependency name is the canonical display name when
 * present. The project name remains a separate facet for surfaces that choose
 * to show metadata provenance.
 *
 * <p>Instances created through this interface or
 * {@link DependencyPresentationFactory} are immutable snapshots. They do not
 * retain dependencies, rules, settings, or metadata services.
 *
 * @author Mark Paluch
 * @see IconDependencyPresentation
 * @see DependencyPresentationFactory
 */
public interface DependencyPresentation extends HasArtifactId, HasPackageIdentity {

	/**
	 * Return the canonical display name: the {@link #getDependencyName() dependency
	 * name} when present, or the {@link #getShortArtifactId() short artifact
	 * identifier} otherwise.
	 *
	 * @return the resolved display name.
	 */
	String getDisplayName();

	/**
	 * Return the display name used in HTML message templates.
	 *
	 * <p>The curated dependency name is returned unchanged. A fallback artifact
	 * identifier is wrapped in single quotes. The result is not HTML-escaped.
	 * Callers embedding it as raw HTML must escape untrusted names.
	 *
	 * @return the dependency display name for an HTML message template.
	 */
	default String getHtmlDisplayName() {
		return hasDependencyName() ? getDependencyName() : "'" + getDisplayName() + "'";
	}

	/**
	 * Return the package-system-specific name component of the
	 * {@link #getArtifactId() artifact coordinates}.
	 *
	 * @return the short artifact identifier without its group component.
	 */
	String getShortArtifactId();

	/**
	 * Return the artifact coordinates in the notation of the package system.
	 *
	 * @return the rendered artifact coordinates.
	 */
	String getCoordinates();

	/**
	 * Return whether this presentation carries a curated dependency name.
	 *
	 * @return {@literal true} if a dependency name is present; {@literal false}
	 * otherwise.
	 */
	boolean hasDependencyName();

	/**
	 * Return the curated dependency name.
	 * <p>Either derived from {@link DependencyRule#getDependencyName()} or
	 * {@link biz.paluch.dap.state.ApplicationSettings#findNameHint(PackageIdentity)}.
	 *
	 * @return the dependency name.
	 * @throws IllegalStateException if no dependency name is present.
	 * @see #hasDependencyName()
	 */
	String getDependencyName();

	/**
	 * Return the display policy for the project name captured from artifact
	 * metadata.
	 *
	 * @return the project-name policy, possibly empty when no name was captured.
	 */
	ProjectName getProjectName();

	/**
	 * Create a coordinate-only presentation from the package identity of the given
	 * source.
	 *
	 * @param aware the source providing the package identity.
	 * @return a detached presentation without dependency or project names.
	 * @see #of(PackageIdentity)
	 */
	public static DependencyPresentation of(HasPackageIdentity aware) {
		return of(aware.getPackageIdentity());
	}

	/**
	 * Create a coordinate-only presentation for the given package identity.
	 * {@link #getDisplayName()} falls back to the short artifact identifier.
	 *
	 * @param pkg the package identity to present.
	 * @return a detached presentation without dependency or project names.
	 */
	public static DependencyPresentation of(PackageIdentity pkg) {
		return SimpleDependencyPresentation.of(pkg, null, ProjectName.empty(pkg.getArtifactId()));
	}

	/**
	 * Create a presentation carrying the given detached name facets.
	 *
	 * @param pkg the package identity to present.
	 * @param dependencyName the curated dependency name, or {@literal null} when no
	 * name is known.
	 * @param projectName the project-name policy derived from artifact metadata.
	 * @return the presentation carrying the given name facets.
	 */
	static DependencyPresentation of(PackageIdentity pkg, @Nullable String dependencyName, ProjectName projectName) {
		return new SimpleDependencyPresentation(pkg, dependencyName, projectName);
	}

}
