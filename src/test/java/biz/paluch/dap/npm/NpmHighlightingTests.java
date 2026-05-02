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

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for highlighting NPM {@code package.json}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class NpmHighlightingTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		NpmFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.4"
			  }
			}
			""")
	void exactVersionSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "^1.0.0-alpha.4"
			  }
			}
			""")
	void exactVersionWithModifierSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.1 - 1.0.0-alpha.4"
			  }
			}
			""")
	void hyphenRangeSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.1 - 1.0.0-alpha.5"
			  }
			}
			""")
	void hyphenRangeSuggestsNoUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasNoGutterMarks();
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": ">=1.0.0-alpha.1 <1.0.0-alpha.4"
			  }
			}
			""")
	void comparatorPairSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.x"
			  }
			}
			""")
	void prefixRangeSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasSingleGutterContaining("1.0.0-alpha.5").highlights("1.0.x");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "npm:@springio/antora-xref-extension@^1.0.0-alpha.4"
			  }
			}
			""")
	void aliasSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasSingleGutterContaining("1.0.0-alpha.5").highlights("1.0.0-alpha.4");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "git+https://github.com/spring-io/antora-xref-extension.git#1.0.0-alpha.4"
			  }
			}
			""")
	void gitUrlWithTagCommittishSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasSingleGutterContaining("1.0.0-alpha.5").highlights("1.0.0-alpha.4");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "git+ssh://git@github.com:spring-io/antora-xref-extension.git#d1185ce59f"
			  }
			}
			""")
	void gitUrlWithShaCommittishSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasSingleGutterContaining("1.0.0-alpha.5");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "spring-io/antora-xref-extension#d1185ce59f"
			  }
			}
			""")
	void shorthandGitWithShaCommittishSuggestsUpgrade(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasSingleGutterContaining("1.0.0-alpha.5").highlights("d1185ce59f");
	}

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "@springio/antora-xref-extension": "1.0.0-alpha.5"
			  }
			}
			""")
	void currentLatestVersionHasNoGutter(PsiFile packageJson) {

		NpmFixtures.analyze(packageJson);

		assertThat(fixture).hasNoGutterMarks();
	}

}
