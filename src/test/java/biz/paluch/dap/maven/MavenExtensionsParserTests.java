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
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.support.ArtifactReference;
import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
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

		MavenExtensionsAssistant assistant = new MavenExtensionsAssistant();
		DependencyCollector collector = new DependencyCollector(PackageSystem.MAVEN);
		assistant.collect(file, collector);

		assertThat(collector)
				.hasDependencyUsage("commons-lang3")
				.hasVersion("3.19.0")
				.hasDeclaration(DeclarationSource.dependency());
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
	void resolvesVersionTextAsArtifactReference(XmlFile file) {

		XmlTag extension = file.getRootTag().findFirstSubTag("extension");
		XmlText version = extension.findFirstSubTag("version").getValue().getTextElements()[0];

		ArtifactReference reference = new MavenExtensionsReferenceResolver(file).resolveArtifactReference(version);

		assertThat(reference.isResolved()).isTrue();
		assertThat(reference.getArtifactId().toString()).isEqualTo("org.apache.commons:commons-lang3");
	}

}
