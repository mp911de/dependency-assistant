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

import java.util.List;
import java.util.stream.Stream;

import biz.paluch.dap.extension.CodeInsightFixtureTests;
import biz.paluch.dap.extension.EditorFile;
import biz.paluch.dap.extension.TestFixture;
import biz.paluch.dap.state.Cache;
import biz.paluch.dap.state.CachedArtifact;
import biz.paluch.dap.state.CachedRelease;
import biz.paluch.dap.state.StateService;
import com.intellij.codeInsight.lookup.Lookup;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static biz.paluch.dap.assertions.Assertions.*;

/**
 * Wrapper properties completion PSI-level tests for
 * {@link MavenWrapperCompletionContributor}.
 *
 * @author Mark Paluch
 */
@CodeInsightFixtureTests
class MavenWrapperCompletionTests {

	private @TestFixture CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() {
		MavenWrapperFixtures.setup(fixture.getProject());
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=<caret>
			""")
	void completesEmptyDistributionUrlWithDefaultUrlFromCache(PsiFile file) {

		fixture.completeBasic();
		assertThat(fixture).completionSuggests(
				"distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file)
				.containsText(
						"distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip")
				.caretAfter("apache-maven-3.10.0-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			wrapperUrl=<caret>
			""")
	void completesEmptyWrapperUrlWithDefaultUrlFromCache(PsiFile file) {

		fixture.completeBasic();
		assertThat(fixture).completionSuggests(
				"wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.3/maven-wrapper-3.3.3.jar");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file)
				.containsText(
						"wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.3/maven-wrapper-3.3.3.jar")
				.caretAfter("maven-wrapper-3.3.3.jar");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=<caret>
			""")
	void completesEmptyDistributionUrlWithFallbackWhenCacheEmpty() {

		StateService.getInstance(fixture.getProject()).setCache(new Cache());

		fixture.completeBasic();
		assertThat(fixture).completionSuggests(
				"distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.15/apache-maven-3.9.15-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			wrapperUrl=<caret>
			""")
	void completesEmptyWrapperUrlWithFallbackWhenCacheEmpty(PsiFile file) {

		StateService.getInstance(fixture.getProject()).setCache(new Cache());

		fixture.completeBasic();
		assertThat(fixture).completionSuggests(
				"wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.4/maven-wrapper-3.3.4.jar");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file).containsText("wrapperUrl=https://");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=<caret>
			""")
	void completesWithServiceReleaseWithPreviewVersionAvailability(PsiFile file) {

		CachedArtifact artifact = new CachedArtifact("org.apache.maven", "apache-maven");
		artifact.getReleases().add(new CachedRelease("4.0.0-rc-1", "2026-03-01"));
		Cache cache = new Cache();
		cache.addArtifacts(List.of(artifact));
		StateService.getInstance(fixture.getProject()).setCache(cache);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests(
				"distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.15/apache-maven-3.9.15-bin.zip");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file).containsText("distributionUrl=https://");
	}

	@ParameterizedTest(name = "[{index}] autoPopup({1}) after ''{0}'' -> {2}")
	@MethodSource("autoPopupScenarios")
	void autoPopupTriggersOnRecognisedPrefixes(String content, char typed, boolean expected) {

		fixture.configureByText("maven-wrapper.properties", content);

		assertThat(invokeAutoPopup(typed)).isEqualTo(expected);
	}

	static Stream<Arguments> autoPopupScenarios() {
		return Stream.of(
				Arguments.of("distributionUrl<caret>\n", '=', true),
				Arguments.of("foo<caret>\n", '=', false),
				Arguments.of("<caret>\n", 'd', true),
				Arguments.of("<caret>\n", 'w', true),
				Arguments.of("x<caret>\n", 'x', false),
				Arguments.of("D<caret>\n", 'D', false));
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.<caret>/apache-maven-3.9.6-bin.zip
			""")
	void completesInsideFirstVersion(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("3.9.9");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
		assertThat(file).caretBetween("maven/3.9.9", "/apache-maven");
	}

	@ParameterizedTest
	@MethodSource("versionStartCompletions")
	void completesVersionToLatest(String content, char finishChar) {

		PsiFile file = fixture.configureByText("maven-wrapper.properties", content);
		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("3.10.0");

		fixture.finishLookup(finishChar);
		assertThat(file).containsText("maven/3.10.0/apache");
		assertThat(file).containsText("maven-3.10.0-bin");
	}

	static Stream<Arguments> versionStartCompletions() {

		String beforePathVersion = "distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/<caret>/apache-maven-3.9.6-bin.zip\n";
		String beforeFileVersionWithDigits = "distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-<caret>3.9.6-bin.zip\n";
		String beforeEmptyFileVersion = "distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-<caret>-bin.zip\n";

		return Stream.of(
				Arguments.of(beforeFileVersionWithDigits, Lookup.NORMAL_SELECT_CHAR),
				Arguments.of(beforeFileVersionWithDigits, Lookup.REPLACE_SELECT_CHAR),
				Arguments.of(beforePathVersion, Lookup.NORMAL_SELECT_CHAR),
				Arguments.of(beforePathVersion, Lookup.COMPLETE_STATEMENT_SELECT_CHAR),
				Arguments.of(beforePathVersion, Lookup.REPLACE_SELECT_CHAR),
				Arguments.of(beforeEmptyFileVersion, Lookup.REPLACE_SELECT_CHAR),
				Arguments.of(beforeEmptyFileVersion, Lookup.COMPLETE_STATEMENT_SELECT_CHAR));
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.<caret>-bin.zip
			""")
	void completesInsideSecondVersion(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("3.9.9").completionExcludes("3.10.0");

		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);
		assertThat(file).containsText("maven/3.9.9/apache");
		assertThat(file).containsText("maven-3.9.9-bin");
		assertThat(file).caretBetween("maven-3.9.9", "-bin.zip");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			distributionUrl=https\\://services.gradle.org/distributions/gradle-8.5<caret>-bin.zip
			""")
	void doesNotOfferCompletionForGradleWrapper(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).isEmpty();
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-<caret>maven-3.9.6-bin.zip
			""")
	void doesNotOfferCompletionInsideArtifactId(PsiFile file) {

		MavenWrapperFixtures.analyze(file);

		fixture.completeBasic();
		assertThat(fixture.getLookupElementStrings()).isEmpty();
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			d<caret>
			""")
	void completesDistributionLine(PsiFile file) {

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file)
				.containsText(
						"distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip")
				.caretAfter("apache-maven-3.10.0-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			w<caret>
			""")
	void completesWrapperLine(PsiFile file) {

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file)
				.containsText(
						"wrapperUrl=https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.3.3/maven-wrapper-3.3.3.jar")
				.caretAfter("maven-wrapper-3.3.3.jar");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			dist<caret>
			""")
	void completesMidKey(PsiFile file) {

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file)
				.containsText(
						"distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.10.0/apache-maven-3.10.0-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			d<caret>=foo
			""")
	void replacesPropertyWhenLineHasExistingValue(PsiFile file) {

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file)
				.containsText("distributionUrl=https://repo1.maven.org")
				.doesNotContainText("=foo");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			d<caret>
			""")
	void completesLineWithFallbackWhenCacheEmpty(PsiFile file) {

		StateService.getInstance(fixture.getProject()).setCache(new Cache());

		fixture.completeBasic();
		fixture.finishLookup(Lookup.NORMAL_SELECT_CHAR);

		assertThat(file).containsText(
				"distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.15/apache-maven-3.9.15-bin.zip");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=d<caret>
			""")
	void doesNotOfferLineCompletionInsideValue() {

		fixture.completeBasic();
		assertThat(lookupElementsOrEmpty()).doesNotContain("distributionUrl", "wrapperUrl");
	}

	@Test
	@EditorFile(name = "gradle-wrapper.properties", content = """
			d<caret>
			""")
	void doesNotOfferLineCompletionInGradleWrapper() {

		fixture.completeBasic();
		assertThat(lookupElementsOrEmpty()).doesNotContain("distributionUrl", "wrapperUrl");
	}

	@Test
	@EditorFile(name = "maven-wrapper.properties", content = """
			distributionUrl=https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			d<caret>
			""")
	void offersLineCompletionEvenWhenDistributionUrlAlreadyExists() {

		fixture.completeBasic();
		assertThat(fixture).completionSuggests("distributionUrl");
	}

	private List<String> lookupElementsOrEmpty() {
		List<String> elements = fixture.getLookupElementStrings();
		return elements == null ? List.of() : elements;
	}

	private boolean invokeAutoPopup(char typeChar) {

		PsiElement position = fixture.getFile().findElementAt(fixture.getCaretOffset() - 1);
		MavenWrapperCompletionContributor contributor = new MavenWrapperCompletionContributor();
		return contributor.invokeAutoPopup(position, typeChar);
	}

}
