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

import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Tests for {@link MavenBomParser} reading BOM POM files through the IntelliJ
 * Maven DOM.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenBomParserTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	@ProjectFile(name = "aggregate-bom-3.5.0.pom", content = """
			<project>
				<groupId>com.example.aggregate</groupId>
				<artifactId>aggregate-bom</artifactId>
				<version>3.5.0</version>
				<properties>
					<member.version>6.2.7</member.version>
				</properties>
				<dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>com.example.aggregate</groupId>
							<artifactId>member-core</artifactId>
							<version>${member.version}</version>
						</dependency>
						<dependency>
							<groupId>${project.groupId}</groupId>
							<artifactId>member-api</artifactId>
							<version>${project.version}</version>
						</dependency>
						<dependency>
							<groupId>com.example.aggregate</groupId>
							<artifactId>member-unresolved</artifactId>
							<version>${missing.version}</version>
						</dependency>
						<dependency>
							<groupId>com.example.nested</groupId>
							<artifactId>nested-bom</artifactId>
							<version>1.0.0</version>
							<type>pom</type>
							<scope>import</scope>
						</dependency>
					</dependencies>
				</dependencyManagement>
			</project>
			""")
	void readsMembersResolvingPropertyAndProjectReferences(PsiFile bomFile) {

		Map<ArtifactId, ArtifactVersion> members = readMembers(bomFile);

		// unresolvable property entries are skipped; unresolvable import entries do not
		// register as members
		assertThat(members)
				.containsEntry(ArtifactId.of("com.example.aggregate", "member-core"), ArtifactVersion.of("6.2.7"))
				.containsEntry(ArtifactId.of("com.example.aggregate", "member-api"), ArtifactVersion.of("3.5.0"))
				.hasSize(2);
	}

	@Test
	@ProjectFile(name = "child-bom-2.0.0.pom", content = """
			<project>
				<parent>
					<groupId>com.example.aggregate</groupId>
					<artifactId>aggregate-parent</artifactId>
					<version>2.0.0</version>
				</parent>
				<artifactId>child-bom</artifactId>
				<dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>${project.groupId}</groupId>
							<artifactId>member-core</artifactId>
							<version>${project.version}</version>
						</dependency>
					</dependencies>
				</dependencyManagement>
			</project>
			""")
	void fallsBackToParentCoordinatesForProjectReferences(PsiFile bomFile) {

		assertThat(readMembers(bomFile))
				.containsEntry(ArtifactId.of("com.example.aggregate", "member-core"), ArtifactVersion.of("2.0.0"))
				.hasSize(1);
	}

	private Map<ArtifactId, ArtifactVersion> readMembers(PsiFile bomFile) {
		return new MavenBomParser(fixture.getProject(), bomFile.getVirtualFile()).readMembers();
	}

}
