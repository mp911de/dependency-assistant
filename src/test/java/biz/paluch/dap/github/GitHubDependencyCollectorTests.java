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

package biz.paluch.dap.github;

import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.GitRef;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for {@link GitHubDependencyCollector}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GitHubDependencyCollectorTests {

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			    - uses: actions/setup-java@1bcf9fb12cf4aa7d266a90ae39939e61372fe520 # v5.4.0
			""")
	void keepsUnresolvedSha(PsiFile workflowFile) {

		DependencyCollector collector = new GitHubDependencyCollector(workflowFile.getProject()).collect(workflowFile);

		Dependency usage = collector.getUsage(TestGitHubReleases.SETUP_JAVA);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion()).isInstanceOf(GitRef.class)
				.hasToString("1bcf9fb12cf4aa7d266a90ae39939e61372fe520");
	}

}
