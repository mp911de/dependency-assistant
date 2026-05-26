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

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level integration tests for {@link MavenParser}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class MavenExtensionsParserTests {

	private @TestFixture Project project;

	@BeforeEach
	void setUp() {
		MavenFixtures.setup(project);
	}

	@Test
	@ProjectFile(name = "extensions.xml", content = """
				<extensions>
					<extension>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>3.19.0</version>
					</extension>
				</extensions>
			""")
	void directDependenciesWithInlineVersionsAreDiscovered(XmlFile file) {

		DependencyCollector collector = new UpdateExtensionsProjectState(file.getProject()).doUpdate(file);

		assertThat(collector)
				.hasDependencyUsage("commons-lang3")
				.hasVersion("3.19.0")
				.hasDeclaration(DeclarationSource.dependency());
	}

}
