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

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MavenProjectMetadataPropertyResolver}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class MavenProjectMetadataPropertyResolverTests {

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<modelVersion>4.0.0</modelVersion>
				<parent>
					<groupId>com.example.parent</groupId>
					<artifactId>reactor</artifactId>
					<version>2.5.0</version>
				</parent>
				<artifactId>module</artifactId>
			</project>
			""")
	void resolvesParentCoordinatesFromReactorPom(PsiFile pom) {

		MavenProjectMetadataPropertyResolver resolver = new MavenProjectMetadataPropertyResolver((XmlFile) pom);

		assertThat(resolver.getProperty("project.parent.version")).isEqualTo("2.5.0");
		assertThat(resolver.getProperty("project.parent.groupId")).isEqualTo("com.example.parent");
		assertThat(resolver.getProperty("project.parent.artifactId")).isEqualTo("reactor");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<modelVersion>4.0.0</modelVersion>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
			</project>
			""")
	void resolvesLegacyPomAliasesToProjectValues(PsiFile pom) {

		MavenProjectMetadataPropertyResolver resolver = new MavenProjectMetadataPropertyResolver((XmlFile) pom);

		assertThat(resolver.getProperty("pom.version")).isEqualTo(resolver.getProperty("project.version"));
		assertThat(resolver.getProperty("pom.groupId")).isEqualTo("com.example");
		assertThat(resolver.getProperty("pom.artifactId")).isEqualTo("demo");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<modelVersion>4.0.0</modelVersion>
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
			</project>
			""")
	void leavesParentPlaceholdersUnresolvedWithoutParent(PsiFile pom) {

		MavenProjectMetadataPropertyResolver resolver = new MavenProjectMetadataPropertyResolver((XmlFile) pom);

		assertThat(resolver.containsProperty("project.parent.version")).isFalse();
		assertThat(resolver.getProperty("project.parent.version")).isNull();
		assertThat(resolver.getProperty("project.parent.groupId")).isNull();
		assertThat(resolver.getProperty("pom.parent.version")).isNull();
	}

}
