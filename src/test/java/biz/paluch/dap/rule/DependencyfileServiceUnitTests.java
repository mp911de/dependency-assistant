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

package biz.paluch.dap.rule;

import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyfileService}.
 *
 * @author Mark Paluch
 */
class DependencyfileServiceUnitTests {

	private static final Path ROOT = Path.of("/work/project");

	private static final Path HOME = Path.of("/home/dev");

	@Test
	void searchesInProjectLocationsForUntrustedProject() {

		List<Path> candidates = DependencyfileService.candidatePaths(ROOT, HOME, false);

		assertThat(candidates).containsExactly(ROOT.resolve("dependencyfile.json"),
				ROOT.resolve(".idea").resolve("dependencyfile.json"));
	}

	@Test
	void searchesParentAndHomeOnlyForTrustedProject() {

		List<Path> candidates = DependencyfileService.candidatePaths(ROOT, HOME, true);

		assertThat(candidates).containsExactly(ROOT.resolve("dependencyfile.json"),
				ROOT.resolve(".idea").resolve("dependencyfile.json"),
				Path.of("/work").resolve("dependencyfile.json"), HOME.resolve("dependencyfile.json"));
	}

	@Test
	void omitsParentWhenProjectRootHasNoParent() {

		List<Path> candidates = DependencyfileService.candidatePaths(Path.of("/"), HOME, true);

		assertThat(candidates).containsExactly(Path.of("/").resolve("dependencyfile.json"),
				Path.of("/").resolve(".idea").resolve("dependencyfile.json"), HOME.resolve("dependencyfile.json"));
	}

}
