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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Mirror}.
 *
 * @author Mark Paluch
 */
class MirrorTests {

	@ParameterizedTest
	@CsvSource(delimiter = '|', textBlock = """
			# mirrorOf        | repositoryId | repositoryUrl                    | matches
			*                 | central      | https://repo1.maven.org/maven2/  | true
			central           | central      | https://repo1.maven.org/maven2/  | true
			central           | jcenter      | https://jcenter.bintray.com/     | false
			central,jcenter   | jcenter      | https://jcenter.bintray.com/     | true
			*,!central        | central      | https://repo1.maven.org/maven2/  | false
			*,!central        | internal     | https://nexus.corp/repo/         | true
			external:*        | internal     | https://nexus.corp/repo/         | true
			external:*        | localnexus   | http://localhost/repo/           | false
			external:*        | filerepo     | file:///opt/repo/                | false
			external:http:*   | internal     | http://nexus.corp/repo/          | true
			external:http:*   | internal     | https://nexus.corp/repo/         | false
			""")
	void appliesMavenMirrorSelectorSemantics(String mirrorOf, String repositoryId, String repositoryUrl,
			boolean matches) {

		Mirror mirror = new Mirror("mirror", "https://mirror.corp/repo/", mirrorOf);

		assertThat(mirror.matches(repositoryId, repositoryUrl)).isEqualTo(matches);
	}

	@Test
	void negationStopsMatchingRegardlessOfOrder() {

		Mirror mirror = new Mirror("mirror", "https://mirror.corp/repo/", "!central,*");

		assertThat(mirror.matches("central", "https://repo1.maven.org/maven2/")).isFalse();
		assertThat(mirror.matches("internal", "https://nexus.corp/repo/")).isTrue();
	}

}
