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

package biz.paluch.dap.assistant.review;

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.AppliedDependencyUpdate;
import biz.paluch.dap.assistant.check.DependencyCheckResult;
import biz.paluch.dap.fixtures.TestCandidates;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileScope;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link UpgradeReview}.
 *
 * @author Mark Paluch
 */
class UpgradeReviewTests {

	private static final ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

	private static final ArtifactId SPRING_TEST = ArtifactId.of("org.springframework", "spring-test");

	private static final ArtifactId POSTGRESQL = ArtifactId.of("org.postgresql", "postgresql");

	private static final ArtifactId TESTCONTAINERS_POSTGRESQL = ArtifactId.of("org.testcontainers", "postgresql");

	private static final ArtifactId LETTUCE_CORE = biz.paluch.dap.fixtures.Releases.LETTUCE_CORE.toArtifactId();

	private static final ArtifactId ADDON = ArtifactId.of("com.example", "addon");

	@Test
	void assemblesAndGroupsTransportedDecisions() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");
		TableRow test = candidate(SPRING_TEST, "6.2.0");
		DependencyCheckResult transported = new DependencyCheckResult(
				List.of(core.getUpgrade(), test.getUpgrade()), FileScope.of(), List.of());

		UpgradeReview review = new UpgradeReview(transported);

		assertThat(review.getAllCandidates()).singleElement().isInstanceOfSatisfying(GroupRow.class,
				group -> assertThat(group.getMembers()).extracting(TableRow::getArtifactId)
						.containsExactly(SPRING_CORE, SPRING_TEST));
	}

	@Test
	void groupApplyFansOutToOneUpdatePerMemberCoordinate() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow test = candidate(SPRING_TEST, "6.2.0");
		GroupRow group = GroupRow.governed(core, test);

		UpgradeReview review = new UpgradeReview(group);
		review.selectTarget(group, ArtifactVersion.of("6.2.1"));

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).hasSize(2);
		assertThat(updates).extracting(update -> update.artifactId().artifactId()).containsExactly("spring-core",
				"spring-test");
		assertThat(updates).extracting(DependencyUpdate::versionAsString).containsOnly("6.2.1");
		assertThat(updates.getFirst().versionSources()).containsExactly(VersionSource.property("spring.version"));
	}

	@Test
	void groupFanOutCarriesDriftingMemberCurrentVersion() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");
		TableRow drifting = candidate(SPRING_TEST, "6.0.9", TestReleases.from("6.0.9", "6.2.1"), "6.2.0",
				"6.0.9");
		GroupRow group = GroupRow.governed(core, drifting);

		UpgradeReview review = new UpgradeReview(group);
		review.selectTarget(group, ArtifactVersion.of("6.2.1"));

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).extracting(update -> update.from().getVersion().toString()).containsExactly("6.2.0",
				"6.0.9");
		assertThat(updates).extracting(DependencyUpdate::versionAsString).containsOnly("6.2.1");
	}

	@Test
	void selectedSingleCandidateProducesOneUpdate() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);
		review.selectTarget(core, ArtifactVersion.of("6.2.1"));

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).hasSize(1);
		assertThat(updates.getFirst().artifactId()).hasToString("spring-core");
		assertThat(updates.getFirst().versionAsString()).isEqualTo("6.2.1");
	}

	@Test
	void deselectedCandidateProducesNoUpdate() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);
		review.selectTarget(core, ArtifactVersion.of("6.2.1"));
		review.setSelected(core, false);

		assertThat(review.getSelectedUpdates()).isEmpty();
	}

	@Test
	void strategySelectionOnGroupResolvesAgainstIntersectionReleases() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", TestReleases.from("6.2.0", "6.2.1", "6.3.0"));
		TableRow test = candidate(SPRING_TEST, "6.2.0", TestReleases.from("6.2.0", "6.2.1"));
		GroupRow group = GroupRow.governed(core, test);

		UpgradeReview review = new UpgradeReview(group);
		review.applyStrategyToAll(UpgradeReview.UpgradeStrategies.LATEST);

		assertThat(review.getUpdateTo(group)).hasToString("6.2.1");
	}

	@Test
	void appliedGroupUpdatesCollapseToOneNotificationEntry() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow test = candidate(SPRING_TEST, "6.2.0");
		GroupRow group = GroupRow.governed(core, test);

		UpgradeReview review = new UpgradeReview(group);
		review.selectTarget(group, ArtifactVersion.of("6.2.1"));

		Set<AppliedDependencyUpdate> applied = new TreeSet<>();
		for (DependencyUpdate update : review.getSelectedUpdates()) {
			applied.add(AppliedDependencyUpdate.of(update.artifactId(), update.from().getVersion(), update.version(),
					biz.paluch.dap.rule.DependencyRule.absent(), update.getUpgradeStrategy()));
		}

	}

	@Test
	void ambiguityIsComputedOverFullCandidateSetRegardlessOfFilter() {

		TableRow driver = candidate(POSTGRESQL, "6.2.0");
		TableRow testcontainer = candidate(TESTCONTAINERS_POSTGRESQL, "6.2.0");
		TableRow lettuce = candidate(LETTUCE_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(driver, testcontainer, lettuce);

		assertThat(review.isAmbiguous(driver)).isTrue();
		assertThat(review.isAmbiguous(testcontainer)).isTrue();
		assertThat(review.isAmbiguous(lettuce)).isFalse();

		review.setHideUpToDate(true);

		assertThat(review.isAmbiguous(driver)).isTrue();
	}

	@Test
	void rowsLabeledByRuleNameDoNotMakeCoordinatesAmbiguous() {

		TableRow driver = candidate(POSTGRESQL, "6.2.0");
		TableRow testcontainer = candidate(TESTCONTAINERS_POSTGRESQL, "6.2.0");
		testcontainer.labelByDependencyName();

		UpgradeReview review = new UpgradeReview(driver, testcontainer);

		assertThat(review.isAmbiguous(driver)).isFalse();
	}

	@Test
	void sharedVersionPropertyCrossReferencesCoupledRowsByBareName() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.0", VersionSource.profileProperty("dev", "spring.version"));
		TableRow lettuce = candidate(LETTUCE_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core, addon, lettuce);

		assertThat(review.getSharedPropertyPeers(core)).containsExactly(addon);
		assertThat(review.getSharedPropertyPeers(addon)).containsExactly(core);
		assertThat(review.getSharedPropertyPeers(lettuce)).isEmpty();
	}

	@Test
	void groupCrossReferencesUngovernedRowSharingMemberProperty() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow test = candidate(SPRING_TEST, "6.2.0");
		GroupRow group = GroupRow.governed(core, test);
		TableRow ungoverned = candidate(ADDON, "6.2.0", VersionSource.property("spring.version"));

		UpgradeReview review = new UpgradeReview(group, ungoverned);

		assertThat(review.getSharedPropertyPeers(group)).containsExactly(ungoverned);
		assertThat(review.getSharedPropertyPeers(ungoverned)).containsExactly(group);
	}


	private static TableRow candidate(ArtifactId artifactId, String version) {
		return candidate(artifactId, version, VersionSource.declared(version));
	}

	private static TableRow candidate(ArtifactId artifactId, String version, VersionSource versionSource) {
		return candidate(artifactId, version, TestReleases.from(version, "6.2.1"), versionSource, version);
	}

	private static TableRow candidate(ArtifactId artifactId, String version, Releases releases,
			String... declaredVersions) {
		return candidate(artifactId, version, releases, VersionSource.declared(version),
				declaredVersions.length == 0 ? new String[] {version} : declaredVersions);
	}

	private static TableRow candidate(ArtifactId artifactId, String version, Releases releases,
			VersionSource versionSource, String... declaredVersions) {
		return new TableRow(TestCandidates.candidate(artifactId, version, it -> it.releases(releases)
				.versionSource(versionSource).rule("Spring Framework").declaredVersions(declaredVersions)));
	}

}
