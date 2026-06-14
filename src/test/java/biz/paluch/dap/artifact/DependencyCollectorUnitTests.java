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

package biz.paluch.dap.artifact;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyCollector}.
 *
 * @author Mark Paluch
 */
class DependencyCollectorUnitTests {

	private ArtifactId CHECKOUT = ArtifactId.of("actions", "checkout");

	private ArtifactId SETUP_JAVA = ArtifactId.of("actions", "setup-java");

	@Test
	void promoteResolvedDeclarationsRegistersUsageWhenResolverReturnsDependency() {

		DependencyCollector collector = new DependencyCollector();
		VersionSource declaredV4 = VersionSource.declared("v4.2.0");
		collector.registerDeclaration(CHECKOUT, DeclarationSource.dependency(), declaredV4);

		collector.promoteResolvedDeclarations(declaration -> Dependency.from(declaration, (ArtifactVersion.of("4.2.0"))));

		assertThat(collector.getUsages()).hasSize(1);
		Dependency usage = collector.getUsage(CHECKOUT);
		assertThat(usage).isNotNull();
		assertThat(usage.getCurrentVersion()).isEqualTo(ArtifactVersion.of("4.2.0"));
		assertThat(usage.getVersionSources()).contains(declaredV4);
		assertThat(usage.getDeclarationSources()).contains(DeclarationSource.dependency());
	}

	@Test
	void promoteResolvedDeclarationsSkipsDeclarationWhenResolverReturnsNull() {

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(CHECKOUT, DeclarationSource.dependency(), VersionSource.declared("unresolvable"));

		collector.promoteResolvedDeclarations(declaration -> null);

		assertThat(collector.getUsages()).isEmpty();
	}

	@Test
	void promoteResolvedDeclarationsDoesNotDisturbAlreadyResolvedUsage() {

		DependencyCollector collector = new DependencyCollector();
		VersionSource v3 = VersionSource.declared("v3.6.0");
		collector.registerDeclaration(CHECKOUT, DeclarationSource.dependency(), v3);
		collector.registerUsage(CHECKOUT, ArtifactVersion.of("3.6.0"), DeclarationSource.dependency(), v3);

		List<ArtifactId> visited = new ArrayList<>();
		collector.promoteResolvedDeclarations(declaration -> {
			visited.add(declaration.getArtifactId());
			return Dependency.from(declaration, ArtifactVersion.of("4.2.0"));
		});

		assertThat(visited).doesNotContain(CHECKOUT);
		assertThat(collector.getUsage(CHECKOUT).getCurrentVersion()).isEqualTo(ArtifactVersion.of("3.6.0"));
	}

	@Test
	void promoteResolvedDeclarationsHandlesMultipleDeclarations() {

		DependencyCollector collector = new DependencyCollector();
		collector.registerDeclaration(CHECKOUT, DeclarationSource.dependency(), VersionSource.declared("v4.2.0"));
		collector.registerDeclaration(SETUP_JAVA, DeclarationSource.dependency(), VersionSource.declared("v4.0.0"));

		collector.promoteResolvedDeclarations(declaration -> {
			if (declaration.getArtifactId().equals(CHECKOUT)) {
				return Dependency.from(declaration, ArtifactVersion.of("4.2.0"));
			}
			return null;
		});

		assertThat(collector.getUsages()).hasSize(1);
		assertThat(collector.getUsage(CHECKOUT)).isNotNull();
		assertThat(collector.getUsage(SETUP_JAVA)).isNull();
	}

}
