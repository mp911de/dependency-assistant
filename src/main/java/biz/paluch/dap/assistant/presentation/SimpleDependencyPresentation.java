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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.metadata.ProjectName;
import org.jspecify.annotations.Nullable;

/**
 * Default {@link DependencyPresentation} backed by plain values.
 *
 * @author Mark Paluch
 */
class SimpleDependencyPresentation implements DependencyPresentation {

	private final PackageIdentity pkg;

	private final String coordinates;

	private final String artifactId;

	private final @Nullable String dependencyName;

	private final ProjectName projectName;

	public SimpleDependencyPresentation(PackageIdentity pkg,
			@Nullable String dependencyName, ProjectName projectName) {

		PackageSystem packageSystem = pkg.getPackageSystem();
		this.pkg = pkg;
		this.coordinates = packageSystem.getCoordinates(pkg.getArtifactId());
		this.artifactId = packageSystem.getArtifactId(pkg.getArtifactId());
		this.dependencyName = dependencyName;
		this.projectName = projectName;
	}

	public static SimpleDependencyPresentation of(PackageIdentity pkg,
			@Nullable String dependencyName, ProjectName projectName) {
		return new SimpleDependencyPresentation(pkg, dependencyName, projectName);
	}

	@Override
	public PackageIdentity getPackageIdentity() {
		return pkg;
	}

	@Override
	public ArtifactId getArtifactId() {
		return pkg.getArtifactId();
	}

	@Override
	public String getShortArtifactId() {
		return artifactId;
	}

	@Override
	public String getCoordinates() {
		return coordinates;
	}

	@Override
	public String getDisplayName() {
		if (hasDependencyName()) {
			return getDependencyName();
		}
		return getShortArtifactId();
	}

	@Override
	public boolean hasDependencyName() {
		return dependencyName != null && !dependencyName.isBlank();
	}

	@Override
	public String getDependencyName() {
		if (hasDependencyName()) {
			return dependencyName;
		}
		throw new IllegalStateException("No dependency name for %s".formatted(pkg));
	}

	@Override
	public ProjectName getProjectName() {
		return projectName;
	}

	@Override
	public String toString() {
		if (hasDependencyName()) {
			return getCoordinates() + " (" + dependencyName + ")";
		}
		return artifactId;
	}

}
