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
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.assistant.check.UpgradeGroup;
import biz.paluch.dap.assistant.check.VersionProperty;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import org.jspecify.annotations.Nullable;

/**
 * Dialog-row presentation collapsing the members of one upgrade group.
 */
class GroupRow extends TableRow {

	private static final int MEMBER_LABEL_LIMIT = 25;

	private final List<TableRow> members;

	private final List<DependencyUpgradeCandidate> upgrades = new ArrayList<>();

	private final @Nullable String name;

	private final String groupMembersOrCount;

	private final List<HtmlChunk> toolTipSections;

	private final Set<VersionProperty> versionProperties = new HashSet<>();

	private GroupRow(UpgradeGroup group, List<TableRow> members, @Nullable String name) {

		super(group.getUpgrade());
		this.members = members;
		this.name = name;
		if (name == null) {
			labelByDependencyName();
		}
		this.toolTipSections = createGroupToolTipSections();

		List<String> artifactIds = new ArrayList<>();
		for (TableRow member : members) {
			versionProperties.addAll(member.getVersionProperties());
			upgrades.add(member.getUpgrade());
			artifactIds.add(member.getArtifactId().artifactId());
		}
		String label = String.join(", ", CoordinateShape.of(artifactIds).memberLabelParts());
		this.groupMembersOrCount = !label.isEmpty() && label.length() <= MEMBER_LABEL_LIMIT ? label
				: String.valueOf(members.size());
	}

	static GroupRow governed(List<TableRow> members) {
		return create(members, null);
	}

	static GroupRow governed(TableRow... members) {
		return governed(List.of(members));
	}

	static GroupRow inferred(List<TableRow> members, String displayName) {
		return create(members, displayName);
	}

	private static GroupRow create(List<TableRow> members, @Nullable String derivedLabel) {

		List<DependencyUpgradeCandidate> upgrades = members.stream().map(TableRow::getUpgrade).toList();
		UpgradeGroup group = UpgradeGroup.of(upgrades);
		return new GroupRow(group, members, derivedLabel);
	}

	private List<HtmlChunk> createGroupToolTipSections() {

		HtmlBuilder name = new HtmlBuilder().append(HtmlChunk.text(getName()).code());
		if (StringUtils.hasText(getDependencyOrProjectName()) && !getName().equals(getDependencyOrProjectName())) {
			name.append(HtmlChunk.text(" (%s)".formatted(getDependencyOrProjectName())));
		}

		HtmlBuilder memberLines = new HtmlBuilder();
		memberLines.appendWithSeparators(HtmlChunk.br(),
				members.stream().map(member -> HtmlChunk.text(member.getArtifactId().toString()).code()).toList());

		return List.of(section("dialog.tooltip.group", name.toFragment()),
				section("dialog.tooltip.group.members", memberLines.toFragment()));
	}

	@Override
	public List<DependencyUpgradeCandidate> getUpgradeCandidates() {
		return upgrades;
	}

	public List<TableRow> getMembers() {
		return members;
	}

	/**
	 * A group row also stands for each of its members.
	 */
	@Override
	public boolean represents(PackageIdentity pkg) {
		return super.represents(pkg) || members.stream().anyMatch(member -> member.represents(pkg));
	}

	@Override
	public String getName() {
		return name != null ? name : super.getName();
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
		return HtmlChunk.empty();
	}

	@Override
	public List<HtmlChunk> getToolTip() {
		return toolTipSections;
	}

	@Override
	public DependencySiteQuery toQuery() {
		return DependencySiteQuery.union(members.stream().map(TableRow::toQuery).toList());
	}

	@Override
	public void doWithRow(Consumer<TableRow> consumer) {
		for (TableRow member : members) {
			consumer.accept(member);
		}
	}

}
