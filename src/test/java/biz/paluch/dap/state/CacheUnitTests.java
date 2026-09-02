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

package biz.paluch.dap.state;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.fixtures.Releases;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Cache}.
 *
 * @author Mark Paluch
 */
class CacheUnitTests {

	private static final ArtifactId LETTUCE = ArtifactId.of("io.lettuce", "lettuce-core");

	private final Cache cache = new Cache();

	@Test
	void shouldFindByIdentity() {

		CachedArtifact maven = Releases.LETTUCE_CORE;
		CachedArtifact npm = Releases.LETTUCE_CORE.snapshot();
		npm.setPackageSystem(PackageSystem.NPM);
		cache.addArtifacts(maven, npm);

		assertThat(cache.findCachedArtifact(PackageIdentity.of(LETTUCE, PackageSystem.MAVEN))).isSameAs(maven);
		assertThat(cache.findCachedArtifact(PackageIdentity.of(LETTUCE, PackageSystem.NPM))).isSameAs(npm);
	}

	@Test
	void identityLookupAcceptsUndetachedCoordinates() {

		CachedArtifact maven = Releases.LETTUCE_CORE;
		cache.addArtifacts(maven);

		// a CachedArtifact is itself an ArtifactId with identity equality
		assertThat(cache.findCachedArtifact(PackageIdentity.of(maven, PackageSystem.MAVEN))).isSameAs(maven);
	}

	@Test
	void identityLookupFallsBackToEntryWithoutEcosystem() {

		CachedArtifact legacy = new CachedArtifact(LETTUCE);
		cache.addArtifacts(legacy);

		assertThat(cache.findCachedArtifact(PackageIdentity.of(LETTUCE, PackageSystem.NPM))).isSameAs(legacy);
	}

	@Test
	void identityLookupMissesForeignEcosystem() {

		cache.addArtifacts(Releases.LETTUCE_CORE);

		assertThat(cache.findCachedArtifact(PackageIdentity.of(LETTUCE, PackageSystem.NPM))).isNull();
	}

	@Test
	void removeProjectDropsMatchingEntry() {

		ProjectId root = ProjectId.of("com.example", "root", "/repo/pom.xml");
		ProjectId module = ProjectId.of("com.example", "module", "/repo/module/pom.xml");
		cache.getProject(root);
		cache.getProject(module);
		long before = cache.getModificationCount();

		cache.removeProject(module);

		assertThat(cache.getProjects()).extracting(ProjectCache::getId).containsExactly(root);
		assertThat(cache.getModificationCount()).isGreaterThan(before);
	}

}
