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

package biz.paluch.dap.assistant.action;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.ReleaseSources;
import biz.paluch.dap.assistant.check.DependencyCheck;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidates;
import biz.paluch.dap.assistant.check.ReleaseLookupResult;
import biz.paluch.dap.assistant.check.ReleaseResolver;
import biz.paluch.dap.assistant.check.UpgradeScope;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.fixtures.DependencyAssistantFixtures;
import biz.paluch.dap.gradle.GradleFixtures;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.SameThreadExecutorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for the multi-context
 * {@link DependencyCheck#findDependencyUpgrades} engine.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class DependencyCheckScopeTests {

	@BeforeEach
	void setUp(Project project) {
		DependencyAssistantFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "engine-a/build.gradle", content = """
			dependencies {
			    implementation 'io.lettuce:lettuce-core:7.4.1.RELEASE'
			}
			""")
	@ProjectFile(name = "engine-b/build.gradle", content = """
			dependencies {
			    implementation 'io.lettuce:lettuce-core:7.5.0.RELEASE'
			}
			""")
	void mergesSameArtifactAcrossFilesIntoOneRow(Project project,
			@ProjectFile("engine-a/build.gradle") PsiFile a, @ProjectFile("engine-b/build.gradle") PsiFile b) {

		GradleFixtures.analyze(a);
		GradleFixtures.analyze(b);

		DependencyUpgradeCandidates result = check(project, a, b);

		assertThat(result.candidates()).singleElement().satisfies(merged -> {
			assertThat(merged.getArtifactId()).isEqualTo(ArtifactId.of("io.lettuce", "lettuce-core"));
			assertThat(merged.getDeclaredVersions().hasVersionDrift()).isTrue();
		});
	}

	@Test
	@ProjectFile(name = "single/build.gradle", content = """
			dependencies {
			    implementation 'io.lettuce:lettuce-core:7.4.1.RELEASE'
			}
			""")
	void singleFileScopeProducesOneOccurrenceRow(Project project, @ProjectFile("single/build.gradle") PsiFile file) {

		GradleFixtures.analyze(file);

		DependencyUpgradeCandidates result = check(project, file);

		assertThat(result.candidates()).singleElement().satisfies(merged -> {
			assertThat(merged.getArtifactId()).isEqualTo(ArtifactId.of("io.lettuce", "lettuce-core"));
			assertThat(merged.getCurrentVersion()).isEqualTo(ArtifactVersion.of("7.4.1.RELEASE"));
			assertThat(merged.getDeclaredVersions().hasVersionDrift()).isFalse();
			assertThat(merged.getUpdateCandidate().hasUpgradeTargets()).isTrue();
		});
	}

	private static DependencyUpgradeCandidates check(Project project, PsiFile... selection) {

		List<VirtualFile> files = Arrays.stream(selection).map(PsiFile::getVirtualFile).toList();
		UpgradeScope scope = UpgradeScopeResolver.resolve(project, new UpgradeRequest(files, null));

		DependencyCheck dependencyCheck = new DependencyCheck(project) {

			@Override
			protected Map<ArtifactId, ReleaseLookupResult> resolveReleases(ProgressIndicator indicator,
					List<ReleaseSources> artifactSources, ReleaseResolver.Consistency consistency) {
				return super.resolveReleases(indicator, artifactSources, consistency, new SameThreadExecutorService(),
						new SameThreadExecutorService());
			}

		};

		return dependencyCheck.findDependencyUpgrades(new EmptyProgressIndicator(), scope);
	}

}
