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

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;


/**
 * PSI-level integration tests for highlighting NPM {@code package.json}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class NpmHighlightingTests {

	@BeforeEach
	void setUp(Project project) {
		NpmFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.4"
			  }
			}
			""")
	void exactVersionSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "^1.0.0-alpha.4"
			  }
			}
			""")
	void exactVersionWithModifierSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.1 - 1.0.0-alpha.4"
			  }
			}
			""")
	void hyphenRangeSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.1 - 1.0.0-alpha.5"
			  }
			}
			""")
	void hyphenRangeSuggestsNoUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasNoGutterMarks();
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": ">=1.0.0-alpha.1 <1.0.0-alpha.4"
			  }
			}
			""")
	void comparatorPairSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "5.0.x"
			  }
			}
			""")
	void prefixPreviewSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutterContaining("5.4.0").highlights("5.0.x");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "5.0.x"
			  }
			}
			""")
	void prefixSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutter().highlights("5.0.x");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "npm:@springio/antora-xref-extension@^1.0.0-alpha.4"
			  }
			}
			""")
	void aliasSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutterContaining("1.0.0-alpha.5").highlights("1.0.0-alpha.4");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "git+https://github.com/spring-io/antora-xref-extension.git#1.0.0-alpha.4"
			  }
			}
			""")
	void gitUrlWithTagCommittishSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutterContaining("1.0.0-alpha.5").highlights("1.0.0-alpha.4");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "git+ssh://git@github.com:spring-io/antora-xref-extension.git#d1185ce59f"
			  }
			}
			""")
	void gitUrlWithShaCommittishSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "axios/axios#d1185ce59f"
			  }
			}
			""")
	void shorthandGitWithShaCommittishSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasSingleGutter().highlights("d1185ce59f");
	}

	@Test
	@ProjectFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.5"
			  }
			}
			""")
	void currentLatestVersionHasNoGutter(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(packageJson).hasNoGutterMarks();
	}

}
