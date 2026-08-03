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

import java.util.List;

import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Tests for {@link MavenPomProperties} inheritance order.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class MavenPomPropertiesTests {

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

		assertThat(MavenUtils.isVersionElement(groupId)).isFalse();
		assertThat(MavenUtils.isVersionElement(version)).isTrue();
		assertThat(MavenUtils.isVersionElement(property)).isTrue();
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

		MavenPomProperties properties = MavenPomProperties.combined(child, List.of(parent, grandparent));

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

		MavenPomProperties properties = MavenPomProperties.combined(child, List.of(parent, grandparent));
		List<ArtifactDeclaration> declarations = new MavenParser(new Cache(), properties).parsePomFile(child);

		assertThat(declarations).singleElement()
				.extracting(it -> it.getVersion().toString()).isEqualTo("2.0");
	}

}
