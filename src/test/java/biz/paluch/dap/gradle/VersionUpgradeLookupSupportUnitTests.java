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
package biz.paluch.dap.gradle;

import static org.assertj.core.api.Assertions.*;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;

import java.util.List;

import org.junit.jupiter.api.Test;

/**
 * Unit tests for {@link VersionUpgradeLookupSupport#determineUpgrade(ArtifactVersion, List)}.
 *
 * @author Mark Paluch
 */
class VersionUpgradeLookupSupportUnitTests {

	@Test
	void returnsNullWhenNoReleasesAvailable() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		VersionUpgradeLookupSupport.UpgradeSuggestion result = VersionUpgradeLookupSupport.determineUpgrade(current,
				List.of());

		assertThat(result).isNull();
	}

	@Test
	void returnsNullWhenCurrentIsAlreadyLatest() {

		ArtifactVersion current = ArtifactVersion.of("2.0.0");
		List<Release> releases = List.of(Release.of("1.5.0"), Release.of("1.0.0"));

		VersionUpgradeLookupSupport.UpgradeSuggestion result = VersionUpgradeLookupSupport.determineUpgrade(current,
				releases);

		assertThat(result).isNull();
	}

	@Test
	void detectsPatchUpgrade() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		List<Release> releases = List.of(Release.of("1.0.1"), Release.of("1.0.0"));

		VersionUpgradeLookupSupport.UpgradeSuggestion result = VersionUpgradeLookupSupport.determineUpgrade(current,
				releases);

		assertThat(result).isNotNull();
		assertThat(result.strategy()).isEqualTo(UpgradeStrategy.PATCH);
		assertThat(result.bestOption().version().toString()).isEqualTo("1.0.1");
		assertThat(result.current()).isEqualTo(current);
	}

	@Test
	void detectsMinorUpgrade() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		List<Release> releases = List.of(Release.of("1.1.0"), Release.of("1.0.0"));

		VersionUpgradeLookupSupport.UpgradeSuggestion result = VersionUpgradeLookupSupport.determineUpgrade(current,
				releases);

		assertThat(result).isNotNull();
		assertThat(result.strategy()).isEqualTo(UpgradeStrategy.MINOR);
		assertThat(result.bestOption().version().toString()).isEqualTo("1.1.0");
	}

	@Test
	void detectsMajorUpgrade() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		List<Release> releases = List.of(Release.of("2.0.0"), Release.of("1.0.0"));

		VersionUpgradeLookupSupport.UpgradeSuggestion result = VersionUpgradeLookupSupport.determineUpgrade(current,
				releases);

		assertThat(result).isNotNull();
		assertThat(result.strategy()).isEqualTo(UpgradeStrategy.MAJOR);
		assertThat(result.bestOption().version().toString()).isEqualTo("2.0.0");
	}

	@Test
	void prefersMajorOverMinorAndPatch() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		// All three tiers available simultaneously.
		List<Release> releases = List.of(Release.of("2.0.0"), Release.of("1.1.0"), Release.of("1.0.1"),
				Release.of("1.0.0"));

		VersionUpgradeLookupSupport.UpgradeSuggestion result = VersionUpgradeLookupSupport.determineUpgrade(current,
				releases);

		assertThat(result).isNotNull();
		assertThat(result.strategy()).isEqualTo(UpgradeStrategy.MAJOR);
		assertThat(result.bestOption().version().toString()).isEqualTo("2.0.0");
	}

	@Test
	void prefersMinorOverPatchWhenNoMajor() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		List<Release> releases = List.of(Release.of("1.2.0"), Release.of("1.0.1"), Release.of("1.0.0"));

		VersionUpgradeLookupSupport.UpgradeSuggestion result = VersionUpgradeLookupSupport.determineUpgrade(current,
				releases);

		assertThat(result).isNotNull();
		assertThat(result.strategy()).isEqualTo(UpgradeStrategy.MINOR);
		assertThat(result.bestOption().version().toString()).isEqualTo("1.2.0");
	}

	@Test
	void returnsNullWhenOnlyPreviewReleasesAvailable() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		// Preview/milestone releases should not trigger an upgrade suggestion.
		List<Release> releases = List.of(Release.of("2.0.0.M1"), Release.of("1.0.0"));

		VersionUpgradeLookupSupport.UpgradeSuggestion result = VersionUpgradeLookupSupport.determineUpgrade(current,
				releases);

		assertThat(result).isNull();
	}

	@Test
	void upgradeMessageIsNonNull() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		List<Release> releases = List.of(Release.of("2.0.0"), Release.of("1.0.0"));

		VersionUpgradeLookupSupport.UpgradeSuggestion result = VersionUpgradeLookupSupport.determineUpgrade(current,
				releases);

		assertThat(result).isNotNull();
		assertThat(result.getMessage()).isNotNull().isNotEmpty();
	}

}
