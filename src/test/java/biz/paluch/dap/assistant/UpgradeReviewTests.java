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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.checker.VulnerabilityRepository;
import biz.paluch.dap.fixtures.TestDependencyRule;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.mock.MockVirtualFile;
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
	void groupApplyFansOutToOneUpdatePerMemberCoordinate() {

		UpgradeCandidate core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		UpgradeCandidate test = candidate(SPRING_TEST, "6.2.0");
		UpgradeGroup group = UpgradeGroup.of(core, test);

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

		UpgradeCandidate core = candidate(SPRING_CORE, "6.2.0");
		UpgradeCandidate drifting = candidate(SPRING_TEST, "6.0.9", releases("6.0.9", "6.2.1"), "6.2.0",
				"6.0.9");
		UpgradeGroup group = UpgradeGroup.of(core, drifting);

		UpgradeReview review = new UpgradeReview(group);
		review.selectTarget(group, ArtifactVersion.of("6.2.1"));

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).extracting(update -> update.from().getVersion().toString()).containsExactly("6.2.0",
				"6.0.9");
		assertThat(updates).extracting(DependencyUpdate::versionAsString).containsOnly("6.2.1");
	}

	@Test
	void selectedSingleCandidateProducesOneUpdate() {

		UpgradeCandidate core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);
		review.selectTarget(core, ArtifactVersion.of("6.2.1"));

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).hasSize(1);
		assertThat(updates.getFirst().artifactId()).hasToString("Spring Framework");
		assertThat(updates.getFirst().versionAsString()).isEqualTo("6.2.1");
	}

	@Test
	void deselectedCandidateProducesNoUpdate() {

		UpgradeCandidate core = candidate(SPRING_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core);
		review.selectTarget(core, ArtifactVersion.of("6.2.1"));
		review.setSelected(core, false);

		assertThat(review.getSelectedUpdates()).isEmpty();
	}

	@Test
	void strategySelectionOnGroupResolvesAgainstIntersectionReleases() {

		UpgradeCandidate core = candidate(SPRING_CORE, "6.2.0", releases("6.2.0", "6.2.1", "6.3.0"));
		UpgradeCandidate test = candidate(SPRING_TEST, "6.2.0", releases("6.2.0", "6.2.1"));
		UpgradeGroup group = UpgradeGroup.of(List.of(core, test));

		UpgradeReview review = new UpgradeReview(group);
		review.applyStrategyToAll(UpgradeReview.UpgradeStrategies.LATEST);

		assertThat(review.getUpdateTo(group)).hasToString("6.2.1");
	}

	@Test
	void appliedGroupUpdatesCollapseToOneNotificationEntry() {

		UpgradeCandidate core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		UpgradeCandidate test = candidate(SPRING_TEST, "6.2.0");
		UpgradeGroup group = UpgradeGroup.of(List.of(core, test));

		UpgradeReview review = new UpgradeReview(group);
		review.selectTarget(group, ArtifactVersion.of("6.2.1"));

		Set<AppliedDependencyUpdate> applied = new TreeSet<>();
		for (DependencyUpdate update : review.getSelectedUpdates()) {
			applied.add(AppliedDependencyUpdate.of(update.artifactId(), update.from().getVersion(), update.version(),
					biz.paluch.dap.rule.DependencyRule.absent(), update.getUpgradeStrategy()));
		}

		assertThat(applied).singleElement()
				.satisfies(update -> assertThat(update.displayLabel()).isEqualTo("Spring Framework"));
	}

	@Test
	void ambiguityIsComputedOverFullCandidateSetRegardlessOfFilter() {

		UpgradeCandidate driver = candidate(POSTGRESQL, "6.2.0");
		UpgradeCandidate testcontainer = candidate(TESTCONTAINERS_POSTGRESQL, "6.2.0");
		UpgradeCandidate lettuce = candidate(LETTUCE_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(driver, testcontainer, lettuce);

		assertThat(review.isAmbiguous(driver)).isTrue();
		assertThat(review.isAmbiguous(testcontainer)).isTrue();
		assertThat(review.isAmbiguous(lettuce)).isFalse();

		review.setHideUpToDate(true);

		assertThat(review.isAmbiguous(driver)).isTrue();
	}

	@Test
	void rowsLabeledByRuleNameDoNotMakeCoordinatesAmbiguous() {

		UpgradeCandidate driver = candidate(POSTGRESQL, "6.2.0");
		UpgradeCandidate testcontainer = candidate(TESTCONTAINERS_POSTGRESQL, "6.2.0");
		testcontainer.labelByDependencyName();

		UpgradeReview review = new UpgradeReview(driver, testcontainer);

		assertThat(review.isAmbiguous(driver)).isFalse();
	}

	@Test
	void sharedVersionPropertyCrossReferencesCoupledRowsByBareName() {

		UpgradeCandidate core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		UpgradeCandidate addon = candidate(ADDON, "6.2.0", VersionSource.profileProperty("dev", "spring.version"));
		UpgradeCandidate lettuce = candidate(LETTUCE_CORE, "6.2.0");

		UpgradeReview review = new UpgradeReview(core, addon, lettuce);

		assertThat(review.getSharedPropertyPeers(core)).containsExactly(addon);
		assertThat(review.getSharedPropertyPeers(addon)).containsExactly(core);
		assertThat(review.getSharedPropertyPeers(lettuce)).isEmpty();
	}

	@Test
	void groupCrossReferencesUngovernedRowSharingMemberProperty() {

		UpgradeCandidate core = candidate(SPRING_CORE, "6.2.0", VersionSource.property("spring.version"));
		UpgradeCandidate test = candidate(SPRING_TEST, "6.2.0");
		UpgradeGroup group = UpgradeGroup.of(core, test);
		UpgradeCandidate ungoverned = candidate(ADDON, "6.2.0", VersionSource.property("spring.version"));

		UpgradeReview review = new UpgradeReview(group, ungoverned);

		assertThat(review.getSharedPropertyPeers(group)).containsExactly(ungoverned);
		assertThat(review.getSharedPropertyPeers(ungoverned)).containsExactly(group);
	}


	private static Dependency dependency(ArtifactId artifactId, String version, VersionSource versionSource) {
		ArtifactVersion artifactVersion = ArtifactVersion.of(version);
		Dependency dependency = new Dependency(artifactId, artifactVersion);
		dependency.addVersionSource(versionSource);
		return dependency;
	}

	private static UpgradeCandidate candidate(ArtifactId artifactId, String version) {
		return candidate(dependency(artifactId, version, VersionSource.declared(version)));
	}

	private static UpgradeCandidate candidate(ArtifactId artifactId, String version, VersionSource versionSource) {
		return candidate(dependency(artifactId, version, versionSource));
	}

	private static UpgradeCandidate candidate(ArtifactId artifactId, String version, Releases releases,
			String... declaredVersions) {

		Dependency dependency = dependency(artifactId, version, VersionSource.declared(version));
		return candidate(dependency, releases, declaredVersions(dependency, declaredVersions));
	}

	private static UpgradeCandidate candidate(Dependency dependency) {
		return candidate(dependency, releases(dependency.getCurrentVersion().toString(), "6.2.1"),
				declaredVersions(dependency));
	}

	private static UpgradeCandidate candidate(Dependency dependency, Releases releases,
			DeclaredVersions declaredVersions) {
		return new UpgradeCandidate(
				new DependencyUpdateCandidate(dependency, releases, VulnerabilityRepository.empty(),
						new TestDependencyRule("Spring Framework")),
				TestInterfaceAssistant.INSTANCE, declaredVersions);
	}

	private static DeclaredVersions declaredVersions(Dependency dependency, String... versions) {

		List<DeclarationSite> declarations = new ArrayList<>();
		List<String> declaredVersions = versions.length == 0 ? List.of(dependency.getCurrentVersion().toString())
				: List.of(versions);
		for (String version : declaredVersions) {
			Dependency declared = new Dependency(dependency.getArtifactId(), ArtifactVersion.of(version));
			declarations.add(new DeclarationSite(new MockVirtualFile("review-" + version + "/pom.xml", "// test"),
					ProjectId.of("com.acme", "app"), declared));
		}
		return DeclaredVersions.from(declarations, it -> null, null);
	}

	private static Releases releases(String... versions) {

		List<Release> releases = new ArrayList<>();
		for (String version : versions) {
			releases.add(Release.of(version));
		}
		return Releases.of(releases);
	}

}
