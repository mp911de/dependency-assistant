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

package biz.paluch.dap.fixtures;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.state.CachedArtifact;

/**
 * Test fixture providing curated {@link CachedArtifact} samples.
 *
 * @author Mark Paluch
 */
public class Releases {

	private static final List<CachedArtifact> ALL = new ArrayList<>();

	public static final CachedArtifact JUNIT_BOM = create("org.junit", "junit-bom", releases -> releases
			.add("6.0.3", "2026-02-15")
			.add("6.1.0-M1", "2025-11-17")
			.add("6.0.2", "2026-01-06")
			.add("6.0.0-M1", "2025-11-17")
			.add("5.14.3", "2026-02-15")
			.add("5.14.2", "2026-02-14")
			.add("5.14.1", "2026-02-13")
			.add("5.14.0", "2026-02-12"));

	public static final CachedArtifact SPRING_BOOT = create("org.springframework.boot", "org.springframework.boot",
			releases -> releases
					.add("4.0.5")
					.add("4.1.0-M4")
					.add("4.0.4")
					.add("3.5.13"));

	public static final CachedArtifact SPRING_MODULITH_BOM = create("org.springframework.modulith",
			"spring-modulith-bom", releases -> releases
					.add("2.0.5", "2026-03-27")
					.add("2.1.0-M4", "2026-03-27")
					.add("2.0.4", "2026-03-19")
					.add("1.4.10", "2026-03-27"));

	public static final CachedArtifact LETTUCE_CORE = create("io.lettuce", "lettuce-core", releases -> releases
			.add("7.5.1.RELEASE", "2026-04-02")
			.add("7.4.0.BETA1", "2025-12-11")
			.add("7.5.0.RELEASE", "2026-03-02")
			.add("7.4.1.RELEASE", "2026-04-02"));

	public static final CachedArtifact SPRING_DEPENDENCY_MANAGEMENT = create("io.spring.dependency-management",
			"io.spring.dependency-management", releases -> releases
					.add("1.1.7")
					.add("1.0.0.RC2")
					.add("1.1.6")
					.add("1.0.15.RELEASE"));

	public static final CachedArtifact GROOVY = create("org.apache.groovy", "groovy", releases -> releases
			.add("5.0.5", "2026-03-26")
			.add("5.0.0-rc-1", "2025-08-03")
			.add("5.0.4", "2026-01-16")
			.add("4.0.31", "2026-03-26"));

	public static final CachedArtifact REACTOR_BOM = create("io.projectreactor", "reactor-bom", releases -> releases
			.add("2025.0.6", "2026-06-08")
			.add("2025.0.5", "2026-04-14")
			.add("2024.0.18", "2026-06-08")
			.add("2024.0.0", "2024-11-12")
			.add("2020.0.13", "2021-11-09")
			.add("2020.0.0", "2020-10-26")
			.add("Dysprosium-SR25", "2021-11-09")
			.add("Dysprosium-RELEASE", "2019-09-24")
			.add("Aluminium-RELEASE", "2017-02-22"));

	public static final CachedArtifact VAVR = create("io.vavr", "vavr", releases -> releases
			.add("1.0.1", "2026-03-01")
			.add("1.0.0-alpha-4", "2021-11-18")
			.add("1.0.0", "2026-02-09")
			.add("0.11.0", "2025-12-14"));

	public static final CachedArtifact SLF4J_API = create("org.slf4j", "slf4j-api", releases -> releases
			.add("2.0.17", "2025-02-25")
			.add("2.1.0-alpha1", "2024-01-02")
			.add("2.0.16", "2024-08-10")
			.add("1.7.36", "2022-02-08"));

	public static final CachedArtifact KOTLIN_REFLECT = create("org.jetbrains.kotlin", "kotlin-reflect",
			releases -> releases
					.add("2.3.20", "2026-03-16")
					.add("2.4.0-Beta1", "2026-03-31")
					.add("2.3.10", "2026-02-04")
					.add("2.2.21", "2025-10-23"));

	public static final CachedArtifact GUAVA = create("com.google.guava", "guava", releases -> releases
			.add("33.6.0-jre", "2026-04-14")
			.add("23.0-rc1", "2017-07-24")
			.add("33.5.0-jre", "2025-09-17")
			.add("33.4.8-jre", "2025-04-14"));

	public static final CachedArtifact VAVR_MATCH = create("io.vavr", "vavr-match", releases -> releases
			.add("1.0.0", "2026-02-08")
			.add("0.11.0", "2025-11-29")
			.add("0.10.7", "2025-07-20")
			.add("0.9.3", "2019-01-07"));

	public static final CachedArtifact APACHE_MAVEN = create("org.apache.maven", "apache-maven", releases -> releases
			.add("3.10.0", "2026-04-01")
			.add("3.9.9", "2024-10-04")
			.add("3.9.6", "2024-01-21")
			.add("3.9.5", "2023-10-19"));

	public static final CachedArtifact MAVEN_WRAPPER = create("org.apache.maven.wrapper", "maven-wrapper",
			releases -> releases
					.add("3.3.3", "2026-03-12")
					.add("3.4.0-rc-1", "2026-02-09")
					.add("3.3.2", "2024-03-08")
					.add("3.3.1", "2023-10-16"));

	public static final CachedArtifact GRADLE = create("org.gradle", "gradle",
			releases -> releases
					.add("9.5.1", "2026-05-12", "sha-9.5.1")
					.add("9.6.0-rc-1", "2026-05-10")
					.add("9.4.1", "2026-03-19", "sha-9.4.1")
					.add("8.14.4", "2026-01-23", "sha-8.14.4")
					.add("8.14.3", "2026-01-23", "sha-8.14.3"));

	private Releases() {
	}

	/**
	 * @return all artifacts registered in this fixture, in declaration order.
	 */
	public static List<CachedArtifact> all() {
		return List.copyOf(ALL);
	}

	private static CachedArtifact create(String groupId, String artifactId, Consumer<ReleaseBuilder> configurer) {

		CachedArtifact artifact = ReleaseBuilder.cachedArtifact(groupId, artifactId, configurer);
		ALL.add(artifact);
		return artifact;
	}

}
