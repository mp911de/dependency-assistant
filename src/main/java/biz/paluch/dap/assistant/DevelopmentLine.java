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

package biz.paluch.dap.assistant;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;
import org.jspecify.annotations.Nullable;

/**
 * A development line of an artifact's version history: the run of versions
 * sharing the same major and minor version (or the same release train), newest
 * first. Patch releases and pre-releases of the same base version belong to one
 * line; a rule {@code Generation} may span several lines (generation {@code 5}
 * covers the {@code 5.0} and {@code 5.1} lines).
 *
 * @author Mark Paluch
 * @see DevelopmentLines
 * @see ArtifactVersion#hasSameMajorMinor(ArtifactVersion)
 */
class DevelopmentLine implements Iterable<ArtifactVersion> {

	private final List<ArtifactVersion> versions;

	private final @Nullable ArtifactVersion latestStable;

	DevelopmentLine(List<ArtifactVersion> versions) {
		this.versions = versions;

		ArtifactVersion latestStable = null;
		for (ArtifactVersion version : versions) {
			if (version.isPreview()) {
				continue;
			}
			latestStable = version;
			break;
		}
		this.latestStable = latestStable;
	}

	/**
	 * Return the versions of this line, newest first.
	 *
	 * @return the versions of this line; never empty.
	 */
	public List<ArtifactVersion> getVersions() {
		return versions;
	}

	/**
	 * Return the newest version of this line, stable or pre-release.
	 */
	public ArtifactVersion getLatest() {
		return versions.getFirst();
	}

	/**
	 * Return the newest stable (non-preview) version of this line.
	 *
	 * @return the newest stable version, or {@literal null} for a pre-release-only
	 * line.
	 */
	public @Nullable ArtifactVersion getLatestStable() {
		return this.latestStable;
	}

	/**
	 * Return whether the given version belongs to this development line.
	 *
	 * @param version the version to test.
	 * @return {@literal true} if the version shares this line's major and minor;
	 * {@literal false} otherwise.
	 */
	public boolean contains(ArtifactVersion version) {
		return Versioned.of(getLatest()).unwrap().hasSameMajorMinor(version);
	}

	/**
	 * Return whether this entire line is older than the given version.
	 *
	 * @param version the version to compare against.
	 * @return {@literal true} if this line's newest version compares older than the
	 * given version; {@literal false} otherwise.
	 */
	public boolean isOlderThan(ArtifactVersion version) {
		return getLatest().compareTo(version) < 0;
	}

	@Override
	public Iterator<ArtifactVersion> iterator() {
		return versions.iterator();
	}

	/**
	 * Return the versions of this line as a stream, newest first.
	 */
	public Stream<ArtifactVersion> stream() {
		return versions.stream();
	}

	@Override
	public String toString() {
		return "DevelopmentLine" + versions;
	}

}
