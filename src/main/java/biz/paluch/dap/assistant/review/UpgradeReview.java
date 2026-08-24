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
 * Mutable, dialog-scoped review session over grouped dependency upgrade rows.
 *
 * <p>The session owns the active visibility filter and each row's selected
 * target and apply mark. Selections propagate through the transitive closure of
 * rows sharing a version property. Confirmation projects the armed rows either
 * into apply-ready {@link DependencyUpdate}s or {@link PlannedUpgrade} targets
 * without exposing the mutable selection state.
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

	/**
	 * Create a new {@code UpgradeReview}.
	 *
	 * @param candidates the update candidates to display.
	 */
	UpgradeReview(TableRow... candidates) {
		this(List.of(candidates), List.of());
	}

	/**
	 * Create a new {@code UpgradeReview}.
	 *
	 * @param candidates the update candidates to display.
	 * @param errors release-fetch errors collected while resolving.
	 */
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
	 * Return whether another row has the same name. The result is computed once
	 * over the full row set so disambiguating labels stay stable while filters
	 * toggle.
	 *
	 * @param row the row to inspect.
	 * @return {@code true} if the row name is ambiguous.
	 */
	boolean isAmbiguous(TableRow row) {
		return ambiguousNames.contains(row.getName());
	}

	/**
	 * Return the other rows coupled to the row through a Shared Version Property.
	 *
	 * @param row the row whose peers are requested.
	 * @return the coupled rows in row order; empty when the row's version
	 * properties back no other row.
	 */
	List<TableRow> getSharedPropertyPeers(TableRow row) {
		return sharedPropertyPeers.getOrDefault(row, List.of());
	}

	/**
	 * Return the fully rendered coordinate-column tooltip for the row.
	 *
	 * @param row the row whose tooltip is requested.
	 * @return the rendered HTML tooltip.
	 */
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
	 * Register a listener notified when the review state changes. The listener is
	 * removed when {@code parent} is disposed.
	 *
	 * @param listener the listener to notify.
	 * @param parent the disposable that owns the registration.
	 */
	void addListener(ReviewListener listener, Disposable parent) {
		listeners.addListener(listener, parent);
	}

	/**
	 * Return the candidates currently shown by the dialog under the active filter.
	 *
	 * @return the visible rows in display order.
	 */
	List<TableRow> getCandidates() {
		return candidates.stream().filter(this::isVisible).toList();
	}

	/**
	 * Return all candidates regardless of the active visibility filter.
	 *
	 * @return all rows in display order.
	 */
	List<TableRow> getAllCandidates() {
		return candidates;
	}

	private boolean isVisible(TableRow row) {
		return isApplyUpdate(row) || filter.includes(row.getUpgrade());
	}

	/**
	 * Return the release options shown for the given row under the active filter.
	 * The filtered view retains remediation targets even when ordinary display
	 * filtering would omit them.
	 *
	 * @param row the row whose releases are requested.
	 * @return the row's active release view.
	 */
	Releases getReleases(TableRow row) {
		return filter.visibleReleases(row.getUpgrade());
	}

	/**
	 * Return the strategy target for the row, or {@literal null} if no target
	 * exists or it is hidden by the active filter. Keeps strategy selection
	 * consistent with what the buttons and combo offer.
	 *
	 * @param row the row whose target is requested.
	 * @param strategy the strategy to resolve.
	 * @return the visible target, or {@literal null} if none is available.
	 */
	@Nullable
	Release findRelease(TableRow row, UpgradeStrategy strategy) {
		return filter.findRelease(row.getUpgrade(), strategy);
	}

	/**
	 * Return whether the {@code Safe} upgrade-strategy entry should be offered:
	 * only when at least one unfiltered row is vulnerable. Evaluated over the full
	 * row set so the entry stays available while filters toggle.
	 *
	 * @return {@literal true} if any row is vulnerable; {@literal false} otherwise.
	 */
	boolean isSafeStrategyAvailable() {
		return hasVulnerableCandidate;
	}

	/**
	 * Return whether the filtered display view is active.
	 *
	 * @return {@code true} when the filtered row and release views are active.
	 */
	boolean isHideUpToDate() {
		return filter.hideUpToDate();
	}

	/**
	 * Return the active upgrade strategy selection.
	 *
	 * @return the active bulk strategy selection.
	 */
	StrategySelection getUpgradeStrategy() {
		return upgradeStrategy;
	}

	/**
	 * Return errors reported while checking dependencies.
	 *
	 * @return the non-fatal dependency-check errors.
	 */
	List<String> getErrors() {
		return errors;
	}

	/**
	 * Return the row's selected target version, or {@literal null} if cleared.
	 *
	 * @param row the row whose target is requested.
	 * @return the target version, or {@literal null} if cleared.
	 */
	@Nullable
	ArtifactVersion getUpdateTo(TableRow row) {
		return getSelection(row).getTargetVersion();
	}

	/**
	 * Return the release matching the row's selected target version, falling back
	 * to a synthetic release when that version is absent from the row's release
	 * history.
	 *
	 * @param row the row whose selection is requested.
	 * @return the selected release or its synthetic representation.
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
	 * Return the release options for the row, retaining a selected synthetic or
	 * otherwise hidden release as the first option.
	 *
	 * @param row the row whose options are requested.
	 * @return the selectable releases.
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
	 * Return the row's selected target version.
	 *
	 * @param row the row whose target is required.
	 * @return the selected target version.
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

	/**
	 * Return whether the row is selected to be applied.
	 *
	 * @param row the row to inspect.
	 * @return {@code true} if the row is armed for apply or transfer.
	 */
	boolean isApplyUpdate(TableRow row) {
		return getSelection(row).isApplyUpdate();
	}

	/**
	 * Return the updates for all visible candidates selected to be applied. A
	 * selected {@link GroupRow} fans out to one update per member coordinate.
	 *
	 * @return the updates to apply in row order.
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
	 * Return the armed upgrades: every visible candidate selected to be applied,
	 * mapped to its required target version, in row order. This is the canonical
	 * form handed to the Upgrade Plan; review-internal selection state does not
	 * leave the review.
	 *
	 * @return the armed rows and their target versions in row order.
	 * @throws IllegalStateException if an armed row has no target version.
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
	 * Select the given target version for the row. A version absent from the row's
	 * release universe is kept as-is: shared-property propagation and persisted
	 * plans legitimately carry versions the row has never released.
	 *
	 * @param row the row whose target is selected.
	 * @param version the target version.
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
	 * Select the row's target for the given strategy, if one is visible.
	 *
	 * @param row the row to update.
	 * @param strategy the strategy whose target is selected.
	 */
	void applyStrategyTarget(TableRow row, UpgradeStrategy strategy) {

		List<TableRow> visibleBefore = getCandidates();
		if (doApplyStrategyTarget(row, strategy)) {
			fireChange(row, visibleBefore);
		}
	}

	/**
	 * Apply the given strategy selection to every visible row.
	 *
	 * @param selection the bulk strategy selection.
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

	/**
	 * Select the filtered display view or the complete view.
	 *
	 * @param hide whether the filtered display view is active.
	 */
	void setHideUpToDate(boolean hide) {

		this.filter = hide ? VisibilityFilter.HIDE_UP_TO_DATE : VisibilityFilter.SHOW_ALL;
		selectedReleases.clear();
		listeners.getMulticaster().changed(ReviewChange.reloadVisible());
	}

	/**
	 * Set whether the row should be applied.
	 *
	 * @param row the row whose selection cohort is changed.
	 * @param apply whether the cohort is armed.
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
	 * Set whether all visible candidates should be applied.
	 *
	 * @param apply whether the visible rows are armed.
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

	/**
	 * Notify listeners after a change rooted in one row: a reload when the visible
	 * row set changed, a single-row refresh when the row stands alone, or an
	 * all-rows refresh when shared-property peers changed with it.
	 */
	private void fireChange(TableRow row, List<TableRow> visibleBefore) {

		if (!visibleBefore.equals(getCandidates())) {
			listeners.getMulticaster().changed(ReviewChange.reloadVisible());
			return;
		}

		listeners.getMulticaster().changed(getSharedPropertyPeers(row).isEmpty() ? ReviewChange.row(row)
				: ReviewChange.allRows());
	}

	/**
	 * Notify listeners after a change spanning many rows: a reload when the visible
	 * row set changed, an all-rows refresh otherwise.
	 */
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
		 * Return the upgrade strategy represented by this selection, or {@literal null}
		 * for manual selection.
		 */
		@Nullable
		UpgradeStrategy getStrategy() {
			return strategy;
		}

		String getMessageKey() {
			return messageKey;
		}

		/**
		 * Return the icon used for this bulk selection. Version steps use the same
		 * visual language as {@link DependencyUpdateTable.VersionOptionCellRenderer}
		 * and {@link VersionAge}.
		 *
		 * @return the bulk strategy icon.
		 */
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
