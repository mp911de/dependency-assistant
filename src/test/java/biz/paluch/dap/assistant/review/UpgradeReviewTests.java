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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.AppliedDependencyUpdate;
import biz.paluch.dap.assistant.check.DependencyCheckResult;
import biz.paluch.dap.fixtures.TestAssistant;
import biz.paluch.dap.fixtures.TestCandidates;
import biz.paluch.dap.fixtures.TestReleases;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.support.UpgradeStrategy;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
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
		review.setVersion(group, ArtifactVersion.of("6.2.1"));

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
		review.setVersion(group, ArtifactVersion.of("6.2.1"));

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).extracting(update -> update.from().getVersion().toString()).containsExactly("6.2.0",
				"6.0.9");
		assertThat(updates).extracting(DependencyUpdate::versionAsString).containsOnly("6.2.1");
	}

	@Test
	void selectedSingleCandidateProducesOneUpdate() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).hasSize(1);
		assertThat(updates.getFirst().artifactId()).hasToString("org.springframework:spring-core");
		assertThat(updates.getFirst().versionAsString()).isEqualTo("6.2.1");
	}

	@Test
	void deselectedCandidateProducesNoUpdate() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));
		review.setSelected(core, false);

		assertThat(review.getSelectedUpdates()).isEmpty();
	}

	@Test
	void selectAllTogglesApplyStateOfVisibleRows() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");
		TableRow test = candidate(SPRING_TEST, "6.2.0");

		UpgradeReview review = new UpgradeReview(core, test);
		review.selectAll(true);

		assertThat(review.isApplyUpdate(core)).isTrue();
		assertThat(review.isApplyUpdate(test)).isTrue();

		review.selectAll(false);

		assertThat(review.isApplyUpdate(core)).isFalse();
		assertThat(review.isApplyUpdate(test)).isFalse();
	}

	@Test
	void applyStrategyTargetSelectsAndArmsStrategyRelease() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", TestReleases.from("6.2.0", "6.2.1"), "6.2.0");

		UpgradeReview review = new UpgradeReview(core);
		review.applyStrategyTarget(core, UpgradeStrategy.LATEST);

		assertThat(review.getUpdateTo(core)).hasToString("6.2.1");
		assertThat(review.isApplyUpdate(core)).isTrue();
	}

	@Test
	void strategySelectionOnGroupResolvesAgainstIntersectionReleases() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", TestReleases.from("6.2.0", "6.2.1", "6.3.0"), "6.2.0");
		TableRow test = candidate(SPRING_TEST, "6.2.0", TestReleases.from("6.2.0", "6.2.1"), "6.2.0");
		GroupRow group = GroupRow.governed(core, test);

		UpgradeReview review = new UpgradeReview(group);
		review.applyStrategyToAll(UpgradeReview.StrategySelection.LATEST);

		assertThat(review.getUpdateTo(group)).hasToString("6.2.1");
	}

	@Test
	void appliedGroupUpdatesCollapseToOneNotificationEntry() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow test = candidate(SPRING_TEST, "6.2.0");
		GroupRow group = GroupRow.governed(core, test);

		UpgradeReview review = new UpgradeReview(group);
		review.setVersion(group, ArtifactVersion.of("6.2.1"));

		Set<AppliedDependencyUpdate> applied = new TreeSet<>();
		for (DependencyUpdate update : review.getSelectedUpdates()) {
			applied.add(AppliedDependencyUpdate.from(update, group.getRule()));
		}

		assertThat(review.getSelectedUpdates()).hasSize(2);
		assertThat(applied).hasSize(1);
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
	void bareCoordinatesSharingArtifactIdAreAmbiguous() {

		TableRow driver = bareCandidate(POSTGRESQL, "6.2.0");
		TableRow testcontainer = bareCandidate(TESTCONTAINERS_POSTGRESQL, "6.2.0");

		UpgradeReview review = new UpgradeReview(driver, testcontainer);

		assertThat(review.isAmbiguous(driver)).isTrue();
		assertThat(review.isAmbiguous(testcontainer)).isTrue();
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

	@Test
	void toolTipListsSharedPropertyPeers() {

		TableRow core = new TableRow(TestCandidates.candidate(SPRING_CORE, "6.2.0",
				it -> it.releases("6.2.1").versionProperty("spring.version")));
		TableRow addon = new TableRow(TestCandidates.candidate(ADDON, "6.2.0",
				it -> it.releases("6.2.1").versionProperty("spring.version")));

		UpgradeReview review = new UpgradeReview(core, addon);

		assertThat(review.getToolTip(core)).contains("addon").startsWith("<html>");
		assertThat(review.getToolTip(addon)).contains("spring-core");
	}

	@Test
	void sharedVersionPropertySynchronizesTargetAndApplyState() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"),
				VersionSource.profileProperty("dev", "spring.version"), "6.2.0");

		UpgradeReview review = new UpgradeReview(core, addon);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		assertThat(review.getUpdateTo(core)).isEqualTo(ArtifactVersion.of("6.2.1"));
		assertThat(review.getUpdateTo(addon)).isEqualTo(ArtifactVersion.of("6.2.1"));
		assertThat(review.isApplyUpdate(core)).isTrue();
		assertThat(review.isApplyUpdate(addon)).isTrue();

		review.setSelected(addon, false);

		assertThat(review.isApplyUpdate(core)).isFalse();
		assertThat(review.isApplyUpdate(addon)).isFalse();
	}

	@Test
	void sharedVersionTargetAbsentFromPeerReleasesProducesUpdates() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"),
				VersionSource.property("spring.version"), "6.2.0");

		UpgradeReview review = new UpgradeReview(core, addon);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		assertThat(review.getSelectedUpdates()).extracting(DependencyUpdate::versionAsString)
				.containsExactly("6.2.1", "6.2.1");
	}

	@Test
	void reselectingPropagatedTargetAbsentFromPeerReleasesKeepsSelection() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"),
				VersionSource.property("spring.version"), "6.2.0");

		UpgradeReview review = new UpgradeReview(core, addon);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		// the combo pre-selects the synthetic release; committing it again must
		// not fail even though 6.2.1 is absent from the addon's release universe
		review.setVersion(addon, review.getSelectedRelease(addon).version());

		assertThat(review.getUpdateTo(addon)).isEqualTo(ArtifactVersion.of("6.2.1"));
		assertThat(review.isApplyUpdate(addon)).isTrue();
	}

	@Test
	void setVersionKeepsTargetAbsentFromReleaseUniverse() {

		TableRow addon = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"), "6.2.0");

		UpgradeReview review = new UpgradeReview(addon);
		review.setVersion(addon, ArtifactVersion.of("6.9.9"));

		assertThat(review.getUpdateTo(addon)).isEqualTo(ArtifactVersion.of("6.9.9"));
	}

	@Test
	void singleRowVersionPickFiresSingleRowRefresh() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);

		assertThat(changes(review, () -> review.setVersion(core, ArtifactVersion.of("6.2.1"))))
				.containsExactly(ReviewChange.row(core));
	}

	@Test
	void repeatedVersionPickFiresNoChange() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		assertThat(changes(review, () -> review.setVersion(core, ArtifactVersion.of("6.2.1")))).isEmpty();
	}

	@Test
	void versionPickRevealingSharedPropertyPeerFiresReload() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"),
				VersionSource.property("spring.version"), "6.2.0");

		UpgradeReview review = new UpgradeReview(core, addon);

		// arming core propagates to the up-to-date addon, which becomes visible
		assertThat(changes(review, () -> review.setVersion(core, ArtifactVersion.of("6.2.1"))))
				.containsExactly(ReviewChange.reloadVisible());
	}

	@Test
	void disarmingRowVisibleOnlyWhileArmedFiresReload() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"),
				VersionSource.property("spring.version"), "6.2.0");

		UpgradeReview review = new UpgradeReview(core, addon);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		assertThat(changes(review, () -> review.setSelected(addon, false)))
				.containsExactly(ReviewChange.reloadVisible());
	}

	@Test
	void checkboxToggleWithUnchangedVisibilityFiresSingleRowRefresh() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		// core keeps an upgrade target, so disarming does not hide it
		assertThat(changes(review, () -> review.setSelected(core, false)))
				.containsExactly(ReviewChange.row(core));
	}

	@Test
	void versionPickOnCoupledVisibleRowsRefreshesAllRows() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.0", VersionSource.profileProperty("dev", "spring.version"));

		UpgradeReview review = new UpgradeReview(core, addon);

		// both rows keep their own upgrade target, so propagation changes no
		// visibility and the table only refreshes rows in place
		assertThat(changes(review, () -> review.setVersion(core, ArtifactVersion.of("6.2.1"))))
				.containsExactly(ReviewChange.allRows());
	}

	@Test
	void togglingVersionFilterFiresReload() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);

		assertThat(changes(review, () -> review.setHideUpToDate(false)))
				.containsExactly(ReviewChange.reloadVisible());
	}

	@Test
	void applyStrategyTargetWithoutTargetFiresNoChange() {

		TableRow targetless = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"), "6.2.0");

		UpgradeReview review = new UpgradeReview(targetless);

		assertThat(changes(review, () -> review.applyStrategyTarget(targetless, UpgradeStrategy.LATEST)))
				.isEmpty();
	}

	@Test
	void selectAllWithUnchangedVisibilityRefreshesRowsWithoutReload() {

		TableRow core = candidate(SPRING_CORE, "6.2.0");
		TableRow test = candidate(SPRING_TEST, "6.2.0");

		UpgradeReview review = new UpgradeReview(core, test);

		assertThat(changes(review, () -> review.selectAll(true))).containsExactly(ReviewChange.allRows());
	}

	@Test
	void selectAllHidingArmedOnlyRowsFiresReload() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"),
				VersionSource.property("spring.version"), "6.2.0");

		UpgradeReview review = new UpgradeReview(core, addon);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		// disarming all hides the addon, which was visible only while armed
		assertThat(changes(review, () -> review.selectAll(false)))
				.containsExactly(ReviewChange.reloadVisible());
	}

	@Test
	void bulkStrategyUsesFirstPeerWithAnApplicableTarget() {

		TableRow targetless = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"),
				VersionSource.property("spring.version"), "6.2.0");
		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));

		UpgradeReview review = new UpgradeReview(targetless, core);
		review.setHideUpToDate(false);

		assertThat(review.findRelease(targetless, UpgradeStrategy.LATEST)).isNull();
		assertThat(review.findRelease(core, UpgradeStrategy.LATEST)).isNotNull();

		review.applyStrategyToAll(UpgradeReview.StrategySelection.LATEST);

		assertThat(review.getSelectedUpdates()).extracting(DependencyUpdate::versionAsString)
				.containsExactly("6.2.1", "6.2.1");
	}

	@Test
	void selectedSharedPropertyPeerRemainsVisibleWithoutOwnUpgradeTarget() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.0", TestReleases.from("6.2.0"),
				VersionSource.property("spring.version"), "6.2.0");

		UpgradeReview review = new UpgradeReview(core, addon);
		review.setHideUpToDate(true);

		assertThat(review.getCandidates()).containsExactly(core);

		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		assertThat(review.getCandidates()).containsExactly(core, addon);
		assertThat(review.getReleaseOptions(addon)).first().extracting(Release::version)
				.isEqualTo(ArtifactVersion.of("6.2.1"));
	}

	@Test
	void samePropertyNameFromAnotherAssistantDoesNotCoupleRows() {

		TestAssistant other = new TestAssistant() {

			@Override
			public String getId() {
				return "other";
			}

		};
		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = new TableRow(TestCandidates.candidate(ADDON, "6.2.0",
				it -> it.releases("6.2.1").versionProperty("spring.version").assistant(other)));

		UpgradeReview review = new UpgradeReview(core, addon);

		assertThat(review.getSharedPropertyPeers(core)).isEmpty();
		assertThat(review.getSharedPropertyPeers(addon)).isEmpty();
	}

	@Test
	void propagatedApplyStateDoesNotDependOnPeerCurrentVersion() {

		TableRow core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		TableRow addon = candidate(ADDON, "6.2.1", TestReleases.from("6.2.1"),
				VersionSource.property("spring.version"), "6.2.1");

		UpgradeReview review = new UpgradeReview(core, addon);
		review.setVersion(core, ArtifactVersion.of("6.2.1"));

		assertThat(review.isApplyUpdate(core)).isTrue();
		assertThat(review.isApplyUpdate(addon)).isTrue();
	}

	/**
	 * Collect the {@link ReviewChange}s fired while running {@code interaction}.
	 */
	private static List<ReviewChange> changes(UpgradeReview review, Runnable interaction) {

		List<ReviewChange> changes = new ArrayList<>();
		Disposable parent = Disposer.newDisposable();
		try {
			review.addListener(changes::add, parent);
			interaction.run();
		} finally {
			Disposer.dispose(parent);
		}
		return changes;
	}

	/**
	 * A candidate without a governing rule, so the row stays labeled by its bare
	 * coordinate.
	 */
	private static TableRow bareCandidate(ArtifactId artifactId, String version) {
		return new TableRow(TestCandidates.candidate(artifactId, version, it -> it.releases(version, "6.2.1")));
	}

	private static TableRow candidate(ArtifactId artifactId, String version) {
		return candidate(artifactId, version, VersionSource.declared(version));
	}

	private static TableRow candidate(ArtifactId artifactId, String version, VersionSource versionSource) {
		return candidate(artifactId, version, TestReleases.from(version, "6.2.1"), versionSource, version);
	}

	private static TableRow candidate(ArtifactId artifactId, String version, Releases releases,
			String... declaredVersions) {
		return candidate(artifactId, version, releases, VersionSource.declared(version), declaredVersions);
	}

	private static TableRow candidate(ArtifactId artifactId, String version, Releases releases,
			VersionSource versionSource, String... declaredVersions) {
		return new TableRow(TestCandidates.candidate(artifactId, version, it -> it.releases(releases)
				.versionSource(versionSource).rule("Spring Framework").declaredVersions(declaredVersions)));
	}

}
