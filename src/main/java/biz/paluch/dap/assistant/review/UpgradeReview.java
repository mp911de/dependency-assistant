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
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Icon;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.assistant.DependencyUpgradeIcons;
import biz.paluch.dap.assistant.check.DeclaredVersions;
import biz.paluch.dap.assistant.check.DependencyCheckResult;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.assistant.check.VersionProperty;
import biz.paluch.dap.checker.CheckerIcons;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.plan.PlannedUpgrade;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import com.intellij.lang.documentation.DocumentationMarkup;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.util.EventDispatcher;
import org.jspecify.annotations.Nullable;

/**
 * Mutable selection state for one dependency review dialog.
 * <p>Target and apply selections propagate through rows connected by shared
 * version properties. Selected rows remain visible regardless of the filter.
 *
 * @author Mark Paluch
 */
class UpgradeReview {

	private final List<TableRow> candidates;

	private final List<String> errors;

	private final Map<TableRow, UpgradeSelection> selections = new HashMap<>();

	private final Set<String> ambiguousNames = new HashSet<>();

	private final Map<TableRow, List<TableRow>> sharedPropertyPeers = new HashMap<>();

	private final Map<TableRow, String> toolTips = new HashMap<>();

	private final Map<TableRow, Release> selectedReleases = new HashMap<>();

	private final EventDispatcher<ReviewListener> listeners = EventDispatcher.create(ReviewListener.class);

	private VisibilityFilter filter = VisibilityFilter.HIDE_UP_TO_DATE;

	private StrategySelection upgradeStrategy = StrategySelection.MANUAL;

	private final boolean hasVulnerableCandidate;

	UpgradeReview(DependencyCheckResult result) {
		this(createRows(result), result.errors());
	}

	private static List<TableRow> createRows(DependencyCheckResult result) {

		List<SingleTableRow> rows = new ArrayList<>(result.upgrades().size());
		result.upgrades().forEach(upgrade -> rows.add(new SingleTableRow(upgrade)));
		return UpgradeRows.of(rows).toList();
	}

	UpgradeReview(TableRow... candidates) {
		this(List.of(candidates), List.of());
	}

	UpgradeReview(List<TableRow> candidates, List<String> errors) {

		this.candidates = candidates;
		this.errors = errors;

		Set<String> seenNames = new HashSet<>();

		Map<TableRow, Set<VersionProperty>> versionProperties = new LinkedHashMap<>();
		for (TableRow row : candidates) {

			if (!seenNames.add(row.getName())) {
				ambiguousNames.add(row.getName());
			}

			Set<VersionProperty> properties = row.getVersionProperties();
			if (!properties.isEmpty()) {
				versionProperties.put(row, properties);
			}
		}

		Map<VersionProperty, List<TableRow>> rowsByProperty = new HashMap<>();
		versionProperties.forEach((row, properties) -> properties
				.forEach(property -> rowsByProperty.computeIfAbsent(property, key -> new ArrayList<>()).add(row)));

		versionProperties.forEach((row, properties) -> {

			Set<TableRow> peerSet = new LinkedHashSet<>();
			for (VersionProperty property : properties) {
				peerSet.addAll(rowsByProperty.get(property));
			}
			peerSet.remove(row);

			if (peerSet.isEmpty()) {
				return;
			}

			List<TableRow> peers = new ArrayList<>(peerSet.size());
			for (TableRow other : versionProperties.keySet()) {
				if (peerSet.contains(other)) {
					peers.add(other);
				}
			}
			sharedPropertyPeers.put(row, peers);
		});

		this.hasVulnerableCandidate = candidates.stream().anyMatch(row -> {
			Vulnerabilities vulnerabilities = row.getVulnerabilities(row.getCurrentVersion());
			return vulnerabilities.isVulnerable();
		});
	}

	/**
	 * Return whether another row has the same name, regardless of the active
	 * filter.
	 */
	boolean isAmbiguous(TableRow row) {
		return ambiguousNames.contains(row.getName());
	}

	/**
	 * Return direct shared-property peers in row order, or an empty list.
	 */
	List<TableRow> getSharedPropertyPeers(TableRow row) {
		return sharedPropertyPeers.getOrDefault(row, List.of());
	}

	String getCoordinateToolTip(TableRow row) {
		return toolTips.computeIfAbsent(row, this::renderCoordinateToolTip);
	}

	private String renderCoordinateToolTip(TableRow row) {

		List<HtmlChunk> sections = new ArrayList<>(row.getCoordinateToolTip());
		List<TableRow> peers = getSharedPropertyPeers(row);
		if (!peers.isEmpty()) {
			sections.add(sharedPropertySection(peers));
		}

		HtmlBuilder rows = new HtmlBuilder();
		sections.forEach(rows::append);

		HtmlBuilder html = new HtmlBuilder().append(row.getToolTipIntro())
				.append(rows.wrapWith(DocumentationMarkup.SECTIONS_TABLE));

		DeclaredVersions declaredVersions = row.getDeclaredVersions();
		if (declaredVersions.hasDeclarationDrift()) {
			html.append(declaredVersions.getDeclarationDriftToolTip());
		}

		return html.wrapWith("html").toString();
	}

	private static HtmlChunk sharedPropertySection(List<TableRow> peers) {

		HtmlBuilder peerLines = new HtmlBuilder();
		peerLines.appendWithSeparators(HtmlChunk.br(),
				peers.stream().map(peer -> HtmlChunk.text(peer.getName()).code()).toList());
		return TableRow.section("dialog.tooltip.sharedProperty", peerLines.toFragment());
	}

	private UpgradeSelection getSelection(TableRow row) {
		return selections.computeIfAbsent(row, it -> new UpgradeSelection(it.getCurrentVersion()));
	}

	/**
	 * Register a listener until {@code parent} is disposed.
	 */
	void addListener(ReviewListener listener, Disposable parent) {
		listeners.addListener(listener, parent);
	}

	/**
	 * Return visible rows in display order, including selected rows.
	 */
	List<TableRow> getCandidates() {
		return candidates.stream().filter(this::isVisible).toList();
	}

	/**
	 * Return the full row list regardless of filtering.
	 */
	List<TableRow> getAllCandidates() {
		return candidates;
	}

	private boolean isVisible(TableRow row) {
		return isApplyUpdate(row) || filter.includes(row.getUpgrade());
	}

	/**
	 * Return filtered release options, retaining remediation targets.
	 */
	Releases getReleases(TableRow row) {
		return filter.visibleReleases(row.getUpgrade());
	}

	/**
	 * Return the visible strategy target, or {@literal null} if none is available.
	 */
	@Nullable
	Release findRelease(TableRow row, UpgradeStrategy strategy) {
		return filter.findRelease(row.getUpgrade(), strategy);
	}

	/**
	 * Return whether any unfiltered row is vulnerable.
	 */
	boolean isSafeStrategyAvailable() {
		return hasVulnerableCandidate;
	}

	boolean isHideUpToDate() {
		return filter.hideUpToDate();
	}

	StrategySelection getUpgradeStrategy() {
		return upgradeStrategy;
	}

	/**
	 * Return non-fatal errors from the dependency check.
	 */
	List<String> getErrors() {
		return errors;
	}

	/**
	 * Return the target version, or {@literal null} if cleared.
	 */
	@Nullable
	ArtifactVersion getUpdateTo(TableRow row) {
		return getSelection(row).getTargetVersion();
	}

	/**
	 * Return the selected release, falling back to the current version if cleared.
	 * Versions absent from release history receive a synthetic release.
	 */
	Release getSelectedRelease(TableRow row) {
		return selectedReleases.computeIfAbsent(row, this::resolveSelectedRelease);
	}

	private Release resolveSelectedRelease(TableRow row) {

		ArtifactVersion updateTo = getUpdateTo(row);
		ArtifactVersion shown = updateTo != null ? updateTo : row.getCurrentVersion();
		Release release = getReleases(row).getRelease(shown);
		if (release == null) {
			release = row.getUpgrade().getReleases().getRelease(shown);
		}
		return release != null ? release : Release.of(shown);
	}

	/**
	 * Return release options, keeping a hidden or synthetic selection as the first
	 * option.
	 */
	List<Release> getReleaseOptions(TableRow row) {

		Releases current = getReleases(row);
		List<Release> releases = new ArrayList<>(current.toList());
		Release selected = getSelectedRelease(row);
		if (current.getRelease(selected.version()) == null) {
			releases.addFirst(selected);
		}
		return releases;
	}

	/**
	 * Return the selected target version.
	 * @throws IllegalStateException if no target version is selected.
	 */
	ArtifactVersion getRequiredUpdateTo(TableRow row) {

		ArtifactVersion updateTo = getSelection(row).getTargetVersion();
		if (updateTo == null) {
			throw new IllegalStateException(
					"Update version for '%s' is required but not set".formatted(row.getName()));
		}
		return updateTo;
	}

	boolean isApplyUpdate(TableRow row) {
		return getSelection(row).isApplyUpdate();
	}

	/**
	 * Return selected updates in row order. Groups produce one update per member.
	 */
	List<DependencyUpdate> getSelectedUpdates() {

		List<DependencyUpdate> updates = new ArrayList<>();
		for (TableRow row : getCandidates()) {

			if (!isApplyUpdate(row)) {
				continue;
			}

			ArtifactVersion version = getRequiredUpdateTo(row);
			for (DependencyUpgradeCandidate upgrade : row.getUpgradeCandidates()) {
				updates.add(upgrade.createUpdate(version));
			}
		}

		return updates;
	}

	/**
	 * Return selected rows and target versions in row order for Upgrade Plan
	 * transfer.
	 * @throws IllegalStateException if a selected row has no target version.
	 */
	Map<PlannedUpgrade, ArtifactVersion> getSelectedUpgrades() {

		Map<PlannedUpgrade, ArtifactVersion> selected = new LinkedHashMap<>();
		for (TableRow row : getCandidates()) {
			if (isApplyUpdate(row)) {
				selected.put(row, getRequiredUpdateTo(row));
			}
		}
		return selected;
	}

	/**
	 * Select a target version and propagate it through shared-property peers.
	 * <p>Versions absent from release history are valid. Shared properties and
	 * persisted plans can refer to versions the row has never released.
	 */
	void setVersion(TableRow row, ArtifactVersion version) {

		UpgradeSelection selection = getSelection(row);
		if (version.matches(selection.getTargetVersion())) {
			return;
		}

		List<TableRow> visibleBefore = getCandidates();
		Release release = row.getUpgrade().getReleases().getRelease(version);
		setArtifactVersion(row, release != null ? release.version() : version);
		fireChange(row, visibleBefore);
	}

	/**
	 * Select the strategy target if it is visible.
	 */
	void applyStrategyTarget(TableRow row, UpgradeStrategy strategy) {

		List<TableRow> visibleBefore = getCandidates();
		if (doApplyStrategyTarget(row, strategy)) {
			fireChange(row, visibleBefore);
		}
	}

	/**
	 * Apply the strategy to every visible row.
	 */
	void applyStrategyToAll(StrategySelection selection) {

		this.upgradeStrategy = selection;
		UpgradeStrategy strategy = selection.getStrategy();
		if (strategy == null) {
			return;
		}

		List<TableRow> visibleBefore = getCandidates();
		for (TableRow row : visibleBefore) {
			doApplyStrategyTarget(row, strategy);
		}
		fireBulkChange(visibleBefore);
	}

	void setHideUpToDate(boolean hide) {

		this.filter = hide ? VisibilityFilter.HIDE_UP_TO_DATE : VisibilityFilter.SHOW_ALL;
		selectedReleases.clear();
		listeners.getMulticaster().changed(ReviewChange.reloadVisible());
	}

	/**
	 * Set the apply flag for the row and its transitive shared-property peers.
	 */
	void setSelected(TableRow row, boolean apply) {

		List<TableRow> visibleBefore = getCandidates();
		boolean hasChanged = false;

		for (TableRow candidate : selectionCohort(row)) {
			UpgradeSelection selection = getSelection(candidate);
			if (selection.isApplyUpdate() != apply) {
				selection.setApplyUpdate(apply);
				hasChanged = true;
			}
		}

		if (hasChanged) {
			fireChange(row, visibleBefore);
		}
	}

	/**
	 * Set the apply flag for all visible rows.
	 */
	void selectAll(boolean apply) {

		List<TableRow> visibleBefore = getCandidates();
		for (TableRow row : visibleBefore) {
			getSelection(row).setApplyUpdate(apply);
		}
		fireBulkChange(visibleBefore);
	}

	private boolean doApplyStrategyTarget(TableRow row, UpgradeStrategy strategy) {

		Release target = findRelease(row, strategy);
		if (target == null) {
			return false;
		}

		setArtifactVersion(row, target.version());
		return true;
	}

	private void setArtifactVersion(TableRow row, ArtifactVersion version) {

		UpgradeSelection source = getSelection(row);
		source.setTargetVersion(version);
		boolean apply = source.isApplyUpdate();
		for (TableRow candidate : selectionCohort(row)) {

			selectedReleases.remove(candidate);
			if (candidate == row) {
				continue;
			}

			UpgradeSelection selection = getSelection(candidate);
			selection.setTargetVersion(version);
			selection.setApplyUpdate(apply);
		}
	}

	private Set<TableRow> selectionCohort(TableRow row) {

		Set<TableRow> cohort = new LinkedHashSet<>();
		List<TableRow> pending = new ArrayList<>();
		pending.add(row);
		while (!pending.isEmpty()) {
			TableRow candidate = pending.removeFirst();
			if (cohort.add(candidate)) {
				pending.addAll(getSharedPropertyPeers(candidate));
			}
		}
		return cohort;
	}

	private void fireChange(TableRow row, List<TableRow> visibleBefore) {

		if (!visibleBefore.equals(getCandidates())) {
			listeners.getMulticaster().changed(ReviewChange.reloadVisible());
			return;
		}

		listeners.getMulticaster().changed(getSharedPropertyPeers(row).isEmpty() ? ReviewChange.row(row)
				: ReviewChange.allRows());
	}

	private void fireBulkChange(List<TableRow> visibleBefore) {

		listeners.getMulticaster().changed(visibleBefore.equals(getCandidates()) ? ReviewChange.allRows()
				: ReviewChange.reloadVisible());
	}

	enum StrategySelection {

		MANUAL("dialog.upgradeStrategy.manual"), //
		BUGFIX("dialog.upgradeStrategy.bugfix", UpgradeStrategy.PATCH), //
		MINOR("dialog.upgradeStrategy.minor", UpgradeStrategy.MINOR), //
		LATEST("dialog.upgradeStrategy.latest", UpgradeStrategy.LATEST), //
		SAFE("dialog.upgradeStrategy.safe", UpgradeStrategy.SAFE);

		private final String messageKey;

		private final @Nullable UpgradeStrategy strategy;

		StrategySelection(String messageKey) {
			this.messageKey = messageKey;
			this.strategy = null;
		}

		StrategySelection(String messageKey, UpgradeStrategy strategy) {
			this.messageKey = messageKey;
			this.strategy = strategy;
		}

		/**
		 * Return the strategy, or {@literal null} for manual selection.
		 */
		@Nullable
		UpgradeStrategy getStrategy() {
			return strategy;
		}

		String getMessageKey() {
			return messageKey;
		}

		Icon getIcon() {

			if (this == SAFE) {
				return CheckerIcons.SAFE;
			}
			if (strategy == null) {
				return DependencyUpgradeIcons.resolveIcon(VersionAge.SAME_OR_UNKNOWN);
			}
			return DependencyUpgradeIcons.resolveIcon(strategy);
		}

	}

}
