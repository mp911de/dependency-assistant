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

package biz.paluch.dap.maven.wrapper;

import java.net.URI;

import biz.paluch.dap.artifact.RemoteRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link WrapperProperty}.
 *
 * @author Mark Paluch
 */
class WrapperPropertyUnitTests {

	@Test
	void parsesRepositoryCorrectly() {

		RemoteRepository repository = WrapperProperty.DISTRIBUTION.parseRemoteRepository(
				URI.create("https://foo.bar.baz/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"), null);

		assertThat(repository.url()).isEqualTo("https://foo.bar.baz/");
	}

	@Test
	void parsesRepositoryWithPathCorrectly() {

		RemoteRepository repository = WrapperProperty.DISTRIBUTION.parseRemoteRepository(
				URI.create("https://foo.bar.baz/x/y/z/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"),
				null);

		assertThat(repository.url()).isEqualTo("https://foo.bar.baz/x/y/z/");
	}

	@Test
	void parsesIncompleteRepositoryWithPathCorrectly() {

		RemoteRepository repository = WrapperProperty.DISTRIBUTION.parseRemoteRepository(
				URI.create("https://foo.bar.baz/x/y/z/org/apache/maven"),
				null);

		assertThat(repository.url()).isEqualTo("https://foo.bar.baz/x/y/z/");
	}

	@Test
	void defaultsToMavenCentral() {

		RemoteRepository repository = WrapperProperty.DISTRIBUTION.parseRemoteRepository(
				URI.create("https://foo.bar.baz/x/z/org/"),
				null);

		assertThat(repository.url()).isEqualTo(RemoteRepository.mavenCentral().url());
	}

}
