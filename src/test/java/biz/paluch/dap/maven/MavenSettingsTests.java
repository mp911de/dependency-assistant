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

package biz.paluch.dap.maven;

import java.net.URI;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RepositoryCredentials;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MavenSettings}.
 *
 * @author Mark Paluch
 */
class MavenSettingsTests {

	RepositoryCredentials credentials = new RepositoryCredentials("internal", "alice", "secret");

	RepositoryCredentials mirrorCredentials = new RepositoryCredentials("mirror", "alice", "secret");

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

		assertThat(repository.getId()).isEqualTo("specific");
	}

	@Test
	void exactMirrorPrecedesEarlierWildcardMirror() {

		Mirror wildcard = new Mirror("catch-all", "https://catch-all.corp/repo/", "*");
		Mirror exact = new Mirror("central-mirror", "https://central.corp/repo/", "central");
		MavenSettings settings = new MavenSettings(Map.of(), List.of(wildcard, exact));

		RemoteRepository repository = settings.getRemoteRepository("central", "https://repo1.maven.org/maven2/");

		assertThat(repository.getId()).isEqualTo("central-mirror");
	}

	@ParameterizedTest
	@ValueSource(strings = {"https://nexus.corp/releases/library/", "https://nexus.corp/snapshots/",
			"https://nexus.corp:443/releases/", "HTTPS://NEXUS.Corp/releases/"})
	void attachesBoundCredentialsToSameOrigin(String url) {

		RepositoryCredentials bound = new RepositoryCredentials("internal", "alice", "secret",
				List.of(URI.create("https://nexus.corp/releases/")));
		MavenSettings settings = new MavenSettings(Map.of("internal", bound), List.of());

		assertThat(settings.getRemoteRepository("internal", url).credentials()).isEqualTo(bound);
	}

	@ParameterizedTest
	@ValueSource(strings = {"http://nexus.corp/releases/", "https://nexus.corp:8443/releases/",
			"https://attacker.example/releases/"})
	void refusesBoundCredentialsForOtherOrigin(String url) {

		RepositoryCredentials bound = new RepositoryCredentials("internal", "alice", "secret",
				URI.create("https://nexus.corp/releases/"));
		MavenSettings settings = new MavenSettings(Map.of("internal", bound), List.of());

		assertThat(settings.getRemoteRepository("internal", url).credentials()).isNull();
	}

	@Test
	void acceptsCredentialsWithEmptyBinding() {

		RepositoryCredentials bound = new RepositoryCredentials("internal", "alice", "secret");
		MavenSettings settings = new MavenSettings(Map.of("internal", bound), List.of());

		assertThat(settings.getRemoteRepository("internal", "https://nexus.corp/releases/").credentials()).isNotNull();
	}

}
