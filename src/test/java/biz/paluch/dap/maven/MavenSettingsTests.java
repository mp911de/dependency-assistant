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

package biz.paluch.dap.maven;

import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RepositoryCredentials;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MavenSettings}.
 *
 * @author Mark Paluch
 */
class MavenSettingsTests {

	RepositoryCredentials credentials = new RepositoryCredentials("internal", "alice", "secret", null);
	RepositoryCredentials mirrorCredentials = new RepositoryCredentials("mirror", "alice", "secret", null);

	@Test
	void returnsRepositoryUnchangedWhenNoMirrorApplies() {

		MavenSettings settings = new MavenSettings(Map.of(), List.of());
		RemoteRepository repository = settings.getRemoteRepository("central", "https://repo1.maven.org/maven2/");

		assertThat(repository).isEqualTo(new RemoteRepository("central", "https://repo1.maven.org/maven2/", null));
	}

	@Test
	void attachesCredentialsByRepositoryIdWhenNoMirrorApplies() {

		MavenSettings settings = new MavenSettings(Map.of("internal", credentials), List.of());
		RemoteRepository repository = settings.getRemoteRepository("internal", "https://nexus.corp/repo/");

		assertThat(repository.credentials()).isEqualTo(credentials);
	}

	@Test
	void routesMatchingRepositoryThroughMirrorWithMirrorCredentials() {

		Mirror mirror = new Mirror("mirror", "https://mirror.corp/repo/", "*");
		MavenSettings settings = new MavenSettings(Map.of("mirror", mirrorCredentials), List.of(mirror));
		RemoteRepository repository = settings.getRemoteRepository("central", "https://repo1.maven.org/maven2/");

		assertThat(repository)
				.isEqualTo(new RemoteRepository("mirror", "https://mirror.corp/repo/", mirrorCredentials));
	}

	@Test
	void leavesRepositoryUntouchedWhenMirrorDoesNotMatch() {

		Mirror mirror = new Mirror("mirror", "https://mirror.corp/repo/", "central");
		MavenSettings settings = new MavenSettings(Map.of(), List.of(mirror));
		RemoteRepository repository = settings.getRemoteRepository("internal", "https://nexus.corp/repo/");

		assertThat(repository).isEqualTo(new RemoteRepository("internal", "https://nexus.corp/repo/", null));
	}

	@Test
	void firstMatchingMirrorWins() {

		Mirror specific = new Mirror("specific", "https://specific.corp/repo/", "central");
		Mirror catchAll = new Mirror("catch-all", "https://catch-all.corp/repo/", "*");
		MavenSettings settings = new MavenSettings(Map.of(), List.of(specific, catchAll));

		RemoteRepository repository = settings.getRemoteRepository("central", "https://repo1.maven.org/maven2/");

		assertThat(repository.id()).isEqualTo("specific");
	}

}
