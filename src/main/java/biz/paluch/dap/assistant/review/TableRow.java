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
import biz.paluch.dap.assistant.check.VersionProperty;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.plan.PlannedUpgrade;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jspecify.annotations.Nullable;

/**
 * Dialog-row presentation over one upgrade aggregate.
 */
class TableRow implements HasArtifactId, HasPackageIdentity, PlannedUpgrade {

	private final DependencyUpgradeCandidate upgradeCandidate;

	private final DependencyRuleEvaluator evaluator;

	private final Icon tableIcon;

	private boolean labelByDependencyName;

	private final String renderedArtifactId;

	private final String rowName;

	private final String dependencyOrProjectName;

	private final HtmlChunk toolTipIntro;

	private final List<HtmlChunk> toolTipSections;

	private @Nullable String renderedToolTip;

	TableRow(DependencyUpgradeCandidate upgradeCandidate) {

		this.upgradeCandidate = upgradeCandidate;
		this.evaluator = DependencyRuleEvaluator.create(upgradeCandidate.getRule(),
				getCurrentVersion());
		this.renderedArtifactId = upgradeCandidate.getArtifactId().artifactId();

		String rowName = getRule().getDependencyName();
		if (StringUtils.isEmpty(rowName)) {
			rowName = renderedArtifactId;
		}
		else {
			labelByDependencyName();
		}
		this.rowName = rowName;

		IconDependencyPresentation presentation = upgradeCandidate.getPresentation();
		if (presentation.hasDependencyName()) {
			this.dependencyOrProjectName = presentation.getDependencyName();

		} else if (presentation.hasProjectName()) {
			this.dependencyOrProjectName = presentation.getProjectName();
		} else {
			this.dependencyOrProjectName = "";
		}

		this.tableIcon = createTableIcon();
		this.toolTipIntro = createToolTipIntro();
		this.toolTipSections = createToolTipSections();
	}

	private Icon createTableIcon() {

		Icon base = upgradeCandidate.getPresentation().getTableIcon();
		if (!upgradeCandidate.getDependency().hasPropertyVersion()) {
			return base;
		}

		return DependencyAssistantIcons.PROPERTY;
	}

	private HtmlChunk createToolTipIntro() {

		DependencyPresentation presentation = upgradeCandidate.getPresentation();
		if (presentation.hasProjectName()
				&& !presentation.getProjectName().equalsIgnoreCase(renderedArtifactId)) {
			return new HtmlBuilder().append(HtmlChunk.text(presentation.getProjectName()))
					.append(HtmlChunk.br()).toFragment();
		}
		return HtmlChunk.empty();
	}

	private List<HtmlChunk> createToolTipSections() {

		DependencyPresentation presentation = upgradeCandidate.getPresentation();
		Dependency dependency = upgradeCandidate.getDependency();

		boolean plugin = !dependency.getDeclarationSources().isEmpty()
				&& dependency.getDeclarationSources().iterator().next() instanceof DeclarationSource.Plugin;

		List<HtmlChunk> sections = new ArrayList<>();
		sections.add(section(plugin ? "dialog.tooltip.plugin" : "dialog.tooltip.coordinates",
				HtmlChunk.text(presentation.getArtifactIdDisplayName()).code()));

		if (!dependency.getDeclarationSources().isEmpty()
				&& dependency.getDeclarationSources().iterator().next() instanceof DeclarationSource.Profile profile) {
			sections.add(section("dialog.tooltip.profile",
					HtmlChunk.text(profile.getProfileId()).code()));
		}

		if (dependency.hasPropertyVersion()) {

			VersionSource.VersionProperty versionProperty = dependency.findPropertyVersion();
			sections.add(section("dialog.tooltip.property",
					HtmlChunk.text(String.valueOf(versionProperty)).code()));
			if (versionProperty instanceof VersionSource.Profile profile) {
				sections.add(section("dialog.tooltip.profile",
						HtmlChunk.text(profile.getProfileId()).code()));
			}
		}

		return sections;
	}

	@Override
	public PackageIdentity getPackageIdentity() {
		return upgradeCandidate.getPackageIdentity();
	}

	/**
	 * Return whether this row stands for the given artifact, used to select the row
	 * a gutter icon or documentation link points at.
	 *
	 * @param pkg the artifact to match.
	 * @return {@literal true} if the row represents the artifact; {@literal false}
	 * otherwise.
	 */
	public boolean represents(PackageIdentity pkg) {
		return getPackageIdentity().equals(pkg);
	}

	@Override
	public ArtifactId getArtifactId() {
		return upgradeCandidate.getArtifactId();
	}


	public DependencyUpgradeCandidate getUpgrade() {
		return upgradeCandidate;
	}

	@Override
	public List<DependencyUpgradeCandidate> getUpgradeCandidates() {
		return List.of(upgradeCandidate);
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
		return upgradeCandidate.getPresentation().getDisplayName();
	}

	public ArtifactVersion getCurrentVersion() {
		return upgradeCandidate.getCurrentVersion();
	}

	public DeclaredVersions getDeclaredVersions() {
		return upgradeCandidate.getDeclaredVersions();
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

	public Set<VersionProperty> getVersionProperties() {
		return upgradeCandidate.getVersionProperties();
	}

	public Icon getTableIcon() {
		return tableIcon;
	}

	public DependencyRule getRule() {
		return upgradeCandidate.getRule();
	}

	public DependencyRuleEvaluator getRuleEvaluator() {
		return evaluator;
	}

	public Vulnerabilities getVulnerabilities(ArtifactVersion version) {
		return upgradeCandidate.getVulnerabilities(version);
	}

	public VersionStatus getStatus(ArtifactVersion version) {
		return VersionStatus.of(DependencyRuleEvaluator.create(getRule(), version),
				getCurrentVersion(), version, getVulnerabilities(version));
	}

	public String getSearchString() {
		return getArtifactId() + " " + getDependencyName() + " " + getName();
	}

	/**
	 * The headline rendered above the section table, or {@link HtmlChunk#empty()}.
	 */
	protected HtmlChunk getToolTipIntro() {
		return toolTipIntro;
	}

	/**
	 * The label/value section rows of this row's tooltip, assembled and rendered by
	 * {@link UpgradeReview#getToolTip(TableRow)}.
	 */
	public List<HtmlChunk> getToolTip() {
		return toolTipSections;
	}

	public @Nullable String getToolTipText() {

		if (renderedToolTip == null) {

			HtmlBuilder tooltip = new HtmlBuilder();
			DeclaredVersions declaredVersions = getDeclaredVersions();

			if (declaredVersions.hasVersionDrift()) {
				tooltip.append(declaredVersions.getVersionDriftToolTip(getCurrentVersion()));
			}

			if (getRule().isPresent()) {
				tooltip.append(evaluator.getToolTipText(getUpgrade().getPresentation()));
			}

			if (tooltip.isEmpty()) {
				renderedToolTip = "";
			} else {
				renderedToolTip = tooltip.wrapWith("html").toString();
			}
		}

		return renderedToolTip;
	}

	public DependencySiteQuery toQuery() {

		List<String> versionPropertyNames = getVersionProperties().stream().map(VersionProperty::property)
				.toList();
		return DependencySiteQuery
				.create(it -> it.artifact(getArtifactId()).versionProperties(versionPropertyNames));
	}

	public void doWithRow(Consumer<TableRow> consumer) {
		consumer.accept(this);
	}

	@Override
	public String toString() {
		return (rowName) + "@" + getCurrentVersion() + " -> ["
				+ upgradeCandidate.getDisplayReleases() + "]";
	}

	/**
	 * Render one label/value row in {@link DocumentationMarkup} section style.
	 * Swing tooltips do not carry the documentation pane's stylesheet, so the
	 * {@code section} class is inert and the label styling is inlined: context-help
	 * gray plus a right padding separating the label column from the value column.
	 */
	static HtmlChunk section(String labelKey, HtmlChunk value) {

		String labelStyle = "color: %s; padding-right: %dpx".formatted(
				ColorUtil.toHtmlColor(UIUtil.getContextHelpForeground()), JBUI.scale(8));
		return HtmlChunk.tag("tr").children(
				DocumentationMarkup.SECTION_HEADER_CELL.style(labelStyle)
						.addText(MessageBundle.message(labelKey)),
				DocumentationMarkup.SECTION_CONTENT_CELL.child(value));
	}

}
