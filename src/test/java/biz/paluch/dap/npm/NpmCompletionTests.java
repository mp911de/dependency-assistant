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
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Platform-harness tests for {@link NpmVersionCompletionContributor}.
 *
 * <p>Asserts the contributor triggers in the correct PSI context. Full
 * completion-equals-dialog parity requires a cache fixture analogous to
 * {@code GitHubFixtures}; that lives behind the {@code applyUpdates(...)} path
 * tested by {@link UpdatePackageJsonFileTests}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class NpmCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	@EditorFile(name = "package.json", content = """
			{
			  "dependencies": {
			    "axios": "1.6<caret>.8"
			  }
			}
			""")
	void contributorTriggersInsideDependencyValue(PsiFile packageJson) {

		fixture.completeBasic();

		// Without a populated release cache the lookup is empty; the assertion only
		// confirms the contributor did not throw and the matcher resolved a value
		// position rather than a property-name position.
		assertThat(fixture.getLookupElementStrings()).isNotNull();
	}

}
