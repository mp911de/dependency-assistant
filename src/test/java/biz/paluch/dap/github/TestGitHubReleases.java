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

package biz.paluch.dap.github;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.GitArtifactId;
import biz.paluch.dap.fixtures.ReleaseBuilder;
import biz.paluch.dap.state.CachedArtifact;

/**
 * Test fixture bank for GitHub-hosted releases.
 *
 * @author Mark Paluch
 */
public class TestGitHubReleases {

	private static final List<CachedArtifact> ALL = new ArrayList<>();

	public static final String CHECKOUT_SHA_LATEST = "d1185ce59f7757407fe6a5febb1e03e3dba2a530";

	public static final String CHECKOUT_SHA_LATEST_SHORT = "d1185ce5";

	public static final String CHECKOUT_SHA_OLDER_MAJOR = "7b4f3880ef3a2616e5c519a35b7a4f07f7b3b2a1";

	public static final CachedArtifact CHECKOUT = create("actions", "checkout",
			releases -> releases.addWithSha("v4.2.0", CHECKOUT_SHA_LATEST)
					.add("v4.1.0")
					.addWithSha("v3.6.0", CHECKOUT_SHA_OLDER_MAJOR));

	public static final CachedArtifact SETUP_JAVA = create("actions", "setup-java",
			releases -> releases.addWithSha("v5.4.0", "1bcf9fb12cf4aa7d266a90ae39939e61372fe520"));

	public static final GitArtifactId GIT_ANTORA_UI = GitArtifactId.of("github.com", "spring-io",
			"antora-ui-spring");

	public static final CachedArtifact ANTORA_UI = create("spring-io", "antora-ui-spring",
			releases -> releases.add("v0.4.26", "2025-01-01",
					"aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")
					.add("v0.4.25", "2024-12-01",
							"bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")
					.add("v0.3.0", "2024-06-01",
							"cccccccccccccccccccccccccccccccccccccccc"));

	private TestGitHubReleases() {
	}

	public static List<CachedArtifact> all() {
		return ALL;
	}

	public static List<CachedArtifact> actions() {
		return List.of(CHECKOUT, SETUP_JAVA);
	}


	private static CachedArtifact create(String groupId, String artifactId, Consumer<ReleaseBuilder> configurer) {
		return create(ArtifactId.of(groupId, artifactId), configurer);
	}

	private static CachedArtifact create(ArtifactId artifactId, Consumer<ReleaseBuilder> configurer) {

		CachedArtifact artifact = ReleaseBuilder.artifact(artifactId, configurer);
		ALL.add(artifact);
		return artifact;
	}

}
