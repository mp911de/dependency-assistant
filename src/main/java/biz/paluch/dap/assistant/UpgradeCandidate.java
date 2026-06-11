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

import javax.swing.Icon;

import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.Generation;
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
class UpgradeCandidate implements HasArtifactId {

	private final DependencyUpdateCandidate candidate;

	private final InterfaceAssistant interfaceAssistant;

	private final DeclaredVersions declaredVersions;

	private final DependencyRule rule;

	private final EvaluatedDependencyRule ruleResult;

	private final Icon tableIcon;

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
	}

	private void filterUpgrades() {

		Releases releases = this.candidate.getReleases();
		Release target = null;

		for (Generation generation : this.rule.getGenerations().list()) {

			ArtifactVersion baseline = ArtifactVersion.of(generation.value());
			for (UpgradeStrategy strategy : UpgradeStrategy.values()) {
				if (!this.rule.isEnabled(strategy)) {
					continue;
				}
				Release release = strategy.select(baseline, releases);
				if (release != null && this.rule.test(release.version())
						&& (target == null || release.compareTo(target) > 0)) {
					target = release;
				}
			}
		}

		if (target != null) {
			this.candidate.getTargets().put(UpgradeStrategy.RULE, target);
			this.candidate.getFilteredTargets().put(UpgradeStrategy.RULE, target);
		}
	}

	private Icon createTableIcon() {

		Icon base = interfaceAssistant.getTableIcon(getUpdateCandidate().getDependency());
		if (base == null) {
			return AllIcons.Nodes.Library;
		}

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

	public String getDependencyName() {
		String name = getRule().getDependencyName();
		if (StringUtils.isEmpty(name)) {
			name = interfaceAssistant.getDisplayName(getArtifactId());
		}
		return name;
	}

	@Override
	public String toString() {
		return getArtifactId() + "@" + getCurrentVersion();
	}

}
