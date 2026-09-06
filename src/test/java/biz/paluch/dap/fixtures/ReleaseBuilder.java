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

package biz.paluch.dap.fixtures;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import org.jspecify.annotations.Nullable;

/**
 * Builds cached release fixtures for one artifact.
 *
 * @author Mark Paluch
 */
public class ReleaseBuilder {

	private final List<CachedRelease> releases;

	private ReleaseBuilder(List<CachedRelease> releases) {
		this.releases = releases;
	}

	public static CachedArtifact artifact(String groupId, String artifactId,
			Consumer<ReleaseBuilder> configurer) {
		return artifact(ArtifactId.of(groupId, artifactId), configurer);
	}

	public static CachedArtifact artifact(ArtifactId artifactId, Consumer<ReleaseBuilder> configurer) {

		CachedArtifact artifact = new CachedArtifact(artifactId);
		List<CachedRelease> releases = new ArrayList<>();
		configurer.accept(new ReleaseBuilder(releases));
		releases.forEach(artifact::addRelease);
		return artifact;
	}

	public ReleaseBuilder add(String version) {
		return add(version, null);
	}

	public ReleaseBuilder add(String version, @Nullable String date) {
		releases.add(new CachedRelease(version, date));
		return this;
	}

	public ReleaseBuilder addWithSha(String version, String sha) {
		return add(version, null, sha);
	}

	public ReleaseBuilder add(String version, @Nullable String date, @Nullable String sha) {
		releases.add(new CachedRelease(version, date, sha));
		return this;
	}

}
