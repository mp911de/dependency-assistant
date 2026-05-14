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
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for {@link MavenWrapperParser}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenWrapperParserTests {

	private static final ArtifactId APACHE_MAVEN = ArtifactId.of("org.apache.maven", "apache-maven");

	// -------------------------------------------------------------------------
	// Distribution URL
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void distributionUrlIsDiscovered(PsiFile file) {

		DependencyCollector collector = analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.apache.maven", "apache-maven")
				.hasVersion("3.9.6")
				.hasDeclaration(DeclarationSource.dependency());
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz
			""")
	void distributionUrlWithTarGzSuffixIsDiscovered(PsiFile file) {

		List<WrapperEntry> entries = new MavenWrapperParser().parse(file);

		assertThat(entries).singleElement().satisfies(entry -> {
			assertThat(entry.property()).isEqualTo(WrapperProperty.DISTRIBUTION);
			assertThat(entry.property().artifactId()).isEqualTo(APACHE_MAVEN);
			assertThat(entry.pathVersion()).isEqualTo("3.9.6");
		});
	}

	// -------------------------------------------------------------------------
	// Wrapper URL
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
			""")
	void wrapperUrlIsDiscovered(PsiFile file) {

		DependencyCollector collector = analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.apache.maven.wrapper", "maven-wrapper")
				.hasVersion("3.3.2")
				.hasDeclaration(DeclarationSource.dependency());
	}

	// -------------------------------------------------------------------------
	// Combined declarations
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
			""")
	void distributionAndWrapperUrlsAreBothDiscovered(PsiFile file) {

		DependencyCollector collector = analyze(file);

		assertThat(collector).hasUsageCount(2);
		assertThat(collector).hasDependencyUsage("apache-maven").hasVersion("3.9.6");
		assertThat(collector).hasDependencyUsage("maven-wrapper").hasVersion("3.3.2");
	}

	// -------------------------------------------------------------------------
	// Repository
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void mavenCentralUrlResolvesToMavenCentralRepository(PsiFile file) {

		List<WrapperEntry> entries = new MavenWrapperParser().parse(file);

		assertThat(entries).singleElement()
				.satisfies(entry -> assertThat(entry.repository()).isEqualTo(RemoteRepository.mavenCentral()));
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://nexus.example.com/repository/maven-public/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void customHostProducesDedicatedRepository(PsiFile file) {

		List<WrapperEntry> entries = new MavenWrapperParser().parse(file);

		assertThat(entries).singleElement().satisfies(entry -> {
			assertThat(entry.repository()).isNotEqualTo(RemoteRepository.mavenCentral());
			assertThat(entry.repository().url()).startsWith("https://nexus.example.com/");
		});
	}

	// -------------------------------------------------------------------------
	// Rejected inputs
	// -------------------------------------------------------------------------

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.5-bin.zip
			""")
	void mismatchedVersionsInPathAndFileNameAreIgnored(PsiFile file) {

		DependencyCollector collector = analyze(file);

		assertThat(collector).isEmpty();
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/\\
			  apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void silentlyIgnoresLineContinuationInDistributionUrl(PsiFile file) {

		List<WrapperEntry> entries = new MavenWrapperParser().parse(file);

		assertThat(entries).isEmpty();
	}

	private static DependencyCollector analyze(PsiFile file) {

		DependencyCollector collector = new DependencyCollector();
		new MavenWrapperParser(collector).parse((PropertiesFile) file);
		return collector;
	}

}
