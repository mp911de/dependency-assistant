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
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.assistant.BuildActionDelegate;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for {@link UpdateMavenWrapperProperties}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class UpdateMavenWrapperPropertiesTests {

	private @TestFixture CodeInsightTestFixture fixture;

	private static final ArtifactId MAVEN = ArtifactId.of("org.apache.maven", "apache-maven");

	private static final ArtifactId WRAPPER = ArtifactId.of("org.apache.maven.wrapper", "maven-wrapper");

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			distributionSha256Sum=ab\\
			                      c123
			wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
			""")
	void shouldUpdateDistribution(PsiFile file) {

		applyUpdate(file, MAVEN, "3.9.6", "3.9.9");

		assertThat(file).containsText("apache-maven/3.9.9/apache-maven-3.9.9-bin.zip");
		assertThat(file).containsText("# distributionSha256Sum=ab\\");
		assertThat(file).containsText("#                       c123");
		assertThat(file).doesNotContainText("3.9.6");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			d\\u0069stribution\\u0055rl=\\u0068ttps\\://repo1\\u002emaven\\u002eorg/maven2/org/apache/maven/apache-maven/3\\u002e9\\u002e6/apache-maven-3\\u002e9\\u002e6-bin\\u002ezip
			""")
	void shouldEscapedDistribution(PsiFile file) {

		applyUpdate(file, MAVEN, "3.9.6", "3.9.9");

		assertThat(file).containsText("d\\u0069stribution\\u0055rl=");
		assertThat(file).containsText("/3.9.9/");
		assertThat(file).containsText("apache-maven-3.9.9-bin\\u002ezip");
		assertThat(file).doesNotContainText("3.9.6");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			d\\u0069stribution\\u0055rl=\\u0068ttps\\://repo1\\u002emaven\\u002eorg/maven2/org/apache/maven/apache-maven/3\\u002e9\\u002e6/apache-maven-3\\u002e9\\u002e6-bin\\u002ezip
			""")
	void detectsUpdateRanges(PsiFile file) {

		PropertyImpl property = PsiTreeUtil.findChildOfType(file, PropertyImpl.class);
		List<TextRange> ranges = MavenWrapperUtils.getVersionRanges(property);
		String text = file.getText();

		assertThat(ranges).hasSize(2);

		for (TextRange range : ranges) {
			assertThat(text.substring(range.getStartOffset(), range.getEndOffset())).isEqualTo("3\\u002e9\\u002e6");
		}
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			foo=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9./apache-maven-3.9.6-bin.zip
			""")
	void detectsUpdateRangesFromDifferentVersions(PsiFile file) {

		PropertyImpl property = PsiTreeUtil.findChildOfType(file, PropertyImpl.class);
		List<TextRange> ranges = MavenWrapperUtils.getVersionRanges(property);
		String text = file.getText();

		assertThat(ranges).hasSize(2);

		TextRange range1 = ranges.get(0);
		TextRange range2 = ranges.get(1);

		assertThat(text.substring(range1.getStartOffset(), range1.getEndOffset())).isEqualTo("3.9.");
		assertThat(text.substring(range2.getStartOffset(), range2.getEndOffset())).isEqualTo("3.9.6");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			foo=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
			""")
	void detectsUpdateRangesFromWrapper(PsiFile file) {

		PropertyImpl property = PsiTreeUtil.findChildOfType(file, PropertyImpl.class);
		List<TextRange> ranges = MavenWrapperUtils.getVersionRanges(property);
		String text = file.getText();

		assertThat(ranges).hasSize(2);

		TextRange range1 = ranges.get(0);
		TextRange range2 = ranges.get(1);

		assertThat(text.substring(range1.getStartOffset(), range1.getEndOffset())).isEqualTo("3.3.2");
		assertThat(text.substring(range2.getStartOffset(), range2.getEndOffset())).isEqualTo("3.3.2");
	}

	@Test
	@ProjectFile(name = ".mvn/wrapper/maven-wrapper.properties", content = """
			# Maven Wrapper configuration
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			distributionSha256Sum=abc123
			wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.2/maven-wrapper-3.3.2.jar
			wrapperSha256Sum=def456
			""")
	void shouldUpdateWrapper(PsiFile file) {

		applyUpdate(file, WRAPPER, "3.3.2", "3.3.3");

		assertThat(file).containsText("maven-wrapper/3.3.3/maven-wrapper-3.3.3.jar");
		assertThat(file).containsText("# Maven Wrapper configuration");
		assertThat(file).containsText("# wrapperSha256Sum=def456");
	}

	private void applyUpdate(PsiFile targetFile, ArtifactId artifactId, String fromVersion, String toVersion) {
		new BuildActionDelegate(targetFile.getProject(),
				(file, updates) -> new UpdateMavenWrapperProperties().applyUpdates(file, updates),
				targetFile.getVirtualFile()).updateBuildFile(List.of(update(artifactId, toVersion)));
	}

	private DependencyUpdate update(ArtifactId artifactId, String toVersion) {

		ArtifactVersion target = ArtifactVersion.of(toVersion);
		return DependencyUpdate.create(artifactId, target);
	}

}
