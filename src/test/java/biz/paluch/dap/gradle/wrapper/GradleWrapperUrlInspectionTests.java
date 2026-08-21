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

package biz.paluch.dap.gradle.wrapper;

import java.util.List;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.ProjectFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.state.StateService;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Highlighting and quick-fix integration tests for
 * {@link GradleWrapperUrlInspection}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class GradleWrapperUrlInspectionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		fixture.enableInspections(GradleWrapperUrlInspection.class);
		GradleWrapperFixtures.setup(fixture.getProject());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https://<warning descr="Wrapper URL contains plaintext credentials">alice:secret@</warning>services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void highlightsCredentialsInUrl(PsiFile file) {

		cacheGradleRelease("8.14.3", "sha-8.14.3");

		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=<warning descr="Wrapper URL does not match the expected Gradle distribution shape">not a url at all</warning>
			""")
	void highlightsInvalidUrl(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/<warning descr="Wrapper URL artifactId 'wrapper' is not the canonical Gradle distribution artifact">wrapper</warning>-8.14.3-bin.zip
			""")
	void highlightsUnknownArtifact(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=<weak_warning descr="distributionUrl has no SHA-256 checksum">https://services.gradle.org/distributions/<warning descr="Wrapper URL file name 'gradle-8.14.3.zip' does not follow the canonical pattern">gradle-8.14.3.zip</warning></weak_warning>
			""")
	void highlightsMalformedFileName(PsiFile file) {
		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=<weak_warning descr="distributionUrl has no SHA-256 checksum">https://services.gradle.org/distributions/gradle-8.14.3-bin.zip</weak_warning>
			""")
	void highlightsMissingDistributionSha(PsiFile file) {

		cacheGradleRelease("8.14.3", "sha-8.14.3");

		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			distributionSha256Sum=
			""")
	void skipsMissingDistributionShaWhenShaPropertyExists(PsiFile file) {

		cacheGradleRelease("8.14.3", "sha-8.14.3");

		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/gradle-8.14.3<caret>-bin.zip
			""")
	void missingDistributionShaOffersComputeFix() {
		assertThat(intentionLabels()).contains("Add SHA-256 checksum");
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/gradle-8.___IntellijIdeaRulezzz___-bin.zip
			""")
	void skipsCompletionPlaceholder(PsiFile file) {

		emptyCache();

		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "gradle/wrapper/gradle-wrapper.properties", content = """
			distributionUrl=ftp://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void skipsMissingDistributionShaForUnsupportedSchemes(PsiFile file) {

		cacheGradleRelease("8.14.3", "sha-8.14.3");

		fixture.testHighlighting(true, false, true, file.getVirtualFile());
	}

	@Test
	@ProjectFile(name = "application.properties", content = """
			distributionUrl=http://services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void ignoresNonWrapperFiles(PsiFile file) {
		fixture.testHighlighting(true, false, false, file.getVirtualFile());
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https://alice<caret>:secret@services.gradle.org/distributions/gradle-8.14.3-bin.zip
			""")
	void stripCredentialsFixRemovesUserInfo(PsiFile file) {

		fixture.launchAction(fixture.findSingleIntention("Remove credentials"));

		assertThat(file)
				.containsText("https://services.gradle.org/distributions/gradle-8.14.3-bin.zip")
				.doesNotContainText("alice", "secret");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/wrap<caret>per-8.14.3-bin.zip
			""")
	void replaceUnknownArtifactFixSetsCanonicalFileName(PsiFile file) {

		fixture.launchAction(fixture.findSingleIntention("Set file to 'gradle-8.14.3-bin.zip'"));

		assertThat(file).containsText("https://services.gradle.org/distributions/gradle-8.14.3-bin.zip");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https://services.gradle.org/distributions/gradle-8.14.3<caret>.zip
			""")
	void replaceMalformedFileNameFixSetsCanonicalFileName(PsiFile file) {

		fixture.launchAction(fixture.findSingleIntention("Set file to 'gradle-8.14.3-bin.zip'"));

		assertThat(file).containsText("https://services.gradle.org/distributions/gradle-8.14.3-bin.zip");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=not a <caret>url at all
			""")
	void invalidUrlOffersDefaultUrlFixWhenCacheIsEmpty(PsiFile file) {

		fixture.launchAction(fixture.findSingleIntention("Fix download URL and use version '9.5.1'"));

		assertThat(file).containsText("https://services.gradle.org/distributions/gradle-9.5.1-bin.zip");
	}

	private List<String> intentionLabels() {
		return fixture.getAvailableIntentions().stream().map(IntentionAction::getText).toList();
	}

	/**
	 * Replace the cache so the given version is the only, and therefore latest,
	 * Gradle release. Keeps the checksum available while suppressing upgrade
	 * suggestions that would add unrelated highlighting.
	 */
	private void cacheGradleRelease(String version, String sha) {

		CachedArtifact gradle = new CachedArtifact("org.gradle", "gradle");
		gradle.addRelease(new CachedRelease(version, "2026-01-23", sha));
		Cache cache = new Cache();
		cache.addArtifacts(List.of(gradle));
		StateService.getInstance(fixture.getProject()).setCache(cache);
	}

	private void emptyCache() {
		StateService.getInstance(fixture.getProject()).setCache(new Cache());
	}

}
