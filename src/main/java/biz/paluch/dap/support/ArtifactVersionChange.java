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

package biz.paluch.dap.support;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.Versioned;
import org.jetbrains.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * Immutable value object describing a target {@link ArtifactVersion} for an
 * {@link ArtifactId}.
 * <p>An {@code ArtifactVersionChange} carries the artifact being changed, the
 * selected target version, and optionally the version currently in use. It does
 * not describe where the version is declared in a build file or how it should
 * be written back; it captures the intent only.
 * <p>{@link DependencyUpdate} extends this intent with the declaration scope
 * needed to apply the change (for example a dependency in a profile or a
 * particular version property).
 *
 * @author Mark Paluch
 * @see DependencyUpdate
 */
public class ArtifactVersionChange {

	private final ArtifactId artifactId;

	private final Versioned from;

	private final ArtifactVersion version;

	ArtifactVersionChange(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion version) {
		this(artifactId, Versioned.of(from), version);
	}

	private ArtifactVersionChange(ArtifactId artifactId, Versioned from, ArtifactVersion version) {
		this.artifactId = artifactId;
		this.from = from;
		this.version = version;
	}

	/**
	 * Create a version change with a known source version.
	 *
	 * @param artifactId the artifact whose version is changing.
	 * @param from the version currently in use.
	 * @param to the selected target version.
	 * @return the version change.
	 */
	public static ArtifactVersionChange of(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion to) {
		return new ArtifactVersionChange(artifactId, Versioned.of(from), to);
	}

	/**
	 * Create a version change without a known source version.
	 *
	 * @param artifactId the artifact whose version is changing.
	 * @param to the selected target version.
	 * @return the version change.
	 */
	public static ArtifactVersionChange of(ArtifactId artifactId, ArtifactVersion to) {
		return new ArtifactVersionChange(artifactId, Versioned.unversioned(), to);
	}

	/**
	 * Return the artifact whose version is changing.
	 *
	 * @return the artifact id.
	 */
	public ArtifactId artifactId() {
		return artifactId;
	}

	/**
	 * Return the selected target version.
	 *
	 * @return the target version.
	 */
	public ArtifactVersion version() {
		return version;
	}

	/**
	 * Return the {@link #version()} as String.
	 *
	 * @return the string representation of the target version.
	 * @see #version()
	 */
	public String versionAsString() {
		return version.toString();
	}

	/**
	 * Return the source version, if known.
	 *
	 * @return the source version, or an unversioned value when no source version is
	 * known.
	 */
	public Versioned from() {
		return from;
	}

	/**
	 * Return whether this change crosses a major version line.
	 *
	 * <p>A change of {@link ArtifactVersion#scheme() versioning scheme} counts as a
	 * major switch. When the source version is unknown no crossing is reported.
	 *
	 * @return {@literal true} if the source version is known and the target either
	 * changes scheme or leaves the source major line; {@literal false} otherwise.
	 */
	public boolean crossesMajor() {
		if (!from.isVersioned()) {
			return false;
		}

		ArtifactVersion source = from.getVersion();
		return source.scheme() != version.scheme() || !version.hasSameMajor(source);
	}

	/**
	 * Return whether this change crosses a minor version line.
	 *
	 * <p>A change of {@link ArtifactVersion#scheme() versioning scheme} counts as a
	 * major switch and therefore also as a minor switch. When the source version is
	 * unknown no crossing is reported.
	 *
	 * @return {@literal true} if the source version is known and the target either
	 * changes scheme or leaves the source major/minor line; {@literal false}
	 * otherwise.
	 */
	public boolean crossesMinor() {
		if (!from.isVersioned()) {
			return false;
		}

		ArtifactVersion source = from.getVersion();
		return source.scheme() != version.scheme() || !version.hasSameMajorMinor(source);
	}

	public @Nullable UpgradeStrategy getUpgradeStrategy() {

		if (!from.isVersioned()) {
			return null;
		}

		VersionAge age = VersionAge.between(from, () -> version);

		switch (age) {
		case NEWER_MAJOR -> {
			return UpgradeStrategy.MAJOR;
		}
		case NEWER_MINOR -> {
			return UpgradeStrategy.MINOR;
		}
		case NEWER_PATCH -> {
			return UpgradeStrategy.PATCH;
		}
		case PREVIEW -> {
			return UpgradeStrategy.PREVIEW;
		}
		default -> {
			return null;
		}
		}
	}

	@Override
	public boolean equals(Object o) {
		if (!(o instanceof ArtifactVersionChange that)) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(artifactId, that.artifactId)) {
			return false;
		}
		if (!ObjectUtils.nullSafeEquals(from, that.from)) {
			return false;
		}
		return ObjectUtils.nullSafeEquals(version, that.version);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(artifactId, from, version);
	}

	@Override
	public String toString() {
		return "ArtifactVersionChange[" +
				artifactId + "@" + from + " -> " + version + ']';
	}

}
