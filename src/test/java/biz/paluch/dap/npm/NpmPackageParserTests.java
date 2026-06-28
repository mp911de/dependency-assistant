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

package biz.paluch.dap.npm;

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Platform-harness tests for {@link NpmPackageParser}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class NpmPackageParserTests {

	private final NpmPackageParser parser = new NpmPackageParser();

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "1.6.8",
			    "@vitejs/plugin-vue": "^3.1.2"
			  }
			}
			""")
	void parsesScopedAndUnscopedNames(PsiFile packageJson) {

		List<NpmDependency> dependencies = parser.parse(packageJson);

		assertThat(dependencies).extracting(NpmDependency::artifactId)
				.containsExactlyInAnyOrder(ArtifactId.of("axios", "axios"),
						ArtifactId.of("@vitejs", "plugin-vue"));
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "devDependencies": {
			    "react": "2.x"
			  }
			}
			""")
	void parsesPrefixRangeFromDevDependencies(PsiFile packageJson) {

		List<NpmDependency> dependencies = parser.parse(packageJson);

		assertThat(dependencies).hasSize(1);
		assertThat(dependencies.getFirst().version()).isInstanceOf(NpmVersionExpression.Prefix.class);
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "alias": "npm:@ankurk91/bootstrap-vue@^3.0.2"
			  }
			}
			""")
	void parsesAliasedDependency(PsiFile packageJson) {

		List<NpmDependency> dependencies = parser.parse(packageJson);

		assertThat(dependencies).hasSize(1);
		assertThat(dependencies.getFirst().artifactId()).isEqualTo(ArtifactId.of("alias", "alias"));
		assertThat(dependencies.getFirst().version()).isInstanceOfSatisfying(NpmVersionExpression.Alias.class,
				alias -> {
					assertThat(alias.packageName()).isEqualTo("@ankurk91/bootstrap-vue");
					assertThat(alias.text()).isEqualTo("3.0.2");
					assertThat(alias.renderUpdate(ArtifactVersion.of("3.1.0")))
							.isEqualTo("npm:@ankurk91/bootstrap-vue@^3.1.0");
				});
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "cli": "git+ssh://git@github.com:npm/cli.git#v1.0.27"
			  }
			}
			""")
	void parsesGitUrlDependency(PsiFile packageJson) {

		List<NpmDependency> dependencies = parser.parse(packageJson);

		assertThat(dependencies).hasSize(1);
		assertThat(dependencies.getFirst().version()).isInstanceOf(NpmVersionExpression.Git.class);
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "good": "1.0.0",
			    "bad-name!": "1.0.0",
			    "skip-empty": "",
			    "skip-latest": "latest",
			    "skip-or": ">=1.0.0 || >=2.0.0",
			    "skip-tarball": "https://example.com/foo.tgz",
			    "skip-file": "file:./local",
			    "skip-link": "link:../sibling",
			    "skip-workspace": "workspace:*"
			  }
			}
			""")
	void skipsOutOfScopeAndDisallowedEntries(PsiFile packageJson) {

		List<NpmDependency> dependencies = parser.parse(packageJson);

		assertThat(dependencies).hasSize(1);
		assertThat(dependencies.getFirst().artifactId()).isEqualTo(ArtifactId.of("good", "good"));
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "name": "demo",
			  "scripts": {"build": "tsc"},
			  "engines": {"node": ">=18"}
			}
			""")
	void returnsEmptyWhenNoDependencyKeys(PsiFile packageJson) {
		assertThat(parser.parse(packageJson)).isEmpty();
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": ["not", "an", "object"]
			}
			""")
	void returnsEmptyWhenDependenciesIsNotObject(PsiFile packageJson) {
		assertThat(parser.parse(packageJson)).isEmpty();
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": 1,
			    "lodash": true,
			    "react": null,
			    "vue": {"nested": "object"},
			    "express": ["array"]
			  }
			}
			""")
	void skipsNonStringDependencyValues(PsiFile packageJson) {
		assertThat(parser.parse(packageJson)).isEmpty();
	}

	@Test
	@ProjectFile(name = "package.json", content = "this is { not valid json")
	void returnsEmptyForMalformedJson(PsiFile packageJson) {
		assertThat(parser.parse(packageJson)).isEmpty();
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "1.6.8"
			  },
			  "devDependencies": {
			    "axios": "2.0.0"
			  }
			}
			""")
	void registersDuplicateKeysAcrossSections(PsiFile packageJson) {

		List<NpmDependency> dependencies = parser.parse(packageJson);

		assertThat(dependencies).hasSize(2);
		assertThat(dependencies).allSatisfy(
				d -> assertThat(d.artifactId()).isEqualTo(ArtifactId.of("axios", "axios")));
	}

}
