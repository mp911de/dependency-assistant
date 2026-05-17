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

import java.time.Duration;
import java.util.regex.Matcher;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

/**
 * @author Mark Paluch
 */
class MavenWrapperUtilsUnitTests {

	@ParameterizedTest
	@ValueSource(strings = {
			"/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip",
			"/org/apache/maven/wrapper/maven-wrapper/3.9.6/maven-wrapper-3.9.6.jar",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.9.6-bin.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.9.6-source.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.9.6-script.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.9.6-only-script.zip",

			// Valid but uncommon: snapshot-style repository URL with a normal release-like
			// Maven path.
			"https://repository.apache.org/content/repositories/snapshots/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip"
	})
	void parseVersions(String string) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(string);

		assertThat(matcher.find()).isTrue();
		assertThat(matcher.group("groupId")).contains("org/apache/maven");
		assertThat(matcher.group("version1")).isEqualTo("3.9.6");
		assertThat(matcher.group("version2")).isEqualTo("3.9.6");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/org/apache/maven/apache-maven/3./apache-maven-3.9.6-bin.zip",
			"/org/apache/maven/wrapper/maven-wrapper/3./maven-wrapper-3.9.6.jar",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3./maven-wrapper-distribution-3.9.6-bin.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3./maven-wrapper-distribution-3.9.6-source.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3./maven-wrapper-distribution-3.9.6-script.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3./maven-wrapper-distribution-3.9.6-only-script.zip"
	})
	void firstVersionIncomplete(String string) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(string);

		assertThat(matcher.find()).isTrue();
		assertThat(matcher.group("groupId")).contains("org/apache/maven");
		assertThat(matcher.group("version1")).isEqualTo("3.");
		assertThat(matcher.group("version2")).isEqualTo("3.9.6");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/org/apache/maven/apache-maven/3.9.6/apache-maven-3.-bin.zip",
			"/org/apache/maven/wrapper/maven-wrapper/3.9.6/maven-wrapper-3..jar",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.-bin.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.-source.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.-script.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.-only-script.zip"
	})
	void secondVersionIncomplete(String string) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(string);

		assertThat(matcher.find()).isTrue();
		assertThat(matcher.group("groupId")).contains("org/apache/maven");
		assertThat(matcher.group("version1")).isEqualTo("3.9.6");
		assertThat(matcher.group("version2")).isEqualTo("3.");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/org/apache/maven/apache-maven/3.9.6/apache-maven--bin.zip",
			"/org/apache/maven/wrapper/maven-wrapper/3.9.6/maven-wrapper-.jar",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution--bin.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution--source.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution--script.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution--only-script.zip"
	})
	void secondVersionEmpty(String string) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(string);

		assertThat(matcher.find()).isTrue();
		assertThat(matcher.group("groupId")).contains("org/apache/maven");
		assertThat(matcher.group("version1")).isEqualTo("3.9.6");
		assertThat(matcher.group("version2")).isEqualTo("");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/org/apache/maven/apache-maven/4.0.0-rc-4/apache-maven-4.0.0-rc-4-bin.zip",
			"https://repository.apache.org/content/repositories/snapshots/org/apache/maven/apache-maven/4.1.0-SNAPSHOT/apache-maven-4.1.0-SNAPSHOT-bin.zip",
			"https://repository.apache.org/content/repositories/snapshots/org/apache/maven/apache-maven/4.1.0-SNAPSHOT/apache-maven-4.1.0-20250710.120440-1-bin.zip"
	})
	void parseQualifiedAndSnapshotVersions(String string) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(string);

		assertThat(matcher.find()).isTrue();
		assertThat(matcher.group("groupId")).contains("org/apache/maven");
		assertThat(matcher.group("artifactId1")).isEqualTo("apache-maven");
		assertThat(matcher.group("artifactId2")).isEqualTo("apache-maven");
		assertThat(matcher.group("version1")).isIn("4.0.0-rc-4", "4.1.0-SNAPSHOT");
		assertThat(matcher.group("version2")).isIn("4.0.0-rc-4",
				"4.1.0-SNAPSHOT", "4.1.0-20250710.120440-1");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-bin.zip",
			"/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-javadoc.zip",
			"/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-sources.zip",
			"/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-src.zip",
			"/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-tests.zip",
			"/org/apache/maven/apache-maven/3.9.6/apache-maven-3.9.6-test-sources.zip",

			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.9.6-bin.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.9.6-source.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.9.6-script.zip",
			"/org/apache/maven/wrapper/maven-wrapper-distribution/3.9.6/maven-wrapper-distribution-3.9.6-only-script.zip"
	})
	void excludesClassifierFromVersion2(String string) {

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(string);

		assertThat(matcher.find()).isTrue();
		assertThat(matcher.group("groupId")).contains("org/apache/maven");
		assertThat(matcher.group("version1")).isEqualTo("3.9.6");
		assertThat(matcher.group("version2")).isEqualTo("3.9.6");
	}

	@Test
	void completesQuicklyOnPathologicalInput() {

		String hostile = "/a/b/" + "1.".repeat(200) + "-trailing/c-1.0";

		assertThatCode(() -> MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(hostile).find())
				.doesNotThrowAnyException();
		assertThat(timeMatch(hostile)).isLessThan(Duration.ofSeconds(1));
	}

	private static Duration timeMatch(String input) {
		long start = System.nanoTime();
		MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(input).find();
		return Duration.ofNanos(System.nanoTime() - start);
	}

}
