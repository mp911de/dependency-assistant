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

package biz.paluch.dap.state;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import com.intellij.util.xmlb.annotations.Transient;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Base class for persisted artifact coordinates, bridging the nullable
 * serializer-friendly identifiers to the non-null {@link ArtifactId} contract.
 *
 * @author Mark Paluch
 */
public abstract class CachedArtifactSupport implements ArtifactId {

	public abstract @Nullable String getGroupId();

	@Override
	@Transient
	public String groupId() {
		Assert.hasText(getGroupId(), "GroupId must not be empty");
		return getGroupId();
	}

	public abstract @Nullable String getArtifactId();

	@Override
	@Transient
	public String artifactId() {
		Assert.hasText(getArtifactId(), "ArtifactId must not be empty");
		return getArtifactId();
	}

	/**
	 * Return the ecosystem, or {@code null} for legacy entries.
	 */
	public abstract @Nullable PackageSystem getPackageSystem();

	/**
	 * Compare coordinates regardless of ecosystem.
	 */
	public boolean matches(ArtifactId artifactId) {
		return artifactId.artifactId().equals(getArtifactId()) && artifactId.groupId().equals(getGroupId());
	}

	/**
	 * Compare coordinates and ecosystem. A missing ecosystem on either side is a
	 * wildcard for legacy entries.
	 */
	public boolean matches(ArtifactId artifactId, @Nullable PackageSystem packageSystem) {
		PackageSystem ecosystem = getPackageSystem();
		return matches(artifactId) && (ecosystem == null || packageSystem == null || ecosystem == packageSystem);
	}

	@Transient
	public ArtifactId toArtifactId() {
		Assert.hasText(getGroupId(), "GroupId must not be empty");
		Assert.hasText(getArtifactId(), "ArtifactId must not be empty");
		return ArtifactId.of(getGroupId(), getArtifactId());
	}

	/**
	 * Return the package identity, using {@link PackageSystem#OTHER} for legacy
	 * entries.
	 */
	@Transient
	public PackageIdentity toPackageIdentity() {
		PackageSystem packageSystem = getPackageSystem();
		if (packageSystem == null) {
			packageSystem = PackageSystem.OTHER;
		}
		return PackageIdentity.of(toArtifactId(), packageSystem);
	}

	@Override
	public String toString() {
		return groupId() + ":" + artifactId();
	}

}
