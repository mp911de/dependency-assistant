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

package biz.paluch.dap.artifact;

import org.springframework.util.ObjectUtils;

/**
 * Artifact coordinates within a package ecosystem.
 * <p>Equality includes both coordinates and ecosystem, so identical coordinates
 * in different package systems remain distinct.
 *
 * @author Mark Paluch
 */
public class PackageIdentity implements HasArtifactId, HasPackageSystem {

	private final ArtifactId artifactId;

	private final PackageSystem packageSystem;

	private PackageIdentity(ArtifactId artifactId, PackageSystem packageSystem) {
		this.artifactId = artifactId;
		this.packageSystem = packageSystem;
	}

	public static PackageIdentity of(ArtifactId artifactId, PackageSystem packageSystem) {
		return new PackageIdentity(artifactId, packageSystem);
	}

	@Override
	public ArtifactId getArtifactId() {
		return artifactId;
	}

	@Override
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
		return "%s[%s]".formatted(packageSystem.name(), packageSystem.getCoordinates(artifactId));
	}

}
