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

package biz.paluch.dap.support;

import java.util.Arrays;
import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionSource;
import com.intellij.mock.MockPsiElement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link AvailableUpgrades}.
 *
 * @author Mark Paluch
 */
class AvailableUpgradesUnitTests {

	private static final ArtifactReference REF = ArtifactReference.from(it -> it
			.artifact(ArtifactId.of("foo", "bar"))
			.versionSource(VersionSource.none())
			.declarationSource(DeclarationSource.dependency())
			.version(ArtifactVersion.of("1.0"))
			.declarationElement(new MockPsiElement(() -> {
			})));

	@Test
	void getUpgradesIsOrderedMajorMinorPatchPreview() {

		ArtifactVersion current = ArtifactVersion.of("1.0.0");
		List<Release> options = releasesNewestFirst("3.0.0", "1.5.0", "1.0.5", "1.0.0");

		AvailableUpgrades upgrades = VersionUpgradeLookup.determineUpgrades(REF, current, options);

		assertThat(upgrades.getUpgrades().sequencedKeySet()).containsExactly(
				UpgradeStrategy.PATCH, UpgradeStrategy.MINOR, UpgradeStrategy.MAJOR);
	}

	@Test
	void noneInstanceIsEmpty() {

		AvailableUpgrades none = AvailableUpgrades.none();

		assertThat(none.isPresent()).isFalse();
		assertThat(none.getUpgrades()).isEmpty();
		assertThat(none.getLatest()).isNull();
	}

	private static List<Release> releasesNewestFirst(String... versions) {
		return Arrays.stream(versions).map(Release::of).toList();
	}

}
