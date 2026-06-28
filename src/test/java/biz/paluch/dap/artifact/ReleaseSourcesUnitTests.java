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

package biz.paluch.dap.artifact;

import java.util.List;

import biz.paluch.dap.fixtures.TestReleaseSource;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ReleaseSources}.
 *
 * @author Mark Paluch
 */
class ReleaseSourcesUnitTests {

	private static final ArtifactId LETTUCE = ArtifactId.of("io.lettuce", "lettuce-core");

	@Test
	void exposesSourceIdentifiersInOrder() {

		ReleaseSources sources = new ReleaseSources(LETTUCE, PackageSystem.MAVEN,
				List.of(new TestReleaseSource("central"), new TestReleaseSource("portal")));

		assertThat(sources.sourceIds()).containsExactly("central", "portal");
	}

	@Test
	void retainsSourcesAcceptedByPredicate() {

		ReleaseSources sources = new ReleaseSources(LETTUCE, PackageSystem.MAVEN,
				List.of(new TestReleaseSource("central"), new TestReleaseSource("github")));

		assertThat(sources.filter(source -> source.getId().equals("github")).sourceIds()).containsExactly("github");
	}

	@Test
	void keepsAllSourcesWhenPredicateRejectsEveryone() {

		ReleaseSources sources = new ReleaseSources(LETTUCE, PackageSystem.MAVEN,
				List.of(new TestReleaseSource("central"), new TestReleaseSource("portal")));

		assertThat(sources.filter(source -> false).sourceIds()).containsExactly("central", "portal");
	}

}
