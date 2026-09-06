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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.assistant.VersionStatus;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.assistant.check.VersionProperty;
import biz.paluch.dap.assistant.presentation.DependencyPresentation;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.metadata.ProjectName;
import biz.paluch.dap.plan.PlannedUpgrade;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Dialog-row presentation for either one upgrade candidate or one grouped
 * selection.
 *
 * <p>A row supplies the display and tooltip model while retaining the member
 * candidates needed for dependency-site queries and update fan-out.
 *
 * @author Mark Paluch
 */
abstract class TableRow implements PlannedUpgrade, Comparable<TableRow> {

	private final Icon tableIcon;

	private @Nullable String renderedCurrentVersionToolTipText;

	public TableRow(Icon tableIcon) {
		this.tableIcon = tableIcon;
	}

	static Icon createTableIcon(DependencyUpgradeCandidate candidate) {

		Icon base = candidate.getPresentation().getTableIcon();
		if (!candidate.getDependency().hasPropertyVersion()) {
			return base;
		}

		return DependencyAssistantIcons.PROPERTY;
	}

	protected String getSearchString(DependencyPresentation presentation) {

		Set<String> searchString = new LinkedHashSet<>();
		searchString.add(getName());
		searchString.add(presentation.getShortArtifactId());
		searchString.add(presentation.getCoordinates());

		if (presentation.hasDependencyName()) {
			searchString.add(presentation.getDependencyName());
		}

		ProjectName projectName = presentation.getProjectName();
		if (projectName.hasDisplayName()) {
			searchString.add(projectName.getDisplayName());
		} else if (projectName.hasProjectName()) {
			searchString.add(projectName.getProjectName());
		}
		return String.join(" ", searchString);
	}

	public Icon getTableIcon() {
		return tableIcon;
	}

	public abstract DependencyUpgradeCandidate getUpgrade();

	public abstract String getName();

	public abstract String getSearchString();

	public abstract ArtifactVersion getCurrentVersion();

	public abstract DeclaredVersions getDeclaredVersions();

	public Set<String> getVersionPropertyNames() {
		Set<String> names = new LinkedHashSet<>();
		getVersionProperties().forEach(it -> names.add(it.property()));
		return names;
	}

	public abstract Set<VersionProperty> getVersionProperties();

	public abstract DependencyRule getRule();

	public abstract DependencyRuleEvaluator getRuleEvaluator();

	public abstract Vulnerabilities getVulnerabilities(ArtifactVersion version);

	public VersionStatus getStatus(ArtifactVersion version) {
		return VersionStatus.of(DependencyRuleEvaluator.create(getRule(), version),
				getCurrentVersion(), version, getVulnerabilities(version));
	}

	/**
	 * Return the tooltip headline, or {@link HtmlChunk#empty()}.
	 */
	protected abstract HtmlChunk getToolTipIntro();

	/**
	 * Return tooltip sections for
	 * {@link UpgradeReview#getCoordinateToolTip(TableRow)}.
	 */
	public abstract List<HtmlChunk> getCoordinateToolTip();

	/**
	 * Return current-version details, or an empty string without drift or rule
	 * details.
	 */
	public String getCurrentVersionToolTipText() {

		if (renderedCurrentVersionToolTipText == null) {
			HtmlBuilder tooltip = new HtmlBuilder();
			DeclaredVersions declaredVersions = getDeclaredVersions();

			if (declaredVersions.hasVersionDrift()) {
				tooltip.append(declaredVersions.getVersionDriftToolTip(getCurrentVersion()));
			}

			if (getRule().isPresent()) {
				tooltip.append(getRuleEvaluator().getToolTipText(getName()));
			}

			if (tooltip.isEmpty()) {
				renderedCurrentVersionToolTipText = "";
			} else {
				renderedCurrentVersionToolTipText = tooltip.wrapWith("html").toString();
			}
		}

		return renderedCurrentVersionToolTipText;
	}

	@Override
	public int compareTo(TableRow o) {
		return getName().compareToIgnoreCase(o.getName());
	}

	/**
	 * Return whether this row represents the package, including group members.
	 */
	public abstract boolean represents(PackageIdentity pkg);

	/**
	 * Create a dependency-site query covering member artifacts and version
	 * properties.
	 */
	public DependencySiteQuery toQuery() {

		List<String> versionPropertyNames = getVersionProperties().stream().map(VersionProperty::property)
				.toList();

		List<ArtifactId> artifactIds = new ArrayList<>();
		doWithUpgradeCandidates(it -> {
			artifactIds.add(it.getArtifactId());
		});
		return DependencySiteQuery
				.create(it -> it.artifacts(artifactIds).versionProperties(versionPropertyNames));
	}

	public abstract void doWithUpgradeCandidates(Consumer<DependencyUpgradeCandidate> consumer);

	static HtmlChunk section(String labelKey, HtmlChunk value) {

		// Swing tooltips lack the documentation stylesheet, so label styling must be
		// inline.
		String labelStyle = "color: %s; padding-right: %dpx".formatted(
				ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()), JBUI.scale(8));
		return HtmlChunk.tag("tr").children(
				DocumentationMarkup.SECTION_HEADER_CELL.style(labelStyle)
						.addText(MessageBundle.message(labelKey)),
				DocumentationMarkup.SECTION_CONTENT_CELL.child(value));
	}

}
