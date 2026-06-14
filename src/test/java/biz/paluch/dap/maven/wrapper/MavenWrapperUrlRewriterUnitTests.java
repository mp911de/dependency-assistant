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

import biz.paluch.dap.artifact.Release;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link MavenWrapperUrlRewriter}.
 *
 * @author Mark Paluch
 */
class MavenWrapperUrlRewriterUnitTests {

	private String CANONICAL_DISTRIBUTION = "https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip";

	@ParameterizedTest
	@CsvSource(textBlock = """
			# user:password
			https://alice:secret@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip       , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# user only
			https://alice@repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip              , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# preserves port
			https://alice:secret@repo1.maven.org:8443/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip  , https://repo1.maven.org:8443/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# unchanged when no credentials
			https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip                    , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void stripCredentialsRemovesUserInfoWhenPresent(String input, String expected) {
		assertThat(MavenWrapperUrlRewriter.stripCredentials(input)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(textBlock = """
			# plain http upgraded
			http://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip   , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# uppercase scheme upgraded
			HTTP://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip   , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# mixed-case scheme upgraded
			Http://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip   , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# https left unchanged
			https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip  , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# scheme-less left unchanged
			repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip          , repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			""")
	void forceHttpsUpgradesPlainOrUppercaseHttpOnly(String input, String expected) {
		assertThat(MavenWrapperUrlRewriter.forceHttps(input)).isEqualTo(expected);
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

	@ParameterizedTest
	@CsvSource(textBlock = """
			# distribution: rewrites both path and file artifact
			apache-maven , https://repo1.maven.org/maven2/org/apache/maven/wrong-name/3.9.6/wrong-name-3.9.6-bin.zip       , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# wrapper jar
			maven-wrapper, https://repo1.maven.org/maven2/org/apache/maven/wrapper/wrong/3.2.0/wrong-3.2.0.jar             , https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
			""")
	void replaceArtifactRewritesBothPathAndFileArtifact(String canonicalArtifact, String input, String expected) {
		assertThat(MavenWrapperUrlRewriter.replaceArtifact(input, canonicalArtifact)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(textBlock = """
			# replaces last three segments for distribution
			org/apache/maven        , https://repo1.maven.org/maven2/wrong/group/foo/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip                       , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# preserves mirror prefix
			org/apache/maven        , https://my-mirror.example.com/maven2/wrong/group/foo/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip                 , https://my-mirror.example.com/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# only replaces trailing segments when group-like tokens appear earlier (no-op canonical)
			org/apache/maven        , https://nexus.example/repo/org/apache/maven/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz      , https://nexus.example/repo/org/apache/maven/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz
			# replaces last four segments for wrapper
			org/apache/maven/wrapper, https://my-mirror.example.com/maven2/a/b/c/d/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar                           , https://my-mirror.example.com/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
			""")
	void replaceGroupPathRewritesTrailingSegments(String canonicalGroup, String input, String expected) {
		assertThat(MavenWrapperUrlRewriter.replaceGroupPath(input, canonicalGroup)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(textBlock = """
			# distribution: preserves .zip extension
			DISTRIBUTION , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/wrong-name.zip      , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# distribution: preserves .tar.gz extension
			DISTRIBUTION , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/wrong-name.tar.gz   , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.tar.gz
			# distribution: unknown extension falls back to canonical .zip
			DISTRIBUTION , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/wrong-name.tgz      , https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip
			# wrapper always rewrites file name to a .jar
			WRAPPER      , https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/whatever.zip, https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar
			""")
	void replaceFileNameProducesCanonicalFileName(WrapperProperty property, String input, String expected) {
		String version = property == WrapperProperty.WRAPPER ? "3.2.0" : "3.9.6";
		assertThat(MavenWrapperUrlRewriter.replaceFileName(input, property, version)).isEqualTo(expected);
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
	void replaceFileNameReturnsInputWhenNoSlashPresent() {
		assertThat(MavenWrapperUrlRewriter.replaceFileName("nofilepart", WrapperProperty.DISTRIBUTION, "3.9.6"))
				.isEqualTo("nofilepart");
	}

	@Test
	void canonicalUrlForDistribution() {

		assertThat(MavenWrapperUrlRewriter.canonicalUrl(WrapperProperty.DISTRIBUTION, "3.9.6"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip");
	}

	@Test
	void canonicalUrlForWrapper() {

		assertThat(MavenWrapperUrlRewriter.canonicalUrl(WrapperProperty.WRAPPER, "3.2.0"))
				.isEqualTo(
						"https://repo1.maven.org/maven2/org/apache/maven/wrapper/maven-wrapper/3.2.0/maven-wrapper-3.2.0.jar");
	}

	@Test
	void canonicalUrlForVersionAwareUsesNormalizedVersionAndDefaultExtension() {

		Release release = Release.of("3.9.6");

		assertThat(MavenWrapperUrlRewriter.canonicalUrl(WrapperProperty.DISTRIBUTION, release))
				.isEqualTo(CANONICAL_DISTRIBUTION);
		assertThat(MavenWrapperUrlRewriter.canonicalUrl(WrapperProperty.WRAPPER, Release.of("3.2.0")))
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
