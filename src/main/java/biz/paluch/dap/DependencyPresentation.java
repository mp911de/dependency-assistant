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

package biz.paluch.dap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.HasArtifactId;
import org.jspecify.annotations.Nullable;

/**
 * Presentation model providing the display names for a dependency as rendered
 * in editor hints, completion, documentation, and the review dialog.
 *
 * <p>A presentation carries up to three name facets:
 * <ul>
 * <li>{@link #getArtifactIdDisplayName()}: the artifact coordinates rendered in
 * the notation of the declaring build system. Always present.</li>
 * <li>{@link #getDependencyName()}: a curated, human-friendly dependency name,
 * typically sourced from a dependency rule or derived from the accepted project
 * name. Optional.</li>
 * <li>{@link #getProjectName()}: the project name captured from the artifact's
 * own metadata. Optional.</li>
 * </ul>
 *
 * <p>{@link #getDisplayName()} is the canonical name to render. Guard access to
 * the optional facets with {@link #hasDependencyName()} and
 * {@link #hasProjectName()}; the corresponding accessors throw
 * {@link IllegalStateException} when the facet is absent.
 *
 * <p>Presentations are immutable value objects and safe to share across
 * threads.
 *
 * @author Mark Paluch
 * @see biz.paluch.dap.assistant.IconDependencyPresentation
 * @see biz.paluch.dap.assistant.DependencyPresentationFactory
 */
public interface DependencyPresentation extends HasArtifactId {

	/**
	 * Return the canonical display name: the {@link #getDependencyName() dependency
	 * name} when present, the {@link #getArtifactIdDisplayName() rendered artifact
	 * coordinates} otherwise.
	 *
	 * @return the resolved display name.
	 */
	String getDisplayName();

	/**
	 * Return the artifact coordinates rendered for display, typically in the
	 * notation of the declaring build system.
	 *
	 * @return the rendered artifact coordinates.
	 */
	String getArtifactIdDisplayName();

	/**
	 * Return whether this presentation carries a curated dependency name.
	 *
	 * @return {@code true} if a dependency name is present; {@code false}
	 * otherwise.
	 */
	boolean hasDependencyName();

	/**
	 * Return the curated dependency name.
	 *
	 * @return the dependency name.
	 * @throws IllegalStateException if no dependency name is present.
	 * @see #hasDependencyName()
	 */
	String getDependencyName();

	/**
	 * Return whether this presentation carries a project name captured from the
	 * artifact's metadata.
	 *
	 * @return {@code true} if a project name is present; {@code false} otherwise.
	 */
	boolean hasProjectName();

	/**
	 * Return the project name as captured from the artifact's metadata.
	 *
	 * @return the project name.
	 * @throws IllegalStateException if no project name is present.
	 * @see #hasProjectName()
	 */
	String getProjectName();

	/**
	 * Create a presentation from the artifact coordinates of the given source,
	 * without dependency or project names.
	 *
	 * @param aware the source providing the artifact coordinates.
	 * @return a presentation rendering the plain artifact coordinates.
	 * @see #of(ArtifactId)
	 */
	public static DependencyPresentation of(HasArtifactId aware) {
		return of(aware.getArtifactId());
	}

	/**
	 * Create a presentation for the given artifact coordinates, without dependency
	 * or project names. {@link #getDisplayName()} falls back to the coordinate
	 * representation.
	 *
	 * @param artifactId the artifact coordinates to present.
	 * @return a presentation rendering the plain artifact coordinates.
	 */
	public static DependencyPresentation of(ArtifactId artifactId) {
		return SimpleDependencyPresentation.of(artifactId, artifactId.toString(), null, null);
	}

	/**
	 * Create a fully populated presentation.
	 *
	 * @param artifactId the artifact coordinates to present.
	 * @param displayName the rendered artifact coordinates returned from
	 * {@link #getArtifactIdDisplayName()}.
	 * @param dependencyName the curated dependency name; can be {@literal null} if
	 * no curated name is known.
	 * @param projectName the project name from the artifact's metadata; can be
	 * {@literal null} if the metadata does not declare one.
	 * @return the presentation carrying the given name facets.
	 */
	static DependencyPresentation of(ArtifactId artifactId, String displayName, @Nullable String dependencyName,
			@Nullable String projectName) {
		return new SimpleDependencyPresentation(artifactId, displayName, dependencyName, projectName);
	}

}

