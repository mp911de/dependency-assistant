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
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.assistant.check.UpgradeGroup;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.util.MessageBundle;
import org.jspecify.annotations.Nullable;

/**
 * Dialog-row presentation collapsing the members of one upgrade group.
 */
class GroupRow extends TableRow {

	private static final int MEMBER_LABEL_LIMIT = 25;

	private final List<TableRow> members;

	private final @Nullable String derivedLabel;

	private final String memberLabel;

	private final String toolTipText;

	private GroupRow(UpgradeGroup group, List<TableRow> members, @Nullable String derivedLabel) {

		super(group.getUpgrade());
		this.members = members;
		this.derivedLabel = derivedLabel;
		if (derivedLabel == null) {
			labelByDependencyName();
		}
		this.toolTipText = createGroupToolTipText();

		List<String> artifactIds = members.stream().map(member -> member.getArtifactId().artifactId()).toList();
		String label = String.join(", ", CoordinateShape.of(artifactIds).memberLabelParts());
		this.memberLabel = !label.isEmpty() && label.length() <= MEMBER_LABEL_LIMIT ? label
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

	private String createGroupToolTipText() {

		StringBuilder tooltip = new StringBuilder();
		tooltip.append("<b>")
				.append(MessageBundle.message("dialog.tooltip.group.header", getName()))
				.append("</b><ul>");
		for (TableRow member : members) {
			tooltip.append("<li><code>").append(member.getArtifactId()).append("</code></li>");
		}
		return tooltip.append("</ul>").toString();
	}

	@Override
	public String getName() {
		return derivedLabel != null ? derivedLabel : super.getName();
	}

	@Override
	public String getToolTipText() {
		return toolTipText;
	}

	@Override
	public void doWithRow(Consumer<TableRow> consumer) {
		for (TableRow member : members) {
			consumer.accept(member);
		}
	}

	@Override
	public List<DependencyUpgradeCandidate> getUpgrades() {
		return getMembers().stream().flatMap(it -> it.getUpgrades().stream()).toList();
	}

	public List<TableRow> getMembers() {
		return members;
	}

	public String getMemberLabel() {
		return memberLabel;
	}

	@Override
	public DependencySiteQuery toQuery() {
		return DependencySiteQuery.union(members.stream().map(TableRow::toQuery).toList());
	}

	@Override
	public List<DependencyUpdate> createUpdates(ArtifactVersion target) {

		List<DependencyUpdate> updates = new ArrayList<>(members.size());
		for (TableRow member : members) {
			updates.addAll(member.createUpdates(target));
		}
		return updates;
	}

}
