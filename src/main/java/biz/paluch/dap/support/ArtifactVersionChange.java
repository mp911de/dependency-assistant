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

package biz.paluch.dap.support;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.artifact.Versioned;
import org.jetbrains.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * A selected version change with an optional source version.
 * {@link DependencyUpdate} adds the source locations needed to apply it.
 *
 * @author Mark Paluch
 */
public class ArtifactVersionChange {

	private final ArtifactId artifactId;

	private final Versioned from;

	private final ArtifactVersion to;

	ArtifactVersionChange(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion to) {
		this(artifactId, Versioned.of(from), to);
	}

	private ArtifactVersionChange(ArtifactId artifactId, Versioned from, ArtifactVersion to) {
		this.artifactId = artifactId;
		this.from = from;
		this.to = to;
	}

	public static ArtifactVersionChange of(ArtifactId artifactId, ArtifactVersion from, ArtifactVersion to) {
		return new ArtifactVersionChange(artifactId, Versioned.of(from), to);
	}

	/**
	 * Create a change with an unknown source version.
	 */
	public static ArtifactVersionChange of(ArtifactId artifactId, ArtifactVersion to) {
		return new ArtifactVersionChange(artifactId, Versioned.unversioned(), to);
	}

	public ArtifactId artifactId() {
		return artifactId;
	}

	public ArtifactVersion to() {
		return to;
	}

	public String versionAsString() {
		return to.toString();
	}

	/**
	 * Return the source version, or an unversioned value if unknown.
	 */
	public Versioned from() {
		return from;
	}

	/**
	 * Return whether the change leaves the source major line. A different version
	 * scheme counts as a major switch. An unknown source version does not.
	 */
	public boolean crossesMajor() {
		if (!from.isVersioned()) {
			return false;
		}

		ArtifactVersion source = from.getVersion();
		return source.scheme() != to.scheme() || !to.hasSameMajor(source);
	}

	/**
	 * Classify the upgrade, giving preview targets precedence over numeric
	 * boundaries.
	 *
	 * @return {@code null} for an unknown source, equal version, or older target.
	 */
	public @Nullable UpgradeStrategy getUpgradeStrategy() {

		if (!from.isVersioned()) {
			return null;
		}

		VersionAge age = VersionAge.between(from, () -> to);

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
		return ObjectUtils.nullSafeEquals(to, that.to);
	}

	@Override
	public int hashCode() {
		return ObjectUtils.nullSafeHash(artifactId, from, to);
	}

	@Override
	public String toString() {
		return "ArtifactVersionChange[" +
				artifactId + "@" + from + " -> " + to + ']';
	}

}
