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

package biz.paluch.dap.artifact;

import org.springframework.util.ObjectUtils;

/**
 * A dependency package identified by artifact coordinates and the package
 * ecosystem that owns them.
 *
 * <p>Use this value when storing or looking up package-level state where the
 * same coordinates can belong to different package systems. Equality uses both
 * the {@link ArtifactId} and {@link PackageSystem}; instances are immutable.
 *
 * @author Mark Paluch
 * @see PackageSystem
 */
public class PackageIdentity implements HasArtifactId {

	private final ArtifactId artifactId;

	private final PackageSystem packageSystem;

	private PackageIdentity(ArtifactId artifactId, PackageSystem packageSystem) {
		this.artifactId = artifactId;
		this.packageSystem = packageSystem;
	}

	/**
	 * Create a package identity for the given coordinates and ecosystem.
	 *
	 * @param artifactId the artifact coordinates within the package ecosystem.
	 * @param packageSystem the ecosystem that interprets the coordinates.
	 * @return the package identity.
	 */
	public static PackageIdentity of(ArtifactId artifactId, PackageSystem packageSystem) {
		return new PackageIdentity(artifactId, packageSystem);
	}

	/**
	 * Return the artifact coordinates identifying the package within its ecosystem.
	 *
	 * @return the artifact coordinates.
	 */
	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	/**
	 * Return the ecosystem that interprets the artifact coordinates.
	 *
	 * @return the package ecosystem.
	 */
	public PackageSystem getPackageSystem() {
		return packageSystem;
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof PackageIdentity that)) {
			return false;
		}
		return packageSystem == that.packageSystem && ObjectUtils.nullSafeEquals(artifactId, that.artifactId);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(artifactId, packageSystem);
	}

	@Override
	public String toString() {
		return "%s[%s]".formatted(packageSystem.name(), artifactId);
	}

}
