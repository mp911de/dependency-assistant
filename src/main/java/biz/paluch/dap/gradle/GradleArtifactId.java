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

package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.HasPackageSystem;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * Gradle module coordinates with version metadata separate from artifact
 * identity.
 * <p>Coordinate parts may contain unresolved property expressions. Equality
 * ignores the version, following {@link ArtifactId}.
 *
 * @author Mark Paluch
 * @see GradleDependency
 */
interface GradleArtifactId extends ArtifactId, HasPackageIdentity, HasPackageSystem {

	/**
	 * Return the version segment, or an empty string when this notation has no
	 * version.
	 */
	String version();

	/**
	 * Resolve coordinate expressions to an artifact identity, excluding the
	 * version.
	 */
	default ArtifactId resolve(PropertyResolver propertyResolver) {

		Expression group = Expression.from(groupId());
		Expression artifact = Expression.from(artifactId());

		return ArtifactId.of(group.resolveRequired(propertyResolver),
				artifact.resolveRequired(propertyResolver));
	}

	/**
	 * Resolve coordinate and version expressions while retaining version metadata.
	 */
	default GradleArtifactId resolveAll(PropertyResolver propertyResolver) {

		Expression group = Expression.from(groupId());
		Expression artifact = Expression.from(artifactId());
		Expression version = Expression.from(version());

		return new DefaultGradleArtifactId(group.resolveRequired(propertyResolver),
				artifact.resolveRequired(propertyResolver), version.resolveRequired(propertyResolver));
	}

	/**
	 * Parse compact {@code group:name[:version]} notation. This is not a full DSL
	 * validator.
	 * @throws IllegalArgumentException if the notation is absent or lacks group or
	 * artifact identity.
	 */
	static GradleArtifactId from(String gav) {
		Assert.hasLength(gav, "GAV must not be empty");
		String[] parts = gav.split(":", -1);
		if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
			throw new IllegalArgumentException("Invalid Gradle coordinate '%s'".formatted(gav));
		}
		return new DefaultGradleArtifactId(parts[0], parts[1], parts.length > 2 ? parts[2] : "");
	}

	static GradleArtifactId from(ArtifactId artifactId, String version) {
		return new DefaultGradleArtifactId(artifactId.groupId(), artifactId.artifactId(), version);
	}

	/**
	 * Return whether the text is a compact-coordinate candidate.
	 * <p>This does not validate all Gradle dependency syntax.
	 */
	static boolean isValid(@Nullable String gav) {
		if (!StringUtils.hasText(gav)) {
			return false;
		}
		String[] parts = gav.split(":", -1);
		return parts.length >= 2 && !parts[0].isEmpty() && !parts[1].isEmpty();
	}

	record DefaultGradleArtifactId(String groupId, String artifactId, String version)
			implements GradleArtifactId {


		@Override
		public PackageIdentity getPackageIdentity() {
			return PackageIdentity.of(this, getPackageSystem());
		}

		@Override
		public PackageSystem getPackageSystem() {
			return PackageSystem.MAVEN;
		}

		@Override
		public boolean equals(Object o) {
			if (o instanceof ArtifactId that) {
				return ObjectUtils.nullSafeEquals(groupId, that.groupId())
						&& ObjectUtils.nullSafeEquals(artifactId, that.artifactId());
			}
			return false;
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHash(groupId, artifactId);
		}

		@Override
		public String toString() {
			return GradlePluginId.isPlugin(this) ? groupId : (groupId + ":" + artifactId);
		}

	}

}
