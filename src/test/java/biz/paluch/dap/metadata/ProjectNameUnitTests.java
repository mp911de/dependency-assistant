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

package biz.paluch.dap.metadata;

import biz.paluch.dap.artifact.ArtifactId;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link ProjectName}.
 *
 * @author Mark Paluch
 */
class ProjectNameUnitTests {

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { //
			"Apache Commons Lang;org.apache.commons;commons-lang3", //
			"Guava: Google Core Libraries for Java;com.google.guava;guava", //
			"SLF4J API Module;org.slf4j;slf4j-api", //
			"AWS Java SDK :: Auth;software.amazon.awssdk;auth"})
	void informativeNameIsShownVerbatim(String projectName, String groupId, String artifactId) {
		assertThat(getDisplayName(projectName, groupId, artifactId)).isEqualTo(projectName);
	}

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { //
			"Spring Framework (Bill of Materials);org.springframework;spring-framework-bom;Spring Framework", //
			"OkHttp (Parent);com.squareup.okhttp3;parent;OkHttp", //
			"JUnit Jupiter (Aggregator);org.junit.jupiter;junit-jupiter;JUnit Jupiter", //
			"Guava BOM;com.google.guava;guava;Guava", //
			"Kotlin Gradle Plugins Bom;org.jetbrains.kotlin;kotlin-gradle-plugins-bom;Kotlin Gradle Plugins", //
			"Protocol Buffers [BOM];com.google.protobuf;protobuf-bom;Protocol Buffers", //
			"Netty/BOM;io.netty;netty-bom;Netty", //
			"Kotlin Libraries bill-of-materials;org.jetbrains.kotlin;kotlin-bom;Kotlin Libraries"})
	void trimsWordySuffixes(String projectName, String groupId, String artifactId, String expected) {
		assertThat(getDisplayName(projectName, groupId, artifactId)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { //
			"Hibernate Search - BOM;org.hibernate.search;hibernate-search-engine;Hibernate Search", //
			"Spring Data Release Train - BOM;org.springframework.data;spring-data-bom;Spring Data", //
			"REST Assured: BOM;io.rest-assured;rest-assured;REST Assured"})
	void trimsTrailingSeparatorDebris(String projectName, String groupId, String artifactId, String expected) {
		assertThat(getDisplayName(projectName, groupId, artifactId)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { //
			"Project Reactor 3 Release Train - BOM;io.projectreactor;reactor-bom;Project Reactor", //
			"Bouncy Castle Java 8+ 1.82 (Bill of Materials);org.bouncycastle;bc-jdk18on-bom;Bouncy Castle Java 8+", //
			"JBoss Logging 3;org.jboss.logging;jboss-logging;JBoss Logging"})
	void trimsTrailingVersion(String projectName, String groupId, String artifactId, String expected) {
		assertThat(getDisplayName(projectName, groupId, artifactId)).isEqualTo(expected);
	}

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { //
			"ActiveMQ :: BOM;org.apache.activemq;activemq-bom", //
			"Testcontainers :: BOM;org.testcontainers;testcontainers-bom", //
			"Core :: BOM;org.eclipse.jetty;jetty-bom", //
			"AWS Java SDK :: Bill of Materials Internal;software.amazon.awssdk;bom-internal"})
	void keepsModuleStyleNames(String projectName, String groupId, String artifactId) {
		assertThat(getDisplayName(projectName, groupId, artifactId)).isEqualTo(projectName);
	}

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { //
			"JAXB BOM with ALL dependencies;org.glassfish.jaxb;jaxb-bom-ext", //
			"Apache HttpComponents Core HTTP/2;org.apache.httpcomponents.core5;httpcore5-h2"})
	void keepsNonTrailingRoleWords(String projectName, String groupId, String artifactId) {
		assertThat(getDisplayName(projectName, groupId, artifactId)).isEqualTo(projectName);
	}

	@Test
	void bareRoleWordIsAbsent() {
		ProjectName bom = ProjectName.of(ArtifactId.of("org.mongodb", "mongodb-driver-bom"), "bom");
		assertThat(bom.hasDisplayName()).isFalse();
	}

	@ParameterizedTest
	@CsvSource(delimiter = ';', value = { //
			"GAX (Google Api eXtensions) for Java (Core);com.google.api;gax;GAX", //
			"Dataflow (errorprone);com.google.errorprone;error-prone-core;Dataflow", //
			"Spring Expression Language (SpEL);org.springframework;spring-expression;Spring Expression Language"})
	void acceptedTrimLosses(String projectName, String groupId, String artifactId, String expected) {
		assertThat(getDisplayName(projectName, groupId, artifactId)).isEqualTo(expected);
	}

	@Test
	void acceptanceJudgesRawNameBeforeTrim() {
		assertThat(
				getDisplayName("Spring Framework (Bill of Materials)", "org.springframework", "spring-framework-bom"))
				.isEqualTo("Spring Framework");
	}

	@Test
	void collapsesWhitespaceAndTrims() {
		assertThat(getDisplayName("AWS Java SDK ::\n\tChecksums ", "software.amazon.awssdk", "checksums"))
				.isEqualTo("AWS Java SDK :: Checksums");
	}

	@Test
	void stripsOneTrailingPeriod() {
		assertThat(getDisplayName("Spring Framework Core.", "org.springframework", "spring-core"))
				.isEqualTo("Spring Framework Core");
	}

	private static String getDisplayName(@Nullable String projectName, String groupId, String artifactId) {
		return ProjectName.of(ArtifactId.of(groupId, artifactId), projectName).getDisplayName();
	}

}
