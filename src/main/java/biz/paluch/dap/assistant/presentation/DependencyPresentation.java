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
import org.jspecify.annotations.Nullable;

/**
 * Detached names and coordinates for a dependency.
 * <p>The curated dependency name is the preferred label. The upstream project
 * name is a separate metadata value. Factory results are immutable snapshots
 * that do not retain dependencies or services.
 *
 * @author Mark Paluch
 */
public interface DependencyPresentation extends HasArtifactId, HasPackageIdentity {

	/**
	 * Return the dependency name, or the short artifact identifier if unnamed.
	 */
	String getDisplayName();

	/**
	 * Return the dependency name for message templates, quoting unnamed
	 * identifiers.
	 * <p>The result is not HTML-escaped. Escape untrusted names before embedding as
	 * HTML.
	 */
	default String getHtmlDisplayName() {
		return hasDependencyName() ? getDependencyName() : "'" + getDisplayName() + "'";
	}

	/**
	 * Return the package-system name component without the group.
	 */
	String getShortArtifactId();

	/**
	 * Return coordinates in the package system's notation.
	 */
	String getCoordinates();

	boolean hasDependencyName();

	/**
	 * Return the curated dependency name.
	 * @throws IllegalStateException if no name is present.
	 */
	String getDependencyName();

	/**
	 * Return the captured project-name policy, possibly empty.
	 */
	ProjectName getProjectName();

	/**
	 * Create a coordinate-only presentation.
	 * @see #of(PackageIdentity)
	 */
	public static DependencyPresentation of(HasPackageIdentity aware) {
		return of(aware.getPackageIdentity());
	}

	/**
	 * Create a presentation without dependency or project names.
	 */
	public static DependencyPresentation of(PackageIdentity pkg) {
		return SimpleDependencyPresentation.of(pkg, null, ProjectName.empty(pkg.getArtifactId()));
	}

	/**
	 * @param dependencyName the curated name, or {@literal null} if unknown.
	 */
	static DependencyPresentation of(PackageIdentity pkg, @Nullable String dependencyName, ProjectName projectName) {
		return new SimpleDependencyPresentation(pkg, dependencyName, projectName);
	}

}
