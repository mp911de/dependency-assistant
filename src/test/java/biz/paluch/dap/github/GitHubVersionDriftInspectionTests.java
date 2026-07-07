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

import java.util.List;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.fixtures.Inspections;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for version drift inspection on GitHub Actions workflows.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GitHubVersionDriftInspectionTests {

	@Test
	@ProjectFile(name = ".github/workflows/ci.yml", content = """
			jobs:
			  build:
			    steps:
			    - uses: actions/setup-java@1bcf9fb12cf4aa7d266a90ae39939e61372fe520 # v5.4.0
			""")
	void doesNotReportDriftWhenStateShaMatchesResolvedGitVersion(Project project, PsiFile workflowFile) {

		GitHubFixtures.setup(project);
		GitHubFixtures.analyze(workflowFile);
		GitHubFixtures.registerSetupJavaShaDependency(project);

		List<ProblemDescriptor> problems = Inspections.inspect(project, workflowFile);

		assertThat(problems).isEmpty();
	}

}
