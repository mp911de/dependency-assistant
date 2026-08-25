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

package biz.paluch.dap.assistant.review;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.CoordinateShape;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.assistant.check.UpgradeGroup;
import biz.paluch.dap.assistant.check.VersionProperty;
import biz.paluch.dap.assistant.presentation.DependencyPresentation;
import biz.paluch.dap.assistant.presentation.IconDependencyPresentation;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.metadata.ProjectName;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import org.jspecify.annotations.Nullable;

/**
 * Dialog-row presentation that collapses the members of one
 * {@link UpgradeGroup} into a single selection.
 */
class GroupRow extends TableRow {

	private static final int MEMBER_LABEL_LIMIT = 25;

	private final UpgradeGroup group;

	private final String name;

	private @Nullable String renderedToolTip;

	private final String searchString;

	private final DependencyRuleEvaluator evaluator;

	private final String groupMembersOrCount;

	private final HtmlChunk toolTipIntro;

	private final List<HtmlChunk> toolTipSections;

	private final Set<VersionProperty> versionProperties = new HashSet<>();

	private GroupRow(String name, UpgradeGroup group) {

		super(createTableIcon(group.getUpgrade()));
		this.name = name;
		this.group = group;

		DependencyUpgradeCandidate merged = group.getUpgrade();

		this.evaluator = DependencyRuleEvaluator.create(merged.getRule(),
				getCurrentVersion());
		this.toolTipIntro = createToolTipIntro();
		this.toolTipSections = createGroupToolTipSections();

		List<String> artifactIds = new ArrayList<>();
		for (DependencyUpgradeCandidate member : group) {
			versionProperties.addAll(member.getVersionProperties());
			artifactIds.add(member.getArtifactId().artifactId());
		}

		String label = String.join(", ", CoordinateShape.of(artifactIds).memberLabelParts());
		this.groupMembersOrCount = !label.isEmpty() && label.length() <= MEMBER_LABEL_LIMIT ? label
				: String.valueOf(group.size());

		this.searchString = getSearchString(merged.getPresentation());
	}

	static GroupRow governed(String name, List<SingleTableRow> members) {
		return create(name, members);
	}

	static GroupRow inferred(String name, List<SingleTableRow> members) {
		return create(name, members);
	}

	private static GroupRow create(String name, List<SingleTableRow> members) {

		List<DependencyUpgradeCandidate> upgrades = members.stream()
				.flatMap(it -> it.getUpgradeCandidates().stream()).toList();
		UpgradeGroup group = UpgradeGroup.of(upgrades);
		return new GroupRow(name, group);
	}

	private HtmlChunk createToolTipIntro() {

		DependencyPresentation presentation = group.getUpgrade().getPresentation();
		ProjectName projectName = presentation.getProjectName();
		if (projectName.hasDisplayName()) {
			return new HtmlBuilder().append(HtmlChunk.text(projectName.getDisplayName()))
					.append(HtmlChunk.br()).toFragment();
		}
		return HtmlChunk.empty();
	}

	private List<HtmlChunk> createGroupToolTipSections() {

		HtmlBuilder name = new HtmlBuilder().append(HtmlChunk.text(getName()).code());
		IconDependencyPresentation presentation = group.getUpgrade().getPresentation();
		ProjectName projectName = presentation.getProjectName();

		if (projectName.hasProjectName()) {
			name.append(HtmlChunk.text(" (%s)".formatted(projectName.getProjectName())));
		}

		HtmlBuilder memberLines = new HtmlBuilder();
		memberLines.appendWithSeparators(HtmlChunk.br(),
				group.stream()
						.map(member -> HtmlChunk.text(member.getPresentation().getCoordinates()).code())
						.toList());

		return List.of(section("dialog.tooltip.group", name.toFragment()),
				section("dialog.tooltip.group.members", memberLines.toFragment()));
	}

	@Override
	public DependencyUpgradeCandidate getUpgrade() {
		return group.getUpgrade();
	}

	@Override
	public String getName() {
		return name;
	}

	@Override
	public String getSearchString() {
		return searchString;
	}

	@Override
	public String getDisplayName() {
		IconDependencyPresentation presentation = getUpgrade().getPresentation();
		if (presentation.hasDependencyName()) {
			return presentation.getDependencyName();
		}
		ProjectName projectName = presentation.getProjectName();
		if (projectName.hasDisplayName()) {
			return projectName.getDisplayName();
		}
		return getName();
	}

	@Override
	public List<DependencyUpgradeCandidate> getUpgradeCandidates() {
		return group.toList();
	}

	@Override
	public ArtifactVersion getCurrentVersion() {
		return group.getUpgrade().getCurrentVersion();
	}

	@Override
	public DeclaredVersions getDeclaredVersions() {
		return group.getUpgrade().getDeclaredVersions();
	}

	@Override
	public DependencyRule getRule() {
		return group.getUpgrade().getRule();
	}

	@Override
	public DependencyRuleEvaluator getRuleEvaluator() {
		return evaluator;
	}

	@Override
	public Vulnerabilities getVulnerabilities(ArtifactVersion version) {

		Vulnerabilities vulnerabilities = Vulnerabilities.clean();

		for (DependencyUpgradeCandidate candidate : group) {
			vulnerabilities = vulnerabilities.addAll(candidate.getVulnerabilities(version));
		}

		return vulnerabilities;
	}

	public String getMemberLabel() {
		return groupMembersOrCount;
	}

	@Override
	public Set<VersionProperty> getVersionProperties() {
		return versionProperties;
	}

	@Override
	protected HtmlChunk getToolTipIntro() {
		return toolTipIntro;
	}

	@Override
	public List<HtmlChunk> getCoordinateToolTip() {
		return toolTipSections;
	}

	@Override
	public boolean represents(PackageIdentity pkg) {
		return group.stream().anyMatch(member -> member.getPackageIdentity().equals(pkg));
	}

	@Override
	public void doWithUpgradeCandidates(Consumer<DependencyUpgradeCandidate> consumer) {
		for (DependencyUpgradeCandidate upgrade : group) {
			consumer.accept(upgrade);
		}
	}

}
