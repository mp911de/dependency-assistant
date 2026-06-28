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

package biz.paluch.dap.npm;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import org.jspecify.annotations.Nullable;

/**
 * Test fixture providing curated NPM {@link CachedArtifact} samples.
 *
 * <p>Mirrors {@link biz.paluch.dap.Releases} for the NPM ecosystem. Each
 * artifact carries a representative slice of release history derived from a
 * real NPM registry response so completion and line marker tests can exercise
 * upgrade-strategy and version-comparison logic without dragging in the full
 * upstream catalogue.
 *
 * @author Mark Paluch
 */
class NpmReleases {

	private static final List<CachedArtifact> ALL = new ArrayList<>();

	static final CachedArtifact ANTORA_XREF_EXTENSION = create("@springio", "antora-xref-extension",
			releases -> releases
					.add("1.0.0-alpha.5", "2025-11-08", "7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1")
					.add("1.0.0-alpha.4", "2024-11-05", "d1185ce59f7757407fe6a5febb1e03e3dba2a530")
					.add("1.0.0-alpha.3", "2024-03-19", "aaabbdde59f7757407fe6a5febb1e03e3dba2a530")
					.add("1.0.0-alpha.2", "2024-03-19")
					.add("1.0.0-alpha.1", "2024-03-19"));

	static final CachedArtifact GH_ANTORA_XREF_EXTENSION = create("spring-io", "antora-xref-extension",
			releases -> releases
					.add("1.0.0-alpha.5", "2025-11-08", "7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1")
					.add("1.0.0-alpha.4", "2024-11-05", "d1185ce59f7757407fe6a5febb1e03e3dba2a530")
					.add("1.0.0-alpha.3", "2024-03-19", "aaabbdde59f7757407fe6a5febb1e03e3dba2a530")
					.add("1.0.0-alpha.2", "2024-03-19")
					.add("1.0.0-alpha.1", "2024-03-19"));

	static final CachedArtifact AXIOS = create("axios", "axios",
			releases -> releases
					.add("6.0.0", "2025-11-08", "7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1")
					.add("5.4.0", "2024-11-05", "d1185ce59f7757407fe6a5febb1e03e3dba2a530")
					.add("5.3.1", "2024-11-05", "d1185ce5aa7757407fe6a5febb1e03e3dba2a530")
					.add("5.3.0", "2024-03-19", "aaabbdde59f7757407fe6a5febb1e03e3dba2a530"));

	private NpmReleases() {
	}

	/**
	 * Return independent copies of all NPM artifacts registered in this fixture, in
	 * declaration order.
	 * <p>Each call yields fresh {@link CachedArtifact} and {@link CachedRelease}
	 * instances so a test that mutates the cache (for example a vulnerability warm
	 * scan on an open file) cannot leak state into another test through the shared
	 * template.
	 *
	 * @return fresh copies of the registered artifacts.
	 */
	static List<CachedArtifact> all() {

		List<CachedArtifact> copies = new ArrayList<>(ALL.size());
		for (CachedArtifact template : ALL) {
			copies.add(copy(template));
		}
		return copies;
	}

	private static CachedArtifact copy(CachedArtifact template) {

		CachedArtifact copy = new CachedArtifact(template.getGroupId(), template.getArtifactId());
		for (CachedRelease release : template.getReleases()) {
			copy.getReleases().add(new CachedRelease(release.version(), release.date(), release.sha()));
		}
		return copy;
	}

	private static CachedArtifact create(String groupId, String artifactId, Consumer<ReleaseBuilder> configurer) {

		CachedArtifact artifact = new CachedArtifact(groupId, artifactId);
		configurer.accept(new ReleaseBuilder(artifact.getReleases()));
		ALL.add(artifact);
		return artifact;
	}

	/**
	 * Fluent collector for {@link CachedRelease} entries on a single
	 * {@link CachedArtifact}.
	 */
	static class ReleaseBuilder {

		private final List<CachedRelease> releases;

		private ReleaseBuilder(List<CachedRelease> releases) {
			this.releases = releases;
		}

		ReleaseBuilder add(String version) {
			return add(version, null);
		}

		ReleaseBuilder add(String version, @Nullable String date) {
			releases.add(new CachedRelease(version, date));
			return this;
		}

		ReleaseBuilder add(String version, String date, String sha) {
			releases.add(new CachedRelease(version, date, sha));
			return this;
		}

	}

}
