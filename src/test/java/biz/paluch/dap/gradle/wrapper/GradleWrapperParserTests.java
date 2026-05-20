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

package biz.paluch.dap.gradle.wrapper;

import java.util.List;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.gradle.GradleDistributionReleaseSource;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for {@link GradleWrapperParser}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class GradleWrapperParserTests {

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			distributionSha256Sum=old
			""")
	void distributionUrlIsDiscovered(PsiFile file) {

		DependencyCollector collector = analyze(file);

		assertThat(collector)
				.hasDependencyUsage("org.gradle", "gradle")
				.hasVersion("8.14.3")
				.hasDeclaration(DeclarationSource.dependency());
		assertThat(collector.getReleaseSources()).contains(GradleDistributionReleaseSource.INSTANCE);
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://mirror.example.com/distributions/gradle-8.14.3-all.zip
			""")
	void allDistributionFlavorIsParsed(PsiFile file) {

		List<GradleWrapperEntry> entries = GradleWrapperParser.parse((PropertiesFile) file);

		assertThat(entries).singleElement().satisfies(entry -> {
			assertThat(entry.versionText()).isEqualTo("8.14.3");
			assertThat(entry.flavor()).isEqualTo("all");
		});
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			networkTimeout=10000
			distributionSha256Sum=old
			""")
	void ignoresNonVersionBearingProperties(PsiFile file) {
		assertThat(analyze(file)).isEmpty();
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.14.3-bin.tar.gz
			""")
	void ignoresNonZipDistribution(PsiFile file) {
		assertThat(analyze(file)).isEmpty();
	}

	private static DependencyCollector analyze(PsiFile file) {

		DependencyCollector collector = new DependencyCollector();
		new GradleWrapperParser(collector).collect((PropertiesFile) file);
		return collector;
	}

}
