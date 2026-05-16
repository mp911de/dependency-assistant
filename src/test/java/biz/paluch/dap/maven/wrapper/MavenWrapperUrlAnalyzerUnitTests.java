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

import biz.paluch.dap.extension.StringTest;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.CredentialsInUrl;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.ImproperGroupId;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.InconsistentArtifact;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.InconsistentVersion;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.InvalidUrl;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.MalformedFileName;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.UnknownArtifact;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MavenWrapperUrlAnalyzer}.
 *
 * @author Mark Paluch
 */
class MavenWrapperUrlAnalyzerUnitTests {

	private static final String CANONICAL_DISTRIBUTION = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

	private static final String CANONICAL_WRAPPER = "https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.9.6/maven-wrapper-3.9.6.jar";

	@StringTest("https://user:pass@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void emitsCredentialsInUrl(String url) {
		assertThat(analyzeDistribution(url)).containsExactly(new CredentialsInUrl());
	}

	@StringTest("https://example.com/not-a-maven-url")
	void emitsInvalidUrl(String url) {
		assertThat(analyzeDistribution(url)).containsExactly(new InvalidUrl());
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.7-bin.zip")
	void emitsInconsistentVersion(String url) {

		assertThat(analyzeDistribution(url))
				.containsExactly(new InconsistentVersion("3.9.6", "3.9.7"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-renamed-3.9.6-bin.zip")
	void emitsInconsistentArtifactWithUnknownArtifactCoEmission(String url) {

		assertThat(analyzeDistribution(url)).containsExactlyInAnyOrder(
				new InconsistentArtifact("apache-maven", "apache-maven-renamed"),
				new UnknownArtifact("apache-maven-renamed"),
				new MalformedFileName("apache-maven-renamed-3.9.6-bin.zip", "3.9.6"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-renamed-3.9.6-bin.zip")
	void emitsUnknownArtifactOnNonCanonicalTokenWhenPathIsCanonical(String url) {

		assertThat(analyzeDistribution(url))
				.contains(new UnknownArtifact("apache-maven-renamed"))
				.doesNotContain(new UnknownArtifact("apache-maven"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/foo/3.9.6/apache-maven-3.9.6-bin.zip")
	void emitsUnknownArtifactOnNonCanonicalTokenWhenFileIsCanonical(String url) {

		assertThat(analyzeDistribution(url)).contains(new UnknownArtifact("foo"))
				.contains(new InconsistentArtifact("foo", "apache-maven"))
				.doesNotContain(new UnknownArtifact("apache-maven"));
	}

	@StringTest("https://repo1.maven.org/maven2/com/example/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void emitsImproperGroupId(String url) {

		assertThat(analyzeDistribution(url))
				.containsExactly(new ImproperGroupId("com/example/maven"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/foo/3.9.6/foo-3.9.6-bin.tar.gz")
	void emitsUnknownArtifactWithoutInconsistentArtifactWhenPathAndFileAgree(String url) {

		assertThat(analyzeDistribution(url)).containsExactlyInAnyOrder(
				new UnknownArtifact("foo"),
				new MalformedFileName("foo-3.9.6-bin.tar.gz", "3.9.6"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.weird")
	void emitsMalformedFileName(String url) {

		assertThat(analyzeDistribution(url))
				.containsExactly(new MalformedFileName("apache-maven-3.9.6-bin.weird", "3.9.6"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven--bin.zip")
	void malformedFileNameSlicesActualFileNameFromUrl(String url) {

		assertThat(analyzeDistribution(url))
				.contains(new MalformedFileName("apache-maven--bin.zip", "3.9.6"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-renamed-3.9.7-bin.zip")
	void emitsInconsistentVersionTogetherWithInconsistentArtifact(String url) {

		assertThat(analyzeDistribution(url)).containsExactlyInAnyOrder(
				new InconsistentVersion("3.9.6", "3.9.7"),
				new InconsistentArtifact("apache-maven", "apache-maven-renamed"),
				new UnknownArtifact("apache-maven-renamed"));
	}

	@StringTest("https://repo1.maven.org/maven2/com/example/maven/foo/3.9.6/foo-3.9.6-bin.zip")
	void emitsImproperGroupIdTogetherWithUnknownArtifact(String url) {

		assertThat(analyzeDistribution(url)).containsExactlyInAnyOrder(
				new ImproperGroupId("com/example/maven"),
				new UnknownArtifact("foo"),
				new MalformedFileName("foo-3.9.6-bin.zip", "3.9.6"));
	}

	@StringTest("https://user:pass@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void emitsCredentialsOnOtherwiseValidUrl(String url) {
		assertThat(analyzeDistribution(url))
				.containsExactlyInAnyOrder(new CredentialsInUrl());
	}

	@StringTest("https://user:pass@example.com/not-a-maven-url")
	void emitsCredentialsInUrlAndInvalidUrl(String url) {
		assertThat(analyzeDistribution(url))
				.containsExactlyInAnyOrder(new CredentialsInUrl(), new InvalidUrl());
	}

	@StringTest("https://user:p@ss@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void emitsCredentialsInUrlForUnencodedAtInPassword(String url) {
		assertThat(analyzeDistribution(url)).contains(new CredentialsInUrl());
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.7-bin.weird")
	void suppressesMalformedFileNameUnderInconsistentVersion(String url) {
		assertThat(analyzeDistribution(url))
				.containsExactly(new InconsistentVersion("3.9.6", "3.9.7"));
	}

	@StringTest("https://nexus.corp.example.com/repository/maven-public/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void mirrorPrefixWithCanonicalGroupPathTailDoesNotEmitImproperGroupId(String url) {
		assertThat(analyzeDistribution(url)).isEmpty();
	}

	@StringTest("https://nexus.corp.example.com/repository/maven-public/com/example/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void mirrorPrefixWithWrongGroupPathTailEmitsImproperGroupIdWithLastSegments(String url) {
		assertThat(analyzeDistribution(url))
				.containsExactly(new ImproperGroupId("com/example/maven"));
	}

	@StringTest("https://user:${PASS}@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void emitsCredentialsInUrlWhenPlaceholderIsInsidePassword(String url) {
		assertThat(analyzeDistribution(url)).contains(new CredentialsInUrl());
	}

	@StringTest("https://repo1.maven.org/maven2/${prefix}/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void skipsWholeValueClassificationWhenPlaceholderInPath(String url) {
		assertThat(analyzeDistribution(url)).isEmpty();
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip")
	void skipsWholeValueClassificationWhenCompletionPlaceholderInRawText(String decoded) {

		String raw = decoded + MavenWrapperUtils.COMPLETION_PLACEHOLDER;

		assertThat(MavenWrapperUrlAnalyzer.analyze(WrapperProperty.DISTRIBUTION, decoded, raw)).isEmpty();
	}

	@StringTest("file:///tmp/apache-maven-3.9.6-bin.zip")
	void fileUrlEmitsInvalidUrlButNoInsecureUrl(String url) {
		assertThat(analyzeDistribution(url)).containsExactly(new InvalidUrl());
	}

	@StringTest("ftp://example.com/apache-maven-3.9.6-bin.zip")
	void ftpUrlEmitsInvalidUrlButNoInsecureUrl(String url) {
		assertThat(analyzeDistribution(url)).containsExactly(new InvalidUrl());
	}

	@StringTest("example.com/apache-maven-3.9.6-bin.zip")
	void noSchemeUrlEmitsInvalidUrlButNoInsecureUrl(String url) {
		assertThat(analyzeDistribution(url)).containsExactly(new InvalidUrl());
	}

	@StringTest(CANONICAL_DISTRIBUTION)
	void canonicalDistributionUrlProducesNoProblems(String url) {
		assertThat(analyzeDistribution(url)).isEmpty();
	}

	@StringTest(CANONICAL_WRAPPER)
	void canonicalWrapperUrlProducesNoProblems(String url) {
		assertThat(analyzeWrapper(url)).isEmpty();
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/wrong/wrapper/maven-wrapper/3.9.6/maven-wrapper-3.9.6.jar")
	void emitsImproperGroupIdForWrapper(String url) {

		assertThat(analyzeWrapper(url))
				.containsExactly(new ImproperGroupId("org/apache/wrong/wrapper"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.9.6/maven-wrapper-3.9.6.zip")
	void emitsMalformedFileNameForWrapperWhenExtensionIsZip(String url) {

		assertThat(analyzeWrapper(url))
				.containsExactly(new MalformedFileName("maven-wrapper-3.9.6.zip", "3.9.6"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.9.6/maven-wrapper-3.9.7.jar")
	void emitsInconsistentVersionForWrapper(String url) {

		assertThat(analyzeWrapper(url))
				.containsExactly(new InconsistentVersion("3.9.6", "3.9.7"));
	}

	@StringTest("https://repo1.maven.org/maven2/org/apache/maven/wrapper/foo/3.9.6/foo-3.9.6.jar")
	void emitsUnknownArtifactForWrapper(String url) {

		assertThat(analyzeWrapper(url)).containsExactlyInAnyOrder(
				new UnknownArtifact("foo"),
				new MalformedFileName("foo-3.9.6.jar", "3.9.6"));
	}

	private static List<MavenWrapperUrlProblem> analyzeWrapper(String url) {
		return analyze(WrapperProperty.WRAPPER, url);
	}

	private static List<MavenWrapperUrlProblem> analyzeDistribution(String url) {
		return analyze(WrapperProperty.DISTRIBUTION, url);
	}

	private static List<MavenWrapperUrlProblem> analyze(WrapperProperty kind, String url) {
		return MavenWrapperUrlAnalyzer.analyze(kind, url, url);
	}

}
