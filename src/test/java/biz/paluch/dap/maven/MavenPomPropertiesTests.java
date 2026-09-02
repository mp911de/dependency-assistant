/*
 * Copyright 2026-present the original author or authors.
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

import java.util.List;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MavenPomProperties}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class MavenPomPropertiesTests {

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
	void resolvesParentCoordinatesFromPom(XmlFile pom) {

		MavenPomProperties properties = MavenPomProperties.from(pom);

		assertThat(properties.getProperty("project.parent.version")).isEqualTo("2.5.0");
		assertThat(properties.getProperty("project.parent.groupId")).isEqualTo("com.example.parent");
		assertThat(properties.getProperty("project.parent.artifactId")).isEqualTo("reactor");
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
	void resolvesLegacyPomAliasesToProjectValues(XmlFile pom) {

		MavenPomProperties properties = MavenPomProperties.from(pom);

		assertThat(properties.getProperty("pom.version")).isEqualTo(properties.getProperty("project.version"));
		assertThat(properties.getProperty("pom.groupId")).isEqualTo("com.example");
		assertThat(properties.getProperty("pom.artifactId")).isEqualTo("demo");
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<modelVersion>4.0.0</modelVersion>
				<artifactId>demo</artifactId>
				<scm>
					<tag>build-1.0</tag>
				</scm>
				<properties>
					<repository.url>https://github.com/example/example-repo</repository.url>
				</properties>
			</project>
			""")
	void registersNestedElementsAndPropertiesEntries(XmlFile pom) {

		MavenPomProperties properties = MavenPomProperties.from(pom);

		assertThat(properties.getProperty("project.scm.tag")).isEqualTo("build-1.0");
		assertThat(properties.getProperty("pom.scm.tag")).isEqualTo("build-1.0");
		assertThat(properties.getProperty("repository.url")).isEqualTo("https://github.com/example/example-repo");
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
	void leavesParentPlaceholdersUnresolvedWithoutParent(XmlFile pom) {

		MavenPomProperties properties = MavenPomProperties.from(pom);

		assertThat(properties.containsProperty("project.parent.version")).isFalse();
		assertThat(properties.getProperty("project.parent.version")).isNull();
		assertThat(properties.getProperty("project.parent.groupId")).isNull();
		assertThat(properties.getProperty("pom.parent.version")).isNull();
	}

	@Test
	@ProjectFile(name = "pom.xml", content = """
			<project>
				<properties><library.version>1.0</library.version></properties>
				<dependencies><dependency>
					<groupId>com.example</groupId><artifactId>library</artifactId>
					<version>1.0</version><scope>test</scope>
				</dependency></dependencies>
			</project>
			""")
	void identifiesOnlyVersionAndPropertyValueElements(XmlFile pom) {

		XmlTag project = pom.getRootTag();
		XmlTag dependency = project.findFirstSubTag("dependencies").findFirstSubTag("dependency");
		XmlText groupId = dependency.findFirstSubTag("groupId").getValue().getTextElements()[0];
		XmlText version = dependency.findFirstSubTag("version").getValue().getTextElements()[0];
		XmlText property = project.findFirstSubTag("properties").getSubTags()[0].getValue().getTextElements()[0];

		assertThat(XmlUtil.isVersionElement(groupId)).isFalse();
		assertThat(XmlUtil.isVersionElement(version)).isTrue();
		assertThat(XmlUtil.isVersionElement(property)).isTrue();
	}

	@Test
	@ProjectFile(name = "child/pom.xml", content = """
			<project><artifactId>child</artifactId></project>
			""")
	@ProjectFile(name = "parent/pom.xml", content = """
			<project><properties><shared.version>2.0</shared.version></properties></project>
			""")
	@ProjectFile(name = "grandparent/pom.xml", content = """
			<project><properties><shared.version>1.0</shared.version></properties></project>
			""")
	void directParentPrecedesGrandparent(@ProjectFile("child/pom.xml") XmlFile child,
			@ProjectFile("parent/pom.xml") XmlFile parent,
			@ProjectFile("grandparent/pom.xml") XmlFile grandparent) {

		MavenPomProperties properties = MavenPomProperties.from(List.of(child, parent, grandparent));

		assertThat(properties.getProperty("shared.version")).isEqualTo("2.0");
	}

	@Test
	@ProjectFile(name = "child/pom.xml", content = """
			<project><dependencies><dependency>
				<groupId>com.example</groupId><artifactId>library</artifactId>
				<version>${shared.version}</version>
			</dependency></dependencies></project>
			""")
	@ProjectFile(name = "parent/pom.xml", content = """
			<project><properties><shared.version>2.0</shared.version></properties></project>
			""")
	@ProjectFile(name = "grandparent/pom.xml", content = """
			<project><properties><shared.version>1.0</shared.version></properties></project>
			""")
	void parserUsesDirectParentBeforeGrandparent(@ProjectFile("child/pom.xml") XmlFile child,
			@ProjectFile("parent/pom.xml") XmlFile parent,
			@ProjectFile("grandparent/pom.xml") XmlFile grandparent) {

		MavenPomProperties properties = MavenPomProperties.from(List.of(child, parent, grandparent));
		List<ArtifactDeclaration> declarations = new MavenParser(properties).parsePomFile(child);

		assertThat(declarations).singleElement()
				.extracting(it -> it.getVersion().toString()).isEqualTo("2.0");
	}

}
