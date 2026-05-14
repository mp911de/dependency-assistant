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

import biz.paluch.dap.artifact.ArtifactVersion;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit test for {@link UpdateMavenWrapperProperties}.
 * 
 * @author Mark Paluch
 */
class UpdateMavenWrapperPropertiesUnitTests {

	@ParameterizedTest
	@ValueSource(strings = {"1.2.3", "1.2.3-SNAPSHOT"})
	void updatesVersion(String toVersion) {

		String format = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/%1$s/apache-maven-%1$s-bin.zip";
		String url = format.formatted("4.5.6");
		String result = UpdateMavenWrapperProperties.getRewrittenUrl(url, ArtifactVersion.of(toVersion));

		assertThat(result).isEqualTo(format.formatted(toVersion));
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.2.3", "1.2.3-SNAPSHOT"})
	void updatesVersionWithJar(String toVersion) {

		String format = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/%1$s/apache-maven-%1$s.jar";
		String url = format.formatted("4.5.6");
		String result = UpdateMavenWrapperProperties.getRewrittenUrl(url, ArtifactVersion.of(toVersion));

		assertThat(result).isEqualTo(format.formatted(toVersion));
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.2.3", "1.2.3-SNAPSHOT"})
	void updatesVersionFromSnapshot(String toVersion) {

		String format = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/%1$s/apache-maven-%1$s-bin.tar.gz";
		String url = format.formatted("4.5.6-SNAPSHOT");
		String result = UpdateMavenWrapperProperties.getRewrittenUrl(url, ArtifactVersion.of(toVersion));

		assertThat(result).isEqualTo(format.formatted(toVersion));
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.2.3", "1.2.3-SNAPSHOT"})
	void updatesVersionFromSnapshotJar(String toVersion) {

		String format = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/%1$s/apache-maven-%1$s.jar";
		String url = format.formatted("4.5.6-SNAPSHOT");
		String result = UpdateMavenWrapperProperties.getRewrittenUrl(url, ArtifactVersion.of(toVersion));

		assertThat(result).isEqualTo(format.formatted(toVersion));
	}

}
