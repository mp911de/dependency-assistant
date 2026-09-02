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

package biz.paluch.dap.maven.wrapper;

import java.util.concurrent.CompletableFuture;

import biz.paluch.dap.assistant.util.ChecksumDownloader;
import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.fixtures.TrustedFixture;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.ServiceContainerUtil;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * PSI-level tests for {@link MavenWrapperChecksumIntention}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class ChecksumIntentionTests {

	private String SHA = "82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9";

	private @TestFixture CodeInsightTestFixture fixture;

	private MavenWrapperChecksumIntention action = new MavenWrapperChecksumIntention.Distribution();

	@BeforeEach
	void setUp() {
		useDownloader(new ChecksumDownloader() {

			@Override
			public CompletableFuture<String> computeSha(Project project, String url) {
				return CompletableFuture.completedFuture(SHA);
			}

		});
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			# <caret>comment
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void distributionIntentionIsAvailableFileWide(PsiFile file) {

		assertThat(action.isAvailable(fixture.getProject(), fixture.getEditor(), file)).isTrue();
		assertThat(action.getText()).isEqualTo("Compute 'distributionUrl' SHA-256 checksum");
		assertThat(action.startInWriteAction()).isFalse();
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			distributionSha256Sum=
			""")
	void distributionIntentionIsNotAvailableWhenShaPropertyExists(PsiFile file) {
		assertThat(action.isAvailable(fixture.getProject(), fixture.getEditor(), file)).isFalse();
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void distributionIntentionIsNotAvailableWhenProjectIsUntrusted(PsiFile file) {

		TrustedFixture.of(fixture.getProject()).runUntrusted(() -> {
			assertThat(action.isAvailable(fixture.getProject(), fixture.getEditor(), file)).isFalse();
		});
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/${prefix}/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void distributionIntentionIsNotAvailableWhenUrlContainsInterpolation(PsiFile file) {
		assertThat(action.isAvailable(fixture.getProject(), fixture.getEditor(), file)).isFalse();
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			<caret>
			validateDistributionUrl=true
			""")
	void blankCaretLineIsReplaced(PsiFile file) {

		invoke(file);

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

		invoke(file);

		assertThat(file).containsText(
				"""
						distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
						validateDistributionUrl=true
						distributionSha256Sum=82e35a63ceba37e9646434c5dd412ea577147f1e4a41ccde1614253187e3dbf9
						""");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			<caret>
			""")
	void discardsChecksumWhenUrlChangesDuringComputation(PsiFile file) {

		useDownloader(new ChecksumDownloader() {

			@Override
			public CompletableFuture<String> computeSha(Project project, String url) {

				Document document = fixture.getEditor().getDocument();
				WriteCommandAction.runWriteCommandAction(project, () -> {
					String current = document.getText();
					document.replaceString(current.indexOf("3.9.6"), current.indexOf("3.9.6") + "3.9.6".length(),
							"3.9.7");
					PsiDocumentManager.getInstance(project).commitDocument(document);
				});
				return CompletableFuture.completedFuture(SHA);
			}

		});

		invoke(file);

		assertThat(file).containsText("apache-maven/3.9.7")
				.doesNotContainText("distributionSha256Sum");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			<caret>
			""")
	void cancelledDownloadLeavesFileUnchanged(PsiFile file) {

		useDownloader(new ChecksumDownloader() {

			@Override
			public CompletableFuture<String> computeSha(Project project, String url) {
				CompletableFuture<String> cancelled = new CompletableFuture<>();
				cancelled.cancel(false);
				return cancelled;
			}

		});

		invoke(file);

		assertThat(file).doesNotContainText("distributionSha256Sum");
	}

	private void invoke(PsiFile file) {
		action.invoke(fixture.getProject(), fixture.getEditor(), file);
	}

	private void useDownloader(ChecksumDownloader downloader) {
		ServiceContainerUtil.registerOrReplaceServiceInstance(ApplicationManager.getApplication(),
				ChecksumDownloader.class, downloader, fixture.getTestRootDisposable());
	}

}
