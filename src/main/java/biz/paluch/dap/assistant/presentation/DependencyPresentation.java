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
 * Presentation model providing the display names for a dependency.
 *
 * <p>Presentations are immutable value objects and safe to share across
 * threads.
 *
 * @author Mark Paluch
 * @see IconDependencyPresentation
 * @see DependencyPresentationFactory
 */
public interface DependencyPresentation extends HasArtifactId, HasPackageIdentity {

	/**
	 * Return the canonical display name: the {@link #getDependencyName() dependency
	 * name} when present, the {@link #getShortArtifactId()} rendered artifact
	 * coordinates} otherwise.
	 *
	 * @return the resolved display name.
	 */
	String getDisplayName();

	/**
	 * Return the dependency name as HTML-ready text.
	 * <p>Favors {@link #getDependencyName()} if present, otherwise
	 * {@link #getDisplayName()} wrapped in quotes to indicate the raw artifact
	 * coordinates.
	 * @return the dependency name.
	 */
	default String getHtmlDisplayName() {
		return hasDependencyName() ? getDependencyName() : "'" + getDisplayName() + "'";
	}

	/**
	 * @return the short {@link #getArtifactId() artifact id} without the group id.
	 */
	String getShortArtifactId();

	/**
	 * Fully qualified artifact coordinates.
	 *
	 * @return fully qualified artifact coordinates.
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
	 * Return the project name as captured from the artifact's metadata.
	 *
	 * @return the project name.
	 */
	ProjectName getProjectName();

	/**
	 * Create a presentation from the artifact coordinates of the given source,
	 * without dependency or project names.
	 *
	 * @param aware the source providing the artifact coordinates.
	 * @return a presentation rendering the plain artifact coordinates.
	 * @see #of(PackageIdentity)
	 */
	public static DependencyPresentation of(HasPackageIdentity aware) {
		return of(aware.getPackageIdentity());
	}

	/**
	 * Create a presentation for the given artifact coordinates, without dependency
	 * or project names. {@link #getDisplayName()} falls back to the coordinate
	 * representation.
	 *
	 * @param pkg the artifact coordinates to present.
	 * @return a presentation rendering the plain artifact coordinates.
	 */
	public static DependencyPresentation of(PackageIdentity pkg) {
		return SimpleDependencyPresentation.of(pkg, null, ProjectName.empty(pkg.getArtifactId()));
	}

	/**
	 * Create a fully populated presentation.
	 *
	 * @param pkg the artifact coordinates to present.
	 * @param dependencyName the curated dependency name; can be {@literal null} if
	 * no curated name is known.
	 * @param projectName the project name from the artifact's metadata.
	 * @return the presentation carrying the given name facets.
	 */
	static DependencyPresentation of(PackageIdentity pkg, @Nullable String dependencyName, ProjectName projectName) {
		return new SimpleDependencyPresentation(pkg, dependencyName, projectName);
	}

}

