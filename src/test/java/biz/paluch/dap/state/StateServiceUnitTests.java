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

package biz.paluch.dap.state;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.checker.Vulnerabilities;
import org.junit.jupiter.api.Test;

import static biz.paluch.dap.fixtures.TestVulnerabilities.*;
import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link StateService}.
 *
 * @author Mark Paluch
 */
class StateServiceUnitTests {

	private ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

	private ArtifactId SPRING_TEST = ArtifactId.of("org.springframework", "spring-test");

	private ArtifactId NETTY_BOM = ArtifactId.of("io.netty", "netty-bom");

	private ArtifactId CODEC_HTTP = ArtifactId.of("io.netty", "netty-codec-http");

	@Test
	void returnsEmptyForUnknownArtifact() {
		StateService stateService = new StateService();

		Set<ArtifactVersion> versions = new TreeSet<>();
		stateService.doWithDependencies(projectId -> !projectId.equals(null), dependency -> {
			if (dependency.getArtifactId().equals(SPRING_CORE)) {
				versions.add(dependency.getCurrentVersion());
			}
		});
		assertThat(versions).isEmpty();
	}

	@Test
	void visitsRuntimeDependencies() {

		StateService service = new StateService();
		store(service, "com.acme", "app", SPRING_CORE, "6.1.0");
		store(service, "com.acme", "lib", SPRING_TEST, "6.1.0");

		List<ArtifactId> visited = new ArrayList<>();
		service.doWithDependencies(dependency -> visited.add(dependency.getArtifactId()));
		assertThat(visited).containsExactlyInAnyOrder(SPRING_CORE, SPRING_TEST);
	}

	@Test
	void aggregatesPredictedBomMembershipForUnknownBomVersion() {

		StateService service = new StateService();

		DependencyCollector collector = new DependencyCollector();
		collector.registerUsage(NETTY_BOM, ArtifactVersion.of("4.1.100"),
				DeclarationSource.bom(Map.of(CODEC_HTTP, ArtifactVersion.of("4.1.100"))),
				VersionSource.declared("4.1.100"));
		collector.registerDeclaration(CODEC_HTTP, DeclarationSource.dependency(), VersionSource.none());
		service.getProjectState(ProjectId.of("com.acme", "app")).setDependencies(collector, PackageSystem.MAVEN);

		CachedArtifact member = new CachedArtifact(CODEC_HTTP);
		member.addRelease(new CachedRelease("4.1.108", null));
		member.recordVulnerabilities(1_000L, ArtifactVersion.of("4.1.108"), List.of(HIGH_VULNERABILITY));
		service.getCache().addArtifacts(member);

		Vulnerabilities vulnerabilities = service.getVulnerabilities(NETTY_BOM, ArtifactVersion.of("4.1.108"));

		assertThat(vulnerabilities.isVulnerable()).isTrue();
		assertThat(vulnerabilities).isInstanceOf(BomAggregate.class);
	}

	private static void store(StateService service, String groupId, String artifactId, ArtifactId dependency,
			String version) {
		store(service, groupId, artifactId, dependency, version, VersionSource.declared(version));
	}

	private static void store(StateService service, String groupId, String artifactId, ArtifactId dependency,
			String version, VersionSource versionSource) {

		DependencyCollector collector = new DependencyCollector();
		collector.registerUsage(dependency, ArtifactVersion.of(version), DeclarationSource.dependency(),
				versionSource);
		service.getProjectState(ProjectId.of(groupId, artifactId))
				.setDependencies(collector, PackageSystem.MAVEN);
	}

}
