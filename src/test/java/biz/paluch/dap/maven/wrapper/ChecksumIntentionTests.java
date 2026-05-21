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

package biz.paluch.dap.maven.wrapper;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for {@link ChecksumIntention}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class ChecksumIntentionTests {

	private static final String SHA = "82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9";

	private @TestFixture CodeInsightTestFixture fixture;

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			# <caret>comment
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void distributionIntentionIsAvailableFileWide(PsiFile file) {

		ChecksumIntention action = new ChecksumIntention.Distribution(checksum());

		assertThat(action.isAvailable(fixture.getProject(), fixture.getEditor(), file)).isTrue();
		assertThat(action.getText()).isEqualTo("Compute 'distributionUrl' SHA-256 checksum");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			distributionSha256Sum=
			""")
	void distributionIntentionIsNotAvailableWhenShaPropertyExists(PsiFile file) {

		ChecksumIntention action = new ChecksumIntention.Distribution(checksum());

		assertThat(action.isAvailable(fixture.getProject(), fixture.getEditor(), file)).isFalse();
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void distributionIntentionIsNotAvailableWhenProjectIsUntrusted(PsiFile file) {

		Project project = fixture.getProject();
		ChecksumIntention action = new ChecksumIntention.Distribution(checksum());

		TrustedProjects.setProjectTrusted(project, false);
		try {
			assertThat(action.isAvailable(project, fixture.getEditor(), file)).isFalse();
		} finally {
			TrustedProjects.setProjectTrusted(project, true);
		}
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/${prefix}/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void distributionIntentionIsNotAvailableWhenUrlContainsInterpolation(PsiFile file) {

		ChecksumIntention action = new ChecksumIntention.Distribution(checksum());

		assertThat(action.isAvailable(fixture.getProject(), fixture.getEditor(), file)).isFalse();
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			<caret>
			validateDistributionUrl=true
			""")
	void blankCaretLineIsReplaced(PsiFile file) {

		invoke(new ChecksumIntention.Distribution(checksum()), file);

		assertThat(file).containsText(
				"""
						distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
						distributionSha256Sum=82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9
						validateDistributionUrl=true
						""");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			validate<caret>DistributionUrl=true
			""")
	void nonBlankCaretLineInsertsBelow(PsiFile file) {

		invoke(new ChecksumIntention.Distribution(checksum()), file);

		assertThat(file).containsText(
				"""
						distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
						validateDistributionUrl=true
						distributionSha256Sum=82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9
						""");
	}

	private void invoke(ChecksumIntention action, PsiFile file) {
		action.invoke(fixture.getProject(), fixture.getEditor(), file);
	}

	private MavenWrapperChecksumQuickFix.ChecksumComputer checksum() {
		return (project, url) -> SHA;
	}

}
