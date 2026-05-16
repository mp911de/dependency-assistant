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

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MavenWrapperUrlRewriter}.
 *
 * @author Mark Paluch
 */
class MavenWrapperUrlRewriterUnitTests {

	@Test
	void stripCredentialsRemovesUserAndPassword() {

		String url = "https://alice:secret@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.stripCredentials(url))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void stripCredentialsRemovesUserOnly() {

		String url = "https://alice@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.stripCredentials(url))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void stripCredentialsPreservesPort() {

		String url = "https://alice:secret@repo1.maven.org:8443/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.stripCredentials(url))
				.isEqualTo(
						"https://repo1.maven.org:8443/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void stripCredentialsLeavesUrlUnchangedWhenNoCredentials() {

		String url = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.stripCredentials(url)).isEqualTo(url);
	}

	@Test
	void forceHttpsUpgradesPlainHttp() {

		String url = "http://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.forceHttps(url))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void forceHttpsLeavesHttpsUnchanged() {

		String url = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.forceHttps(url)).isEqualTo(url);
	}

	@Test
	void forceHttpsLeavesSchemelessUnchanged() {

		String url = "repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.forceHttps(url)).isEqualTo(url);
	}

	@Test
	void forceHttpsUpgradesUppercaseScheme() {

		String url = "HTTP://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.forceHttps(url)).startsWith("https://")
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void forceHttpsUpgradesMixedCaseScheme() {

		String url = "Http://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.forceHttps(url)).startsWith("https://")
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.2.3", "1.2.3-SNAPSHOT"})
	void replaceVersionRewritesBothPathAndFileVersions(String toVersion) {

		String format = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/%1$s/apache-maven-%1$s-bin.zip";
		String url = format.formatted("4.5.6");

		assertThat(MavenWrapperUrlRewriter.replaceVersion(url, toVersion)).isEqualTo(format.formatted(toVersion));
	}

	@ParameterizedTest
	@ValueSource(strings = {"1.2.3", "1.2.3-SNAPSHOT"})
	void replaceVersionFromSnapshot(String toVersion) {

		String format = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/%1$s/apache-maven-%1$s-bin.tar.gz";
		String url = format.formatted("4.5.6-SNAPSHOT");

		assertThat(MavenWrapperUrlRewriter.replaceVersion(url, toVersion)).isEqualTo(format.formatted(toVersion));
	}

	@Test
	void replaceVersionForWrapperJar() {

		String format = "https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/%1$s/maven-wrapper-%1$s.jar";
		String url = format.formatted("3.2.0");

		assertThat(MavenWrapperUrlRewriter.replaceVersion(url, "3.3.1")).isEqualTo(format.formatted("3.3.1"));
	}

	@Test
	void replaceVersionReturnsInputWhenNoMatch() {

		String url = "https://example.com/no-coordinates-here";

		assertThat(MavenWrapperUrlRewriter.replaceVersion(url, "1.0.0")).isEqualTo(url);
	}

	@Test
	void replaceArtifactRewritesBothPathAndFileArtifact() {

		String url = "https://repo1.maven.org/maven2/org/apache/maven/wrong-name/3.9.6/wrong-name-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.replaceArtifact(url, "apache-maven"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void replaceArtifactForWrapperJar() {

		String url = "https://repo1.maven.org/maven2/org/apache/maven/wrapper/wrong/3.2.0/wrong-3.2.0.jar";

		assertThat(MavenWrapperUrlRewriter.replaceArtifact(url, "maven-wrapper"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar");
	}

	@Test
	void replaceGroupPathReplacesLastThreeSegmentsForDistribution() {

		String url = "https://repo1.maven.org/maven2/wrong/group/foo/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.replaceGroupPath(url, "org/apache/maven"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void replaceGroupPathPreservesMirrorPrefix() {

		String url = "https://my-mirror.example.com/maven2/wrong/group/foo/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

		assertThat(MavenWrapperUrlRewriter.replaceGroupPath(url, "org/apache/maven"))
				.isEqualTo(
						"https://my-mirror.example.com/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void replaceGroupPathOnlyReplacesTrailingSegmentsWhenGroupLikeTokensAppearEarlier() {

		String url = "https://nexus.example/repo/org/apache/maven/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz";

		assertThat(MavenWrapperUrlRewriter.replaceGroupPath(url, "org/apache/maven"))
				.isEqualTo(
						"https://nexus.example/repo/org/apache/maven/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz");
	}

	@Test
	void replaceGroupPathReplacesLastFourSegmentsForWrapper() {

		String url = "https://my-mirror.example.com/maven2/a/b/c/d/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar";

		assertThat(MavenWrapperUrlRewriter.replaceGroupPath(url, "org/apache/maven/wrapper"))
				.isEqualTo(
						"https://my-mirror.example.com/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar");
	}

	@Test
	void replaceFileNameForDistributionPreservesZipExtension() {

		String url = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/wrong-name.zip";

		assertThat(MavenWrapperUrlRewriter.replaceFileName(url, WrapperProperty.DISTRIBUTION, "3.9.6"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void replaceFileNameForDistributionPreservesTarGzExtension() {

		String url = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/wrong-name.tar.gz";

		assertThat(MavenWrapperUrlRewriter.replaceFileName(url, WrapperProperty.DISTRIBUTION, "3.9.6"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz");
	}

	@Test
	void replaceFileNameForDistributionFallsBackToTarGzOnUnknownExtension() {

		String url = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/wrong-name.tgz";

		assertThat(MavenWrapperUrlRewriter.replaceFileName(url, WrapperProperty.DISTRIBUTION, "3.9.6"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz");
	}

	@Test
	void replaceFileNameSuggestionMirrorsTransformedExtension() {

		String url = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/wrong.zip";

		assertThat(MavenWrapperUrlRewriter.replaceFileNameSuggestion(url, WrapperProperty.DISTRIBUTION, "3.9.6"))
				.isEqualTo("apache-maven-3.9.6-bin.zip");
		assertThat(MavenWrapperUrlRewriter.replaceFileNameSuggestion(url, WrapperProperty.WRAPPER, "3.9.6"))
				.isEqualTo("maven-wrapper-3.9.6.jar");
	}

	@Test
	void replaceFileNameForWrapperAlwaysWritesJar() {

		String url = "https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/whatever.zip";

		assertThat(MavenWrapperUrlRewriter.replaceFileName(url, WrapperProperty.WRAPPER, "3.2.0"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar");
	}

	@Test
	void canonicalUrlForDistribution() {

		assertThat(MavenWrapperUrlRewriter.canonicalUrl(WrapperProperty.DISTRIBUTION, "3.9.6"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz");
	}

	@Test
	void canonicalUrlForWrapper() {

		assertThat(MavenWrapperUrlRewriter.canonicalUrl(WrapperProperty.WRAPPER, "3.2.0"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar");
	}

	@Test
	void roundTripDistributionIsIdempotent() {

		String canonical = MavenWrapperUrlRewriter.canonicalUrl(WrapperProperty.DISTRIBUTION, "3.9.6");

		assertThat(MavenWrapperUrlRewriter.replaceVersion(canonical, "3.9.6")).isEqualTo(canonical);
		assertThat(MavenWrapperUrlRewriter.replaceArtifact(canonical, "apache-maven")).isEqualTo(canonical);
		assertThat(MavenWrapperUrlRewriter.replaceGroupPath(canonical, "org/apache/maven")).isEqualTo(canonical);
		assertThat(MavenWrapperUrlRewriter.replaceFileName(canonical, WrapperProperty.DISTRIBUTION, "3.9.6"))
				.isEqualTo(canonical);
		assertThat(MavenWrapperUrlRewriter.forceHttps(canonical)).isEqualTo(canonical);
		assertThat(MavenWrapperUrlRewriter.stripCredentials(canonical)).isEqualTo(canonical);
	}

	@Test
	void roundTripWrapperIsIdempotent() {

		String canonical = MavenWrapperUrlRewriter.canonicalUrl(WrapperProperty.WRAPPER, "3.2.0");

		assertThat(MavenWrapperUrlRewriter.replaceVersion(canonical, "3.2.0")).isEqualTo(canonical);
		assertThat(MavenWrapperUrlRewriter.replaceArtifact(canonical, "maven-wrapper")).isEqualTo(canonical);
		assertThat(MavenWrapperUrlRewriter.replaceGroupPath(canonical, "org/apache/maven/wrapper"))
				.isEqualTo(canonical);
		assertThat(MavenWrapperUrlRewriter.replaceFileName(canonical, WrapperProperty.WRAPPER, "3.2.0"))
				.isEqualTo(canonical);
	}

}
