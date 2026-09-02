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

package biz.paluch.dap.artifact;

import java.net.URI;

import biz.paluch.dap.fixtures.Coordinates;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MavenRepository}.
 *
 * @author Mark Paluch
 */
class MavenRepositoryUnitTests {

	@ParameterizedTest
	@ValueSource(strings = {"org.scala-lang:scala-library", "com.101tec:zkc_lient",
			"org.eclipse.jdt:ORG.eclipse.jdt.annotation"})
	void shouldAcceptArtifactIds(String coordinates) {
		MavenRepository.validate(Coordinates.of(coordinates + ":1.0").getArtifactId());
	}

	@ParameterizedTest
	@ValueSource(strings = {"..scala-lang:scala-library:1.0", "com.101tec:../zkc_lient:1.0",
			"http://org.eclipse.jdt:1.0"})
	void shouldRejectInvalidArtifacts(String coordinates) {
		assertThatExceptionOfType(ArtifactNotFoundException.class)
				.isThrownBy(() -> MavenRepository.validate(Coordinates.of(coordinates)
						.getArtifactId()));
	}

	@ParameterizedTest
	@CsvSource(textBlock = """
			https://repo.example.com/maven2/, https://repo.example.com/maven2/org/foo/maven-metadata.xml , true
			https://repo.example.com/maven2/, https://repo.example.com:443/maven2/org/foo/               , true
			https://repo.example.com/maven2/, https://REPO.example.com/maven2/org/foo/                   , true
			https://repo.example.com/maven2/, http://repo.example.com/maven2/org/foo/                    , false
			https://repo.example.com/maven2/, https://repo.example.com:8443/maven2/org/foo/              , false
			https://repo.example.com/maven2/, https://other.example.com/maven2/org/foo/                  , false
			https://repo.example.com/maven2/, https://repo.example.com/other/org/foo/                    , false
			https://repo.example.com        , https://repo.example.com/anything                          , true
			""")
	void credentialsAreLimitedToRepositoryScope(String repository, String target, boolean expected) {
		assertThat(MavenRepository.isWithinRepository(URI.create(repository), URI.create(target))).isEqualTo(expected);
	}

}
