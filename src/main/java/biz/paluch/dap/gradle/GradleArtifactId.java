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
package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.StringUtils;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;
import org.springframework.util.ObjectUtils;

/**
 * {@link ArtifactId} variant used as an intermediate representation for Gradle
 * module coordinates.
 *
 * <p>
 * Gradle dependency declarations can combine artifact identity and version
 * intent in a single notation such as {@code group:name:version}, while the
 * rest of the assistant operates primarily on ecosystem-neutral
 * {@link ArtifactId artifact identities}. This contract keeps those concerns
 * separated: {@link #groupId()} and {@link #artifactId()} form the dependency
 * identity, while {@link #version()} carries Gradle declaration metadata until
 * a parser decides how that version should be represented.
 *
 * <p>
 * Property expressions are deliberately accepted as coordinate parts. They
 * are resolved at the boundary where Gradle-specific declarations are adapted
 * to the common dependency model so callers can keep parser extraction,
 * property lookup, and release lookup as separate responsibilities.
 *
 * <p>
 * Equality follows the {@link ArtifactId} identity contract. Code that needs
 * version-sensitive comparison should include {@link #version()} explicitly in
 * its own key.
 *
 * @author Mark Paluch
 * @see GradleDependency
 */
interface GradleArtifactId extends ArtifactId {

	/**
	 * Version segment associated with the Gradle declaration, or an empty string
	 * when the declaration relies on dependency management or a separate version
	 * block.
	 */
	String version();

	/**
	 * Adapt this Gradle coordinate to the common artifact-identity model.
	 * <p>
	 * Only the identity parts are materialized here. Version handling remains
	 * with the Gradle dependency layer so property-backed versions can still be
	 * associated with their declaration site and {@code VersionSource}.
	 * @param propertyResolver property resolver to use for coordinate expressions.
	 * @return the resolved artifact identity.
	 */
	default ArtifactId resolve(PropertyResolver propertyResolver) {

		Expression group = Expression.from(groupId());
		Expression artifact = Expression.from(artifactId());

		return ArtifactId.of(group.resolveRequired(propertyResolver),
				artifact.resolveRequired(propertyResolver));
	}

	/**
	 * Materialize the complete Gradle coordinate for use cases that intentionally
	 * preserve the version as coordinate metadata.
	 * <p>
	 * This variant is appropriate after parser code has established that the
	 * version belongs with the dependency declaration itself. Use
	 * {@link #resolve(PropertyResolver)} when crossing into release lookup,
	 * caching, or other artifact-identity concerns.
	 * @param propertyResolver property resolver to use for coordinate and version
	 * expressions.
	 * @return the resolved Gradle coordinate.
	 */
	default GradleArtifactId resolveAll(PropertyResolver propertyResolver) {

		Expression group = Expression.from(groupId());
		Expression artifact = Expression.from(artifactId());
		Expression version = Expression.from(version());

		return new DefaultGradleArtifactId(group.resolveRequired(propertyResolver),
				artifact.resolveRequired(propertyResolver), version.resolveRequired(propertyResolver));
	}

	/**
	 * Create a Gradle coordinate from compact Gradle module notation.
	 * <p>
	 * This factory models the value shape used by Gradle's string notation; it
	 * is not a full Gradle DSL validator. Parser code should use it only after it
	 * has selected text that is meant to represent module coordinates.
	 *
	 * @param gav compact Gradle module notation, typically
	 * {@code group:name:version} or {@code group:name}.
	 * @return the Gradle coordinate.
	 */
	static GradleArtifactId from(String gav) {
		Assert.hasLength(gav, "GAV must not be empty");
		String[] parts = gav.split(":", -1);
		if (parts.length < 2 || parts[0].isEmpty() || parts[1].isEmpty()) {
			throw new IllegalArgumentException("Invalid Gradle coordinate '%s'".formatted(gav));
		}
		return new DefaultGradleArtifactId(parts[0], parts[1], parts.length > 2 ? parts[2] : "");
	}

	/**
	 * Attach Gradle version metadata to an existing artifact identity.
	 * <p>
	 * Use this factory for structured Gradle declarations where group and name
	 * were parsed independently but should still flow through the same coordinate
	 * model as compact string notation.
	 * @param artifactId the artifact identity.
	 * @param version the Gradle version segment, if available.
	 * @return the Gradle coordinate.
	 */
	static GradleArtifactId from(ArtifactId artifactId, String version) {
		return new DefaultGradleArtifactId(artifactId.groupId(), artifactId.artifactId(), version);
	}

	/**
	 * Lightweight parser-dispatch conditional for Gradle module notation.
	 * <p>
	 * The check distinguishes compact coordinate strings from
	 * other Gradle argument forms. It should not be used as a general-purpose
	 * validator for dependency notation accepted by Gradle itself.
	 * @param gav candidate text from a Gradle declaration.
	 * @return {@literal true} if the text is eligible for compact-coordinate
	 * parsing.
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
