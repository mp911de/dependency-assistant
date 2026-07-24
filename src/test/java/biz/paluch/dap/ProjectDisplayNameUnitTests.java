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

package biz.paluch.dap;

import biz.paluch.dap.artifact.ArtifactId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ProjectDisplayName}.
 *
 * @author Mark Paluch
 */
class ProjectDisplayNameUnitTests {

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { //
			"Apache Commons Lang;org.apache.commons;commons-lang3", //
			"Guava: Google Core Libraries for Java;com.google.guava;guava", //
			"SLF4J API Module;org.slf4j;slf4j-api", //
			"AWS Java SDK :: Auth;software.amazon.awssdk;auth"})
	void informativeNameIsShownVerbatim(String projectName, String groupId, String artifactId) {
		assertThat(displayName(projectName, groupId, artifactId)).isEqualTo(projectName);
	}

	@ParameterizedTest // parenthesis tail and " BOM" word are trimmed for display
	@CsvSource(delimiter = ';', value = { //
			"Spring Framework (Bill of Materials);org.springframework;spring-framework-bom;Spring Framework", //
			"OkHttp (Parent);com.squareup.okhttp3;parent;OkHttp", //
			"JUnit Jupiter (Aggregator);org.junit.jupiter;junit-jupiter;JUnit Jupiter", //
			"Guava BOM;com.google.guava;guava;Guava", //
			"Kotlin Libraries bill-of-materials;org.jetbrains.kotlin;kotlin-bom;Kotlin Libraries"})
	void trimsWordySuffixes(String projectName, String groupId, String artifactId, String expected) {
		assertThat(displayName(projectName, groupId, artifactId)).isEqualTo(expected);
	}

	@ParameterizedTest // separator debris left behind by the BOM word removal
	@CsvSource(delimiter = ';', value = { //
			"ActiveMQ :: BOM;org.apache.activemq;activemq-parent;ActiveMQ", //
			"Hibernate Search - BOM;org.hibernate.search;hibernate-search-engine;Hibernate Search", //
			"Spring Data Release Train - BOM;org.springframework.data;spring-data-bom;Spring Data Release Train", //
			"REST Assured: BOM;io.rest-assured;rest-assured;REST Assured"})
	void trimsTrailingSeparatorDebris(String projectName, String groupId, String artifactId, String expected) {
		assertThat(displayName(projectName, groupId, artifactId)).isEqualTo(expected);
	}

	@ParameterizedTest // variant collapse and acronym loss are accepted trim losses
	@CsvSource(delimiter = ';', value = { //
			"GAX (Google Api eXtensions) for Java (Core);com.google.api;gax;GAX", //
			"Dataflow (errorprone);com.google.errorprone;error-prone-core;Dataflow", //
			"Spring Expression Language (SpEL);org.springframework;spring-expression;Spring Expression Language"})
	void acceptedTrimLosses(String projectName, String groupId, String artifactId, String expected) {
		assertThat(displayName(projectName, groupId, artifactId)).isEqualTo(expected);
	}

	@Test // acceptance judges the raw name; the trimmed form alone would echo the
			// coordinates
	void acceptanceJudgesRawNameBeforeTrim() {
		assertThat(displayName("Spring Framework (Bill of Materials)", "org.springframework", "spring-framework-bom"))
				.isEqualTo("Spring Framework");
	}

	@Test
	void collapsesWhitespaceAndTrims() {
		assertThat(displayName("AWS Java SDK ::\n\tChecksums ", "software.amazon.awssdk", "checksums"))
				.isEqualTo("AWS Java SDK :: Checksums");
	}

	@Test
	void stripsOneTrailingPeriod() {
		assertThat(displayName("Spring Framework Core.", "org.springframework", "spring-core"))
				.isEqualTo("Spring Framework Core");
	}

	@ParameterizedTest // the grouping tier collapses :: module separators into word boundaries
	@CsvSource(delimiter = ';', value = { //
			"AWS Java SDK :: Auth;software.amazon.awssdk;auth;AWS Java SDK Auth", //
			"AspectJ Runtime;org.aspectj;aspectjrt;AspectJ Runtime", //
			"Guava: Google Core Libraries for Java;com.google.guava;guava;Guava: Google Core Libraries for Java"})
	void groupingNameCollapsesDeclaredBoundaries(String projectName, String groupId, String artifactId,
			String expected) {
		assertThat(groupingName(projectName, groupId, artifactId)).isEqualTo(expected);
	}

	@ParameterizedTest // a boundary-shifted echo carries the word boundary the shape analysis needs
	@CsvSource(delimiter = ';', value = { //
			"AspectJ Weaver;org.aspectj;aspectjweaver", //
			"ANTLR 4 Runtime;org.antlr;antlr4-runtime"})
	void groupingNameKeepsBoundaryShiftedEchoes(String projectName, String groupId, String artifactId) {
		assertThat(groupingName(projectName, groupId, artifactId)).isEqualTo(projectName);
	}

	@ParameterizedTest // a subset echo adds no information over the coordinates
	@CsvSource(delimiter = ';', value = { //
			"Undertow :: Core;io.undertow;undertow-core", //
			"spring-security-bom;org.springframework.security;spring-security-bom", //
			"Spring AOP;org.springframework;spring-aop"})
	void groupingNameOmitsCoordinateSubsetEchoes(String projectName, String groupId, String artifactId) {
		assertThat(groupingName(projectName, groupId, artifactId)).isNull();
	}

	private static String displayName(@Nullable String projectName, String groupId, String artifactId) {
		return ProjectDisplayName.getAcceptedProjectName(ArtifactId.of(groupId, artifactId), projectName);
	}

	private static @Nullable String groupingName(@Nullable String projectName, String groupId, String artifactId) {
		return ProjectDisplayName.getGroupingName(ArtifactId.of(groupId, artifactId), projectName);
	}

}
