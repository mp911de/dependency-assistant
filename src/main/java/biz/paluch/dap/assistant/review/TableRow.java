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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.DependencyPresentation;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.IconDependencyPresentation;
import biz.paluch.dap.assistant.VersionStatus;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.plan.PlannedUpgrade;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.util.text.StringUtil;

/**
 * Dialog-row presentation over one upgrade aggregate.
 */
class TableRow implements HasArtifactId, HasPackageIdentity, PlannedUpgrade {

	private final DependencyUpgradeCandidate upgrade;

	private final DependencyRuleEvaluator evaluator;

	private final Icon tableIcon;

	private boolean labelByDependencyName;

	private final String renderedArtifactId;

	private final String toolTipText;

	private final String rowName;

	private final String dependencyOrProjectName;

	TableRow(DependencyUpgradeCandidate upgrade) {

		this.upgrade = upgrade;
		this.evaluator = DependencyRuleEvaluator.create(upgrade.getRule(), getArtifactId(),
				getCurrentVersion());
		this.renderedArtifactId = upgrade.getArtifactId().artifactId();

		String rowName = getRule().getDependencyName();
		if (StringUtils.isEmpty(rowName)) {
			rowName = renderedArtifactId;
		}
		else {
			labelByDependencyName();
		}
		this.rowName = rowName;

		IconDependencyPresentation presentation = upgrade.getPresentation();
		if (presentation.hasDependencyName()) {
			this.dependencyOrProjectName = presentation.getDependencyName();

		} else if (presentation.hasProjectName()) {
			this.dependencyOrProjectName = presentation.getProjectName();
		} else {
			this.dependencyOrProjectName = "";
		}

		this.tableIcon = createTableIcon();
		this.toolTipText = createToolTipText();
	}

	private String createToolTipText() {

		DependencyPresentation presentation = upgrade.getPresentation();
		Dependency dependency = upgrade.getDependency();
		String tooltip = presentation.hasProjectName()
				&& !presentation.getProjectName().equalsIgnoreCase(renderedArtifactId)
						? (StringUtil.escapeXmlEntities(presentation.getProjectName()) + "<br/>")
						: "";

		tooltip += MessageBundle.message("dialog.tooltip.coordinates",
				StringUtil.escapeXmlEntities(presentation.getArtifactIdDisplayName()));

		if (!dependency.getDeclarationSources().isEmpty()
				&& dependency.getDeclarationSources().iterator().next() instanceof DeclarationSource.Plugin) {
			tooltip = MessageBundle.message("dialog.tooltip.plugin", tooltip);
		}

		if (dependency.hasPropertyVersion()) {
			VersionSource.VersionProperty versionProperty = dependency.findPropertyVersion();
			tooltip += "<br/>"
					+ MessageBundle.message("dialog.tooltip.property", versionProperty);
			if (versionProperty instanceof VersionSource.Profile profile) {
				tooltip += "<br/>" + MessageBundle.message("dialog.tooltip.profile",
						profile.getProfileId());
			}
		}

		if (!dependency.getDeclarationSources().isEmpty()
				&& dependency.getDeclarationSources().iterator().next() instanceof DeclarationSource.Profile profile) {
			tooltip += "<br/>"
					+ MessageBundle.message("dialog.tooltip.profile", profile.getProfileId());
		}

		return tooltip;
	}

	private Icon createTableIcon() {

		Icon base = upgrade.getPresentation().getTableIcon();
		if (!upgrade.getDependency().hasPropertyVersion()) {
			return base;
		}

		return DependencyAssistantIcons.PROPERTY;
	}

	public DeclaredVersions getDeclaredVersions() {
		return upgrade.getDeclaredVersions();
	}

	@Override
	public PackageIdentity getPackageIdentity() {
		return upgrade.getPackageIdentity();
	}

	@Override
	public ArtifactId getArtifactId() {
		return upgrade.getArtifactId();
	}

	public ArtifactVersion getCurrentVersion() {
		return upgrade.getCurrentVersion();
	}

	public DependencyUpgradeCandidate getUpgrade() {
		return upgrade;
	}

	public Icon getTableIcon() {
		return tableIcon;
	}

	public DependencyRule getRule() {
		return upgrade.getRule();
	}

	public DependencyRuleEvaluator getRuleEvaluator() {
		return evaluator;
	}

	public Vulnerabilities getVulnerabilities(ArtifactVersion version) {
		return upgrade.getVulnerabilities(version);
	}

	public VersionStatus getStatus(ArtifactVersion version) {
		return VersionStatus.of(evaluate(version), getCurrentVersion(), version, getVulnerabilities(version));
	}

	public String getName() {
		return labelByDependencyName ? rowName : renderedArtifactId;
	}

	String getDependencyOrProjectName() {
		return dependencyOrProjectName;
	}

	public void labelByDependencyName() {
		this.labelByDependencyName = true;
	}

	public boolean isLabeledByDependencyName() {
		return labelByDependencyName;
	}

	public String getDependencyName() {
		return upgrade.getPresentation().getDisplayName();
	}

	public String getToolTipText() {
		return toolTipText;
	}

	public Set<String> getVersionPropertyNames() {

		Set<String> names = new LinkedHashSet<>();

		doWithRow(it -> {
			if (it.getUpgrade().getDependency()
					.findPropertyVersion() instanceof VersionSource.VersionProperty property) {
				names.add(property.getProperty());
			}
		});
		return names;
	}

	public DependencySiteQuery toQuery() {
		return DependencySiteQuery
				.create(it -> it.artifact(getArtifactId()).versionProperties(getVersionPropertyNames()));
	}

	public List<DependencyUpdate> createUpdates(ArtifactVersion target) {

		Dependency dependency = upgrade.getDependency();
		ArtifactId artifactId = new FriendlyArtifactId(dependency.getArtifactId(), getName());
		return List.of(upgrade.createUpdate(artifactId, target));
	}

	public DependencyRuleEvaluator evaluate(ArtifactVersion version) {
		return DependencyRuleEvaluator.create(getRule(), getArtifactId(), version);
	}

	@Override
	public List<DependencyUpgradeCandidate> getUpgrades() {
		return List.of(upgrade);
	}

	public void doWithRow(Consumer<TableRow> consumer) {
		consumer.accept(this);
	}

	public String getSearchString() {
		return getArtifactId() + " " + getDependencyName() + " " + getName();
	}

	@Override
	public String toString() {
		return (rowName) + "@" + getCurrentVersion() + " -> ["
				+ upgrade.getDisplayReleases() + "]";
	}

	/**
	 * Artifact identity rendering a friendly dependency name for notifications.
	 */
	private static class FriendlyArtifactId implements ArtifactId {

		private final ArtifactId id;

		private final String friendlyName;

		FriendlyArtifactId(ArtifactId id, String friendlyName) {
			this.id = id;
			this.friendlyName = friendlyName;
		}

		@Override
		public boolean equals(Object other) {
			return id.equals(other instanceof FriendlyArtifactId friendly ? friendly.id : other);
		}

		@Override
		public int hashCode() {
			return id.hashCode();
		}

		@Override
		public String groupId() {
			return id.groupId();
		}

		@Override
		public String artifactId() {
			return id.artifactId();
		}

		@Override
		public String toString() {
			return friendlyName;
		}

	}

}
