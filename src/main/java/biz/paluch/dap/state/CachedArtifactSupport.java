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

	/**
	 * Return the cached group identifier.
	 *
	 * @return the group identifier.
	 */
	public abstract @Nullable String getGroupId();

	@Override
	@Transient
	public String groupId() {
		Assert.hasText(getGroupId(), "GroupId must not be empty");
		return getGroupId();
	}

	/**
	 * Return the cached artifact identifier.
	 *
	 * @return the artifact identifier.
	 */
	public abstract @Nullable String getArtifactId();

	@Override
	@Transient
	public String artifactId() {
		Assert.hasText(getArtifactId(), "ArtifactId must not be empty");
		return getArtifactId();
	}

	public abstract @Nullable PackageSystem getEcosystem();

	/**
	 * Return whether this cache entry refers to the given artifact.
	 *
	 * @param artifactId the artifact to compare with.
	 * @return {@literal true} if both group and artifact identifiers match.
	 */
	public boolean matches(ArtifactId artifactId) {
		return artifactId.artifactId().equals(getArtifactId()) && artifactId.groupId().equals(getGroupId());
	}

	/**
	 * Return whether this entry matches the given coordinates and ecosystem. A
	 * {@literal null} ecosystem on either side is treated as a wildcard so entries
	 * persisted before ecosystem tracking still match.
	 *
	 * @param artifactId the artifact to compare with.
	 * @param packageSystem the ecosystem to compare; may be {@literal null}.
	 * @return {@literal true} if the entry matches; {@literal false} otherwise.
	 */
	public boolean matches(ArtifactId artifactId, @Nullable PackageSystem packageSystem) {
		PackageSystem ecosystem = getEcosystem();
		return matches(artifactId) && (ecosystem == null || packageSystem == null || ecosystem == packageSystem);
	}

	/**
	 * Return the artifact coordinates represented by this cache entry.
	 *
	 * @return the artifact identifier.
	 */
	@Transient
	public ArtifactId toArtifactId() {
		Assert.hasText(getGroupId(), "GroupId must not be empty");
		Assert.hasText(getArtifactId(), "ArtifactId must not be empty");
		return ArtifactId.of(getGroupId(), getArtifactId());
	}

	/**
	 * Return the package identity represented by this cache entry.
	 * @return the package identity.
	 */
	@Transient
	public PackageIdentity toPackageIdentity() {
		Assert.state(getEcosystem() != null, "Package ecosystem not set");
		return PackageIdentity.of(toArtifactId(), getEcosystem());
	}

	@Override
	public String toString() {
		return groupId() + ":" + artifactId();
	}

}
