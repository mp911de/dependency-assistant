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

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.swing.Icon;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.icons.AllIcons;
import com.intellij.ui.LayeredIcon;
import com.intellij.util.IconUtil;

/**
 * User-interface upgrade candidate enriched with assistant context and
 * version-drift details.
 *
 * @author Mark Paluch
 */
public class UpgradeCandidate implements HasArtifactId {

	private final DependencyUpdateCandidate candidate;

	private final InterfaceAssistant interfaceAssistant;

	private final DeclaredVersions declaredVersions;

	private final DependencyRule rule;

	private final EvaluatedDependencyRule ruleResult;

	private final Icon tableIcon;

	private boolean labelByDependencyName;

	private final String toolTipText;

	/**
	 * Create an update candidate.
	 *
	 * @param candidate the dependency update option to offer.
	 * @param assistant the format-specific assistant that can apply the update.
	 * @param declaredVersions the versions the artifact is declared at, used for
	 * drift reporting.
	 * @param rule the associated dependency rule.
	 */
	public UpgradeCandidate(DependencyUpdateCandidate candidate, InterfaceAssistant assistant,
			DeclaredVersions declaredVersions, DependencyRule rule) {

		this.candidate = candidate;
		this.interfaceAssistant = assistant;
		this.declaredVersions = declaredVersions;
		this.rule = rule;

		if (rule.isPresent()) {
			for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
				if (!rule.isEnabled(strategy)) {
					candidate.getFilteredTargets().remove(strategy);
				}
			}

			if (!rule.test(candidate.getCurrentVersion())) {
				filterUpgrades();
			}
		}

		this.ruleResult = EvaluatedDependencyRule.of(rule, getArtifactId(), getCurrentVersion(), assistant);
		this.tableIcon = createTableIcon();
		this.toolTipText = createToolTipText();
	}

	private String createToolTipText() {

		String artifactId = getArtifactId().toString();
		String tooltip = artifactId;
		DependencyUpdateCandidate updateCandidate = getUpdateCandidate();
		Dependency dependency = candidate.getDependency();
		if (updateCandidate.hasPropertyVersion()) {
			VersionSource.VersionProperty versionProperty = updateCandidate.getPropertyVersion();
			tooltip = MessageBundle.message("dialog.tooltip.property", "<code>" + versionProperty + "</code>");
			if (versionProperty instanceof VersionSource.Profile pps) {
				tooltip += "<br/>" + MessageBundle.message("dialog.tooltip.profile",
						"<code>" + pps.getProfileId() + "</code>");
			}
		}

		if (!dependency.getDeclarationSources().isEmpty()
				&& candidate.getDeclarationSource() instanceof DeclarationSource.Plugin) {
			tooltip += "<br/>" + MessageBundle.message("dialog.tooltip.plugin", artifactId);
		}

		if (!dependency.getDeclarationSources().isEmpty()
				&& candidate.getDeclarationSource() instanceof DeclarationSource.Profile profile) {
			tooltip += MessageBundle.message("dialog.tooltip.profile",
					"<code>" + profile.getProfileId() + "</code>");
		}

		return tooltip;
	}

	private void filterUpgrades() {

		Releases releases = this.candidate.getReleases();
		Release target = rule.suggestRemediation(releases);

		if (target != null) {
			this.candidate.getTargets().put(UpgradeStrategy.RULE, target);
			this.candidate.getFilteredTargets().put(UpgradeStrategy.RULE, target);
		}
	}

	private Icon createTableIcon() {

		Icon base = interfaceAssistant.getTableIcon(getUpdateCandidate().getDependency());

		if (getUpdateCandidate().hasPropertyVersion()) {

			int pad = 0;
			int bw = base.getIconWidth();
			int bh = base.getIconHeight();

			LayeredIcon layered = new LayeredIcon(2);
			layered.setIcon(base, 0);
			Icon propertySmall = IconUtil.scale(AllIcons.Nodes.Property, null, 0.5f);

			int ow = propertySmall.getIconWidth();
			int oh = propertySmall.getIconHeight();
			layered.setIcon(propertySmall, 1, Math.max(0, bw - ow - pad), Math.max(0, bh - oh - pad));
			return layered;
		}

		return base;
	}

	/**
	 * Return the declared versions for this candidate, carrying drift information.
	 *
	 * @return the declared versions associated with the candidate.
	 */
	public DeclaredVersions getDeclaredVersions() {
		return declaredVersions;
	}

	/**
	 * Return the format-specific assistant that can apply this candidate.
	 *
	 * @return the assistant identifying the candidate's build ecosystem.
	 */
	InterfaceAssistant getInterfaceAssistant() {
		return interfaceAssistant;
	}

	@Override
	public ArtifactId getArtifactId() {
		return candidate.getArtifactId();
	}

	/**
	 * Return the currently declared artifact version.
	 *
	 * @return the current version from the wrapped update option.
	 */
	public ArtifactVersion getCurrentVersion() {
		return candidate.getCurrentVersion();
	}

	/**
	 * Return the wrapped dependency update option.
	 *
	 * @return the update option used to render and apply the candidate.
	 */
	public DependencyUpdateCandidate getUpdateCandidate() {
		return candidate;
	}

	public Icon getTableIcon() {
		return tableIcon;
	}

	public DependencyRule getRule() {
		return rule;
	}

	public EvaluatedDependencyRule getRuleResult() {
		return ruleResult;
	}

	/**
	 * Return the label shown in the dialog's Dependency column: the artifactId, or
	 * the rule's dependency name once {@link #labelByDependencyName()} was applied.
	 *
	 * @return the row label for the Dependency column.
	 */
	public String getRowLabel() {
		return labelByDependencyName ? getDependencyName() : getArtifactId().artifactId();
	}

	/**
	 * Label this row with the rule's dependency name instead of the artifactId.
	 * Applied when the rule name is not claimed by any other row.
	 */
	void labelByDependencyName() {
		this.labelByDependencyName = true;
	}

	/**
	 * Return whether this row is labeled with the rule's dependency name rather
	 * than the artifactId.
	 */
	boolean isLabeledByDependencyName() {
		return labelByDependencyName;
	}

	public String getDependencyName() {
		String name = getRule().getDependencyName();
		if (StringUtils.isEmpty(name)) {
			name = interfaceAssistant.getDisplayName(getArtifactId());
		}
		return name;
	}

	public String getToolTipText() {
		return this.toolTipText;
	}

	/**
	 * Return the bare property names backing this candidate's declared version.
	 * Property identity deliberately ignores profile and module scoping.
	 *
	 * @return the bare version property names; empty for inline declarations.
	 */
	Set<String> getVersionPropertyNames() {

		Set<String> names = new LinkedHashSet<>();
		for (VersionSource source : getUpdateCandidate().getDependency().getVersionSources()) {
			if (source instanceof VersionSource.VersionProperty property) {
				names.add(property.getProperty());
			}
		}

		return names;
	}

	/**
	 * Derive the {@link DependencySiteQuery} this row is centered on for a
	 * Dependency Site Find: its artifact and the bare version properties backing
	 * its version.
	 *
	 * @return the query for this candidate; never {@literal null}.
	 */
	DependencySiteQuery toQuery() {
		return DependencySiteQuery
				.create(it -> it.artifact(getArtifactId()).versionProperties(getVersionPropertyNames()));
	}

	/**
	 * Create the updates to apply for the chosen target version. The update
	 * coordinate renders the row's dependency name so notifications can show a
	 * friendly label.
	 *
	 * @param target the confirmed target version.
	 * @return one update for this candidate's coordinate.
	 */
	public List<DependencyUpdate> createUpdates(ArtifactVersion target) {

		Dependency dependency = getUpdateCandidate().getDependency();
		FriendlyArtifactId artifactId = new FriendlyArtifactId(dependency.getArtifactId(), getDependencyName());
		return List.of(DependencyUpdate.from(artifactId, dependency, target));
	}

	/**
	 * Evaluate the rule associated with this candidate against the given version.
	 * @param version the version to evaluate.
	 * @return the evaluation outcome.
	 */
	public EvaluatedDependencyRule evaluate(ArtifactVersion version) {
		return EvaluatedDependencyRule.of(getRule(), getArtifactId(), version, interfaceAssistant);
	}

	@Override
	public String toString() {
		return (labelByDependencyName ? getDependencyName() : getArtifactId()) + "@" + getCurrentVersion() + " -> ["
				+ getUpdateCandidate().getFilteredReleases() + "]";
	}

	/**
	 * Artifact identity rendering a friendly dependency name for notifications.
	 */
	private record FriendlyArtifactId(ArtifactId id, String friendlyName) implements ArtifactId {

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
