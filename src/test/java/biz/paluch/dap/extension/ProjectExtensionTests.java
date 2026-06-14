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

package biz.paluch.dap.extension;

import com.intellij.openapi.project.Project;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Integration tests for {@link ProjectExtension}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class ProjectExtensionTests {

	private @TestFixture Project project;

	@Test
	void injectsProjectFieldAndParameter(Project parameter) {

		assertThat(project).isNotNull();
		assertThat(parameter).isSameAs(project);
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
			</project>
			""")
	void injectsProjectFileParameter(XmlFile file) {

		assertThat(file.getName()).isEqualTo("pom.xml");

		XmlTag rootTag = file.getDocument().getRootTag();
		assertThat(rootTag.getName()).isEqualTo("project");
	}

}
