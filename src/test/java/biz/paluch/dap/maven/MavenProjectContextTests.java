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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Versioned;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MavenProjectContext#getProjectVersion()}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenProjectContextTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>2.1.0</version>
			</project>
			""")
	void readsProjectVersionFromPom(PsiFile pomFile) {
		assertThat(context(pomFile).getProjectVersion()).isEqualTo(Versioned.of(ArtifactVersion.of("2.1.0")));
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<parent>
					<groupId>org.springframework.boot</groupId>
					<artifactId>spring-boot-starter-parent</artifactId>
					<version>3.2.0</version>
				</parent>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
			</project>
			""")
	void fallsBackToParentVersion(PsiFile pomFile) {
		assertThat(context(pomFile).getProjectVersion()).isEqualTo(Versioned.of(ArtifactVersion.of("3.2.0")));
	}

	@Test
	@EditorFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
			</project>
			""")
	void returnsUnversionedWhenNoVersionDeclared(PsiFile pomFile) {
		assertThat(context(pomFile).getProjectVersion()).isEqualTo(Versioned.unversioned());
	}

	private MavenProjectContext context(PsiFile pomFile) {
		MavenFixtures.analyze(pomFile);
		return MavenProjectContext.of(fixture.getProject(), pomFile);
	}

}
