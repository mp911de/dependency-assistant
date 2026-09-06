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

package biz.paluch.dap.assistant.completion;

import java.util.Iterator;
import java.util.List;
import java.util.stream.Stream;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.util.Sequence;
import org.jspecify.annotations.Nullable;

/**
 * Versions sharing a major and minor version, or release train, newest first.
 * <p>Patch and preview releases belong to the same line. A rule generation may
 * span several lines, such as generation 5 covering 5.0 and 5.1.
 *
 * @author Mark Paluch
 */
class DevelopmentLine implements Sequence<ArtifactVersion> {

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
	 * Return the retained non-empty version list, newest first.
	 * <p>Do not mutate it because the stable-version result is cached.
	 */
	public List<ArtifactVersion> getVersions() {
		return versions;
	}

	/**
	 * Return the newest version, including previews.
	 */
	public ArtifactVersion getLatest() {
		return versions.getFirst();
	}

	/**
	 * Return the newest stable version, or {@literal null} for a preview-only line.
	 */
	public @Nullable ArtifactVersion getLatestStable() {
		return this.latestStable;
	}

	/**
	 * Return whether the version shares this line's major and minor.
	 */
	public boolean contains(ArtifactVersion version) {
		return getLatest().unwrap().hasSameMajorMinor(version);
	}

	/**
	 * Return whether this line's newest version is older than the given version.
	 */
	public boolean isOlderThan(ArtifactVersion version) {
		return getLatest().compareTo(version) < 0;
	}

	@Override
	public Iterator<ArtifactVersion> iterator() {
		return versions.iterator();
	}

	@Override
	public Stream<ArtifactVersion> stream() {
		return versions.stream();
	}

	@Override
	public String toString() {
		return "DevelopmentLine" + versions;
	}

}
