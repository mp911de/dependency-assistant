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
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.HasPackageIdentity;
import biz.paluch.dap.artifact.HasPackageSystem;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.assistant.check.VersionProperty;
import biz.paluch.dap.assistant.presentation.DependencyPresentation;
import biz.paluch.dap.assistant.presentation.IconDependencyPresentation;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.lookup.DependencySiteQuery;
import biz.paluch.dap.metadata.ProjectName;
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
 * Dialog-row presentation for one {@link DependencyUpgradeCandidate}.
 */
class SingleTableRow extends TableRow implements HasArtifactId, HasPackageIdentity, HasPackageSystem {

	private final DependencyUpgradeCandidate upgradeCandidate;

	private final IconDependencyPresentation presentation;

	private final String name;

	private @Nullable String renderedToolTip;

	private final String searchString;

	private final DependencyRuleEvaluator evaluator;

	private final HtmlChunk toolTipIntro;

	private final List<HtmlChunk> toolTipSections;

	SingleTableRow(DependencyUpgradeCandidate upgradeCandidate) {

		super(createTableIcon(upgradeCandidate));

		this.upgradeCandidate = upgradeCandidate;
		this.presentation = upgradeCandidate.getPresentation();
		this.evaluator = DependencyRuleEvaluator.create(upgradeCandidate.getRule(),
				getCurrentVersion());

		this.name = this.presentation.hasDependencyName() ? this.presentation.getDependencyName()
				: presentation.getShortArtifactId();

		this.toolTipIntro = createToolTipIntro();
		this.toolTipSections = createToolTipSections();
		this.searchString = getSearchString(presentation);
	}

	private HtmlChunk createToolTipIntro() {

		DependencyPresentation presentation = upgradeCandidate.getPresentation();
		ProjectName projectName = presentation.getProjectName();
		if (projectName.hasDisplayName()) {
			return new HtmlBuilder().append(HtmlChunk.text(projectName.getDisplayName()))
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
				HtmlChunk.text(presentation.getCoordinates()).code()));

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
	public DependencyUpgradeCandidate getUpgrade() {
		return upgradeCandidate;
	}

	public String getName() {
		return name;
	}

	public String getSearchString() {
		return searchString;
	}

	@Override
	public ArtifactId getArtifactId() {
		return upgradeCandidate.getArtifactId();
	}

	@Override
	public PackageIdentity getPackageIdentity() {
		return upgradeCandidate.getPackageIdentity();
	}

	@Override
	public PackageSystem getPackageSystem() {
		return getPackageIdentity().getPackageSystem();
	}

	@Override
	public String getDisplayName() {
		IconDependencyPresentation presentation = upgradeCandidate.getPresentation();
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
		return List.of(upgradeCandidate);
	}

	public ArtifactVersion getCurrentVersion() {
		return upgradeCandidate.getCurrentVersion();
	}

	public DeclaredVersions getDeclaredVersions() {
		return upgradeCandidate.getDeclaredVersions();
	}

	public Set<VersionProperty> getVersionProperties() {
		return upgradeCandidate.getVersionProperties();
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

	/**
	 * The headline rendered above the section table, or {@link HtmlChunk#empty()}.
	 */
	protected HtmlChunk getToolTipIntro() {
		return toolTipIntro;
	}

	public List<HtmlChunk> getCoordinateToolTip() {
		return toolTipSections;
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
		return upgradeCandidate.getPackageIdentity().equals(pkg);
	}

	@Override
	public void doWithUpgradeCandidates(Consumer<DependencyUpgradeCandidate> consumer) {
		consumer.accept(upgradeCandidate);
	}

	public DependencySiteQuery toQuery() {

		List<String> versionPropertyNames = getVersionProperties().stream().map(VersionProperty::property)
				.toList();
		return DependencySiteQuery
				.create(it -> it.artifact(getArtifactId()).versionProperties(versionPropertyNames));
	}

	@Override
	public String toString() {
		return name + "@" + getCurrentVersion() + " -> ["
				+ upgradeCandidate.getDisplayReleases() + "]";
	}

	/**
	 * Render one label/value row in {@link DocumentationMarkup} section style.
	 * Swing tooltips do not carry the documentation pane's stylesheet, so the
	 * {@code section} class is inert and the label styling is inlined: context-help
	 * gray plus a right padding separating the label column from the value column.
	 *
	 * @param labelKey the message key for the row label.
	 * @param value the rendered row value.
	 * @return the tooltip table row.
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
