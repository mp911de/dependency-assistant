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

import javax.swing.JTable;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.fixtures.TestDependencyRule;
import biz.paluch.dap.fixtures.TestInterfaceAssistant;
import biz.paluch.dap.state.ProjectId;
import com.intellij.icons.AllIcons;
import com.intellij.mock.MockVirtualFile;
import com.intellij.ui.SimpleColoredComponent;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.ListTableModel;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link DependencyCheckDialog.DependencyCoordinateColumn}.
 *
 * @author Mark Paluch
 */
class DependencyCoordinateColumnTests {

	ArtifactVersion CURRENT = ArtifactVersion.of("6.2.0");

	@Test
	void uniqueArtifactIdRendersBareLabel() {

		UpgradeCandidate driver = candidate(ArtifactId.of("org.postgresql", "postgresql"), null);
		UpgradeReview review = new UpgradeReview(List.of(driver), List.of());

		SimpleColoredComponent component = render(review, driver);

		assertThat(component.getCharSequence(false).toString()).isEqualTo("postgresql");
	}

	@Test
	void ambiguousArtifactIdAppendsGroupId() {

		UpgradeCandidate driver = candidate(ArtifactId.of("org.postgresql", "postgresql"), null);
		UpgradeCandidate testcontainer = candidate(ArtifactId.of("org.testcontainers", "postgresql"), null);
		UpgradeReview review = new UpgradeReview(List.of(driver, testcontainer), List.of());

		assertThat(render(review, driver).getCharSequence(false).toString())
				.isEqualTo("postgresql  (org.postgresql)");
		assertThat(render(review, testcontainer).getCharSequence(false).toString())
				.isEqualTo("postgresql  (org.testcontainers)");
	}

	@Test
	void groupRowShowsRuleNameWithMemberCount() {

		UpgradeGroup group = group();
		UpgradeReview review = new UpgradeReview(List.of(group), List.of());

		SimpleColoredComponent component = render(review, group);

		assertThat(component.getCharSequence(false).toString()).isEqualTo("Spring Framework  (2)");
	}

	@Test
	void groupTooltipListsMemberCoordinatesWithVersionSource() {

		UpgradeGroup group = group();
		UpgradeReview review = new UpgradeReview(List.of(group), List.of());

		SimpleColoredComponent component = render(review, group);

		assertThat(component.getToolTipText()).contains("org.springframework:spring-core")
				.contains("spring.version").contains("org.springframework:spring-test");
	}

	@Test
	void sharedPropertyRowsSwapIconAndCrossReferenceTooltips() {

		UpgradeCandidate core = candidate(ArtifactId.of("org.springframework", "spring-core"),
				VersionSource.property("spring.version"));
		UpgradeCandidate addon = candidate(ArtifactId.of("com.example", "addon"),
				VersionSource.property("spring.version"));
		UpgradeReview review = new UpgradeReview(List.of(core, addon), List.of());

		SimpleColoredComponent coreComponent = render(review, core);
		SimpleColoredComponent addonComponent = render(review, addon);

		assertThat(coreComponent.getIcon()).isEqualTo(AllIcons.Nodes.Shared);
		assertThat(coreComponent.getToolTipText()).contains("spring.version").contains("addon");
		assertThat(addonComponent.getToolTipText()).contains("spring.version").contains("spring-core");
	}

	@Test
	void sharedPropertyRowsUnderlineLabelWithWeakWarningWave() {

		UpgradeCandidate core = candidate(ArtifactId.of("org.springframework", "spring-core"),
				VersionSource.property("spring.version"));
		UpgradeCandidate addon = candidate(ArtifactId.of("com.example", "addon"),
				VersionSource.property("spring.version"));
		UpgradeCandidate lettuce = candidate(ArtifactId.of("io.lettuce", "lettuce-core"), null);
		UpgradeReview review = new UpgradeReview(List.of(core, addon, lettuce), List.of());

		assertThat(labelAttributes(render(review, core)).getStyle() & SimpleTextAttributes.STYLE_WAVED).isNotZero();
		assertThat(labelAttributes(render(review, addon)).getStyle() & SimpleTextAttributes.STYLE_WAVED).isNotZero();
		assertThat(labelAttributes(render(review, lettuce)).getStyle() & SimpleTextAttributes.STYLE_WAVED).isZero();
	}

	private static SimpleTextAttributes labelAttributes(SimpleColoredComponent component) {

		SimpleColoredComponent.ColoredIterator fragments = component.iterator();
		fragments.next();
		return fragments.getTextAttributes();
	}

	@Test
	void unrelatedRowKeepsTableIconAndPlainTooltip() {

		UpgradeCandidate driver = candidate(ArtifactId.of("org.postgresql", "postgresql"), null);
		UpgradeReview review = new UpgradeReview(List.of(driver), List.of());

		SimpleColoredComponent component = render(review, driver);

		assertThat(component.getIcon()).isEqualTo(driver.getTableIcon());
	}

	private static SimpleColoredComponent render(UpgradeReview review, UpgradeCandidate candidate) {

		DependencyCheckDialog.DependencyCoordinateColumn column = new DependencyCheckDialog.DependencyCoordinateColumn(
				review);
		ListTableModel<UpgradeCandidate> model = new ListTableModel<>(column);
		review.setHideUpToDate(false);
		model.setItems(review.getCandidates());

		int row = review.getCandidates().indexOf(candidate);
		return (SimpleColoredComponent) column.getRenderer(candidate).getTableCellRendererComponent(new JTable(model),
				candidate.getArtifactId(), false, false, row, 0);
	}

	private UpgradeGroup group() {

		UpgradeCandidate core = candidate(ArtifactId.of("org.springframework", "spring-core"),
				VersionSource.property("spring.version"));
		UpgradeCandidate test = candidate(ArtifactId.of("org.springframework", "spring-test"), null);
		return UpgradeGroup.of(List.of(core, test));
	}

	private UpgradeCandidate candidate(ArtifactId artifactId, @Nullable VersionSource versionSource) {

		Dependency dependency = new Dependency(artifactId, CURRENT);
		dependency.addDeclarationSource(DeclarationSource.dependency());
		dependency.addVersionSource(versionSource != null ? versionSource
				: VersionSource.declared(CURRENT.toString()));

		DeclarationSite site = new DeclarationSite(new MockVirtualFile("column/pom.xml", "// test"),
				ProjectId.of("com.acme", "app"), new Dependency(artifactId, CURRENT));
		Releases releases = Releases.of(Release.of(CURRENT), Release.of("6.2.1"));

		return new UpgradeCandidate(new DependencyUpdateCandidate(dependency, releases), new TestInterfaceAssistant(),
				DeclaredVersions.from(List.of(site), it -> null), new TestDependencyRule("Spring Framework"));
	}

}
