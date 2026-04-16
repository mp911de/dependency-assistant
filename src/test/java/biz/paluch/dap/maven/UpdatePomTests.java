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

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for {@link UpdatePom}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UpdatePomTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	void dependencyInlineVersionIsUpdated() {

		PsiFile pom = fixture.addFileToProject("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencies>
					<dependency>
						<groupId>org.apache.commons</groupId>
						<artifactId>commons-lang3</artifactId>
						<version>3.19.0</version>
					</dependency>
				</dependencies>
				"""));

		applyUpdate(pom, "org.apache.commons", "commons-lang3", "3.19.0", DeclarationSource.dependency(),
				VersionSource.declared("3.19.0"), "3.20.0");

		assertThat(pom.getText()).contains("<version>3.20.0</version>").doesNotContain("<version>3.19.0</version>");
	}

	@Test
	void dependencyManagementInlineVersionIsUpdated() {

		PsiFile pom = fixture.addFileToProject("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<dependencyManagement>
					<dependencies>
						<dependency>
							<groupId>org.springframework.boot</groupId>
							<artifactId>spring-boot-dependencies</artifactId>
							<version>3.5.0</version>
							<type>pom</type>
							<scope>import</scope>
						</dependency>
					</dependencies>
				</dependencyManagement>
				"""));

		applyUpdate(pom, "org.springframework.boot", "spring-boot-dependencies", "3.5.0", DeclarationSource.managed(),
				VersionSource.declared("3.5.0"), "3.5.1");

		assertThat(pom.getText()).contains("<version>3.5.1</version>").doesNotContain("<version>3.5.0</version>");
	}

	@Test
	void propertyVersionIsUpdated() {

		PsiFile pom = fixture.addFileToProject("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<properties>
					<spring.version>6.1.0</spring.version>
				</properties>
				<dependencies>
					<dependency>
						<groupId>org.springframework</groupId>
						<artifactId>spring-core</artifactId>
						<version>${spring.version}</version>
					</dependency>
				</dependencies>
				"""));

		applyUpdate(pom, "org.springframework", "spring-core", "6.1.0", DeclarationSource.dependency(),
				VersionSource.property("spring.version"), "6.2.0");

		assertThat(pom.getText()).contains("<spring.version>6.2.0</spring.version>");
	}

	@Test
	void pluginInlineVersionIsUpdated() {

		PsiFile pom = fixture.addFileToProject("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<build>
					<plugins>
						<plugin>
							<groupId>org.apache.maven.plugins</groupId>
							<artifactId>maven-compiler-plugin</artifactId>
							<version>3.13.0</version>
						</plugin>
					</plugins>
				</build>
				"""));

		applyUpdate(pom, "org.apache.maven.plugins", "maven-compiler-plugin", "3.13.0", DeclarationSource.plugin(),
				VersionSource.declared("3.13.0"), "3.14.0");

		assertThat(pom.getText()).contains("<version>3.14.0</version>").doesNotContain("<version>3.13.0</version>");
	}

	@Test
	void profilePropertyVersionIsUpdated() {

		PsiFile pom = fixture.addFileToProject("pom.xml", Pom.of("""
				<groupId>com.example</groupId>
				<artifactId>demo</artifactId>
				<version>1.0.0</version>
				<profiles>
					<profile>
						<id>dev</id>
						<properties>
							<spring.version>6.1.0</spring.version>
						</properties>
						<dependencies>
							<dependency>
								<groupId>org.springframework</groupId>
								<artifactId>spring-core</artifactId>
								<version>${spring.version}</version>
							</dependency>
						</dependencies>
					</profile>
				</profiles>
				"""));

		ArtifactId id = ArtifactId.of("org.springframework", "spring-core");
		ArtifactVersion updateTo = ArtifactVersion.of("6.2.0");
		Dependency dep = new Dependency(id, ArtifactVersion.of("6.1.0"));
		dep.addDeclarationSource(DeclarationSource.profileDependency("dev"));
		dep.addVersionSource(VersionSource.profileProperty("dev", "spring.version"));

		DependencyUpdate update = new DependencyUpdate(id, updateTo, dep.getDeclarationSources(), dep.getVersionSources());

		new UpdatePom(fixture.getProject()).applyUpdates(pom.getVirtualFile(), List.of(update));
		PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();

		assertThat(pom.getText()).contains("<spring.version>6.2.0</spring.version>");
	}

	private void applyUpdate(PsiFile targetFile, String groupId, String artifactId, String fromVersion,
			DeclarationSource declarationSource, VersionSource versionSource, String toVersion) {

		ArtifactId id = ArtifactId.of(groupId, artifactId);
		ArtifactVersion current = ArtifactVersion.of(fromVersion);
		ArtifactVersion updateTo = ArtifactVersion.of(toVersion);

		Dependency dep = new Dependency(id, current);
		dep.addDeclarationSource(declarationSource);
		dep.addVersionSource(versionSource);

		DependencyUpdate update = new DependencyUpdate(id, updateTo, dep.getDeclarationSources(), dep.getVersionSources());

		new UpdatePom(fixture.getProject()).applyUpdates(targetFile.getVirtualFile(), List.of(update));
		PsiDocumentManager.getInstance(fixture.getProject()).commitAllDocuments();
	}

}
