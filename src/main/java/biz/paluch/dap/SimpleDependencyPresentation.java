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
import biz.paluch.dap.artifact.PackageIdentity;
import org.jspecify.annotations.Nullable;

/**
 * Default {@link DependencyPresentation} backed by plain values.
 *
 * @author Mark Paluch
 */
class SimpleDependencyPresentation implements DependencyPresentation {

	private final PackageIdentity pkg;

	private final String artifactIdDisplayName;

	private final @Nullable String dependencyName;

	private final @Nullable String projectName;

	public SimpleDependencyPresentation(PackageIdentity pkg, String artifactIdDisplayName,
			@Nullable String dependencyName, @Nullable String projectName) {
		this.pkg = pkg;
		this.artifactIdDisplayName = artifactIdDisplayName;
		this.dependencyName = dependencyName;
		this.projectName = projectName;
	}

	public static SimpleDependencyPresentation of(PackageIdentity pkg, String renderedArtifactId,
			@Nullable String dependencyName,
			@Nullable String projectName) {

		return new SimpleDependencyPresentation(pkg, renderedArtifactId, dependencyName, projectName);
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
	public String getArtifactIdDisplayName() {
		return artifactIdDisplayName;
	}

	@Override
	public String getDisplayName() {
		if (hasDependencyName()) {
			return getDependencyName();
		}
		return getArtifactIdDisplayName();
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
	public boolean hasProjectName() {
		return projectName != null && !projectName.isBlank();
	}

	@Override
	public String getProjectName() {
		if (hasProjectName()) {
			return projectName;
		}
		throw new IllegalStateException("No project name for %s".formatted(pkg));
	}

	@Override
	public String toString() {
		if (hasDependencyName()) {
			return artifactIdDisplayName + " (" + dependencyName + ")";
		}
		return artifactIdDisplayName;
	}

}
