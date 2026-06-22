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

import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
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

	ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

		ArtifactId SPRING_TEST = ArtifactId.of("org.springframework", "spring-test");

		ArtifactVersion CURRENT = ArtifactVersion.of("6.2.0");

		ArtifactVersion TARGET = ArtifactVersion.of("6.2.1");

	@Test
	void groupApplyFansOutToOneUpdatePerMemberCoordinate() {

		UpgradeCandidate core = candidate(SPRING_CORE, CURRENT, VersionSource.property("spring.version"));
		UpgradeCandidate test = candidate(SPRING_TEST, CURRENT, VersionSource.declared(CURRENT.toString()));
		UpgradeGroup group = UpgradeGroup.of(List.of(core, test));

		UpgradeReview review = new UpgradeReview(List.of(group), List.of());
		review.selectTarget(group, TARGET);

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).hasSize(2);
		assertThat(updates).extracting(DependencyUpdate::coordinate)
				.allSatisfy(coordinate -> assertThat(coordinate).hasToString("Spring Framework"));
		assertThat(updates).extracting(update -> update.coordinate().artifactId()).containsExactly("spring-core",
				"spring-test");
		assertThat(updates).extracting(DependencyUpdate::version).containsOnly(TARGET);
		assertThat(updates.getFirst().versionSources()).containsExactly(VersionSource.property("spring.version"));
	}

	@Test
	void groupFanOutCarriesDriftingMemberCurrentVersion() {

		ArtifactVersion older = ArtifactVersion.of("6.0.9");

		UpgradeCandidate core = candidate(SPRING_CORE, CURRENT, VersionSource.declared(CURRENT.toString()));
		UpgradeCandidate drifting = candidate(SPRING_TEST, older, VersionSource.declared(older.toString()),
				site(SPRING_TEST, CURRENT, "drift/pom.xml"), site(SPRING_TEST, older, "drift-b/pom.xml"));
		UpgradeGroup group = UpgradeGroup.of(List.of(core, drifting));

		UpgradeReview review = new UpgradeReview(List.of(group), List.of());
		review.selectTarget(group, TARGET);

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).extracting(DependencyUpdate::from).containsExactly(CURRENT, older);
		assertThat(updates).extracting(DependencyUpdate::version).containsOnly(TARGET);
	}

	@Test
	void selectedSingleCandidateProducesOneUpdate() {

		UpgradeCandidate core = candidate(SPRING_CORE, CURRENT, VersionSource.declared(CURRENT.toString()));

		UpgradeReview review = new UpgradeReview(List.of(core), List.of());
		review.selectTarget(core, TARGET);

		List<DependencyUpdate> updates = review.getSelectedUpdates();

		assertThat(updates).hasSize(1);
		assertThat(updates.getFirst().coordinate()).hasToString("Spring Framework");
		assertThat(updates.getFirst().version()).isEqualTo(TARGET);
	}

	@Test
	void deselectedCandidateProducesNoUpdate() {

		UpgradeCandidate core = candidate(SPRING_CORE, CURRENT, VersionSource.declared(CURRENT.toString()));

		UpgradeReview review = new UpgradeReview(List.of(core), List.of());
		review.selectTarget(core, TARGET);
		review.setSelected(core, false);

		assertThat(review.getSelectedUpdates()).isEmpty();
	}

	@Test
	void strategySelectionOnGroupResolvesAgainstIntersectionReleases() {

		ArtifactVersion next = ArtifactVersion.of("6.3.0");

		UpgradeCandidate core = candidate(SPRING_CORE, CURRENT, Releases.of(Release.of(CURRENT), Release.of(TARGET),
				Release.of(next)));
		UpgradeCandidate test = candidate(SPRING_TEST, CURRENT, Releases.of(Release.of(CURRENT), Release.of(TARGET)));
		UpgradeGroup group = UpgradeGroup.of(List.of(core, test));

		UpgradeReview review = new UpgradeReview(List.of(group), List.of());
		review.applyStrategyToAll(UpgradeReview.UpgradeStrategies.LATEST);

		assertThat(review.getUpdateTo(group)).isEqualTo(TARGET);
	}

	@Test
	void appliedGroupUpdatesCollapseToOneNotificationEntry() {

		UpgradeCandidate core = candidate(SPRING_CORE, CURRENT, VersionSource.property("spring.version"));
		UpgradeCandidate test = candidate(SPRING_TEST, CURRENT, VersionSource.declared(CURRENT.toString()));
		UpgradeGroup group = UpgradeGroup.of(List.of(core, test));

		UpgradeReview review = new UpgradeReview(List.of(group), List.of());
		review.selectTarget(group, TARGET);

		Set<AppliedDependencyUpdate> applied = new TreeSet<>();
		for (DependencyUpdate update : review.getSelectedUpdates()) {
			applied.add(new AppliedDependencyUpdate(update.coordinate(), update.from(), update.version()));
		}

		assertThat(applied).singleElement()
				.satisfies(update -> assertThat(update.getDependencyName()).isEqualTo("Spring Framework"));
	}

	@Test
	void ambiguityIsComputedOverFullCandidateSetRegardlessOfFilter() {

		UpgradeCandidate driver = candidate(ArtifactId.of("org.postgresql", "postgresql"), CURRENT,
				VersionSource.declared(CURRENT.toString()));
		UpgradeCandidate testcontainer = candidate(ArtifactId.of("org.testcontainers", "postgresql"), CURRENT,
				VersionSource.declared(CURRENT.toString()));
		UpgradeCandidate lettuce = candidate(ArtifactId.of("io.lettuce", "lettuce-core"), CURRENT,
				VersionSource.declared(CURRENT.toString()));

		UpgradeReview review = new UpgradeReview(List.of(driver, testcontainer, lettuce), List.of());

		assertThat(review.isAmbiguous(driver)).isTrue();
		assertThat(review.isAmbiguous(testcontainer)).isTrue();
		assertThat(review.isAmbiguous(lettuce)).isFalse();

		review.setHideUpToDate(true);

		assertThat(review.isAmbiguous(driver)).isTrue();
	}

	@Test
	void rowsLabeledByRuleNameDoNotMakeCoordinatesAmbiguous() {

		UpgradeCandidate driver = candidate(ArtifactId.of("org.postgresql", "postgresql"), CURRENT,
				VersionSource.declared(CURRENT.toString()));
		UpgradeCandidate testcontainer = candidate(ArtifactId.of("org.testcontainers", "postgresql"), CURRENT,
				VersionSource.declared(CURRENT.toString()));
		testcontainer.labelByDependencyName();

		UpgradeReview review = new UpgradeReview(List.of(driver, testcontainer), List.of());

		assertThat(review.isAmbiguous(driver)).isFalse();
	}

	@Test
	void sharedVersionPropertyCrossReferencesCoupledRowsByBareName() {

		UpgradeCandidate core = candidate(SPRING_CORE, CURRENT, VersionSource.property("spring.version"));
		UpgradeCandidate addon = candidate(ArtifactId.of("com.example", "addon"), CURRENT,
				VersionSource.profileProperty("dev", "spring.version"));
		UpgradeCandidate lettuce = candidate(ArtifactId.of("io.lettuce", "lettuce-core"), CURRENT,
				VersionSource.declared(CURRENT.toString()));

		UpgradeReview review = new UpgradeReview(List.of(core, addon, lettuce), List.of());

		assertThat(review.getSharedPropertyPeers(core)).containsExactly(addon);
		assertThat(review.getSharedPropertyPeers(addon)).containsExactly(core);
		assertThat(review.getSharedPropertyPeers(lettuce)).isEmpty();
	}

	@Test
	void groupCrossReferencesUngovernedRowSharingMemberProperty() {

		UpgradeCandidate core = candidate(SPRING_CORE, CURRENT, VersionSource.property("spring.version"));
		UpgradeCandidate test = candidate(SPRING_TEST, CURRENT, VersionSource.declared(CURRENT.toString()));
		UpgradeGroup group = UpgradeGroup.of(List.of(core, test));
		UpgradeCandidate ungoverned = candidate(ArtifactId.of("com.example", "addon"), CURRENT,
				VersionSource.property("spring.version"));

		UpgradeReview review = new UpgradeReview(List.of(group, ungoverned), List.of());

		assertThat(review.getSharedPropertyPeers(group)).containsExactly(ungoverned);
		assertThat(review.getSharedPropertyPeers(ungoverned)).containsExactly(group);
	}

	private static UpgradeCandidate candidate(ArtifactId artifactId, ArtifactVersion version,
			VersionSource versionSource) {
		return candidate(artifactId, version, versionSource, site(artifactId, version, "review/pom.xml"));
	}

	private static UpgradeCandidate candidate(ArtifactId artifactId, ArtifactVersion version, Releases releases) {

		Dependency dependency = new Dependency(artifactId, version);
		dependency.addVersionSource(VersionSource.declared(version.toString()));
		return new UpgradeCandidate(new DependencyUpdateCandidate(dependency, releases), new TestInterfaceAssistant(),
				DeclaredVersions.from(List.of(site(artifactId, version, "review/pom.xml")), it -> null, null),
				new TestDependencyRule("Spring Framework"));
	}

	private static UpgradeCandidate candidate(ArtifactId artifactId, ArtifactVersion version,
			VersionSource versionSource, DeclarationSite... sites) {

		Dependency dependency = new Dependency(artifactId, version);
		dependency.addVersionSource(versionSource);
		Releases releases = Releases.of(Release.of(version), Release.of("6.2.1"));
		return new UpgradeCandidate(new DependencyUpdateCandidate(dependency, releases), new TestInterfaceAssistant(),
				DeclaredVersions.from(List.of(sites), it -> null, null), new TestDependencyRule("Spring Framework"));
	}

	private static DeclarationSite site(ArtifactId artifactId, ArtifactVersion version, String path) {
		return new DeclarationSite(new MockVirtualFile(path, "// test"), ProjectId.of("com.acme", "app"),
				new Dependency(artifactId, version));
	}

}
