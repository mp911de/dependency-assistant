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
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.swing.Icon;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.assistant.DependencyUpgradeIcons;
import biz.paluch.dap.assistant.check.DependencyCheckResult;
import biz.paluch.dap.checker.CheckerIcons;
import biz.paluch.dap.rule.DependencyRuleEvaluator;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.upgrade.UpgradeSuggestions;
import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;
import org.jspecify.annotations.Nullable;

/**
 * Editable state of the dependency upgrade review dialog.
 *
 * @author Mark Paluch
 */
class UpgradeReview {

	private final List<TableRow> candidates;

	private final List<String> errors;

	private final Map<TableRow, UpgradeSelection> selections = new LinkedHashMap<>();

	private final boolean hasRule;

	private final Set<String> ambiguousArtifactIds = new HashSet<>();

	private final Map<TableRow, List<TableRow>> sharedPropertyPeers = new HashMap<>();

	private final EventDispatcher<ReviewListener> listeners = EventDispatcher.create(ReviewListener.class);

	private VisibilityFilter filter = VisibilityFilter.HIDE_UP_TO_DATE;

	private UpgradeStrategies upgradeStrategy = UpgradeStrategies.MANUAL;

	private final boolean hasSafeVersion;

	UpgradeReview(DependencyCheckResult result) {
		this(createRows(result), result.errors());
	}

	private static List<TableRow> createRows(DependencyCheckResult result) {

		List<TableRow> rows = new ArrayList<>(result.upgrades().size());
		result.upgrades().forEach(upgrade -> rows.add(new TableRow(upgrade)));
		return UpgradeRows.of(rows).toList();
	}

	/**
	 * Create a new {@code UpgradeReview}.
	 * @param candidates the update candidates to display.
	 */
	UpgradeReview(TableRow... candidates) {
		this(List.of(candidates), List.of());
	}

	/**
	 * Create a new {@code UpgradeReview}.
	 * @param candidates the update candidates to display.
	 * @param errors release-fetch errors collected while resolving.
	 */
	UpgradeReview(List<TableRow> candidates, List<String> errors) {

		this.candidates = candidates;
		this.errors = errors;

		boolean hasRule = false;
		Set<String> coordinateLabels = new HashSet<>();
		Map<TableRow, Set<String>> versionProperties = new LinkedHashMap<>();
		for (TableRow row : candidates) {

			if (row.getRule().isPresent()) {
				hasRule = true;
			}

			if (!row.isLabeledByDependencyName()
					&& !coordinateLabels.add(row.getArtifactId().artifactId())) {
				ambiguousArtifactIds.add(row.getArtifactId().artifactId());
			}

			Set<String> propertyNames = row.getVersionPropertyNames();
			if (!propertyNames.isEmpty()) {
				versionProperties.put(row, propertyNames);
			}

			selections.put(row, new UpgradeSelection(row.getCurrentVersion()));
		}
		this.hasRule = hasRule;

		versionProperties.forEach((row, propertyNames) -> {

			List<TableRow> peers = new ArrayList<>();
			versionProperties.forEach((other, otherNames) -> {
				if (other != row && !Collections.disjoint(propertyNames, otherNames)) {
					peers.add(other);
				}
			});

			if (!peers.isEmpty()) {
				sharedPropertyPeers.put(row, peers);
			}
		});

		boolean hasSafeVersion = false;

		for (TableRow row : this.candidates) {
			if (row.getUpgrade().isVulnerable()) {
				hasSafeVersion = true;
				break;
			}
		}

		this.hasSafeVersion = hasSafeVersion;
	}

	/**
	 * Return whether the row's bare artifactId collides with another row labeled by
	 * its coordinate. Computed once over the full row set so labels stay stable
	 * while filters toggle.
	 */
	boolean isAmbiguous(TableRow row) {
		return !row.isLabeledByDependencyName()
				&& ambiguousArtifactIds.contains(row.getArtifactId().artifactId());
	}

	/**
	 * Return the other rows coupled to the row through a Shared Version Property:
	 * one bare property name backing the declared version of more than one row.
	 * Computed once over the full row set so the rendering path stays cheap;
	 * informative only, coupled rows are never pulled into a group or blocked from
	 * applying.
	 *
	 * @return the coupled rows in row order; empty when the row's version
	 * properties back no other row.
	 */
	List<TableRow> getSharedPropertyPeers(TableRow row) {
		return sharedPropertyPeers.getOrDefault(row, List.of());
	}

	public UpgradeSelection getSelection(TableRow row) {
		return selections.computeIfAbsent(row, it -> new UpgradeSelection(it.getCurrentVersion()));
	}

	/**
	 * Register a listener notified when the review state changes. The listener is
	 * removed when {@code parent} is disposed.
	 */
	public void addListener(ReviewListener listener, Disposable parent) {
		listeners.addListener(listener, parent);
	}

	/**
	 * Return the candidates currently shown by the dialog under the active filter.
	 */
	List<TableRow> getCandidates() {
		return candidates.stream().filter(this::isVisible).toList();
	}

	/**
	 * Return all candidates regardless of the active visibility filter.
	 */
	List<TableRow> getAllCandidates() {
		return candidates;
	}

	private boolean isVisible(TableRow row) {
		return filter.includes(row.getUpgrade());
	}

	/**
	 * Return the release options shown for the given row under the active filter. A
	 * vulnerable row's Safe Version is pinned in so it stays selectable even when
	 * the filter would otherwise hide every newer release.
	 */
	public Releases getReleases(TableRow row) {
		return filter.visibleReleases(row.getUpgrade());
	}

	public UpgradeSuggestions getTargets(TableRow row) {
		return filter.visibleTargets(row.getUpgrade());
	}

	/**
	 * Return the strategy target for the row, or {@literal null} if no target
	 * exists or it is hidden by the active filter. Keeps strategy selection
	 * consistent with what the buttons and combo offer.
	 */
	@Nullable
	Release resolveTarget(TableRow row, UpgradeStrategy strategy) {
		return filter.hideUpToDate() ? row.getUpgrade().resolveDisplayTarget(strategy)
				: row.getUpgrade().resolveTarget(strategy);
	}

	/**
	 * Return whether the {@code Safe} upgrade-strategy entry should be offered:
	 * only when at least one unfiltered row is vulnerable. Evaluated over the full
	 * row set so the entry stays available while filters toggle.
	 *
	 * @return {@literal true} if any row is vulnerable; {@literal false} otherwise.
	 */
	boolean isSafeStrategyAvailable() {
		return hasSafeVersion;
	}

	/**
	 * Return whether up-to-date rows and noise releases are hidden.
	 */
	boolean isHideUpToDate() {
		return filter.hideUpToDate();
	}

	/**
	 * Return the active upgrade strategy selection.
	 */
	UpgradeStrategies getUpgradeStrategy() {
		return upgradeStrategy;
	}

	/**
	 * Return errors reported while checking dependencies.
	 */
	List<String> getErrors() {
		return errors;
	}

	/**
	 * Return the row's selected target version, or {@literal null} if cleared.
	 */
	@Nullable
	ArtifactVersion getUpdateTo(TableRow row) {
		return getSelection(row).getTargetVersion();
	}

	/**
	 * Return the visible release matching the row's selected target version
	 * (falling back to the current version), or {@literal null} if no visible
	 * release matches.
	 */
	@Nullable
	Release getSelectedRelease(TableRow row) {

		ArtifactVersion updateTo = getUpdateTo(row);
		ArtifactVersion shown = updateTo != null ? updateTo : row.getCurrentVersion();
		return getReleases(row).getRelease(shown);
	}

	/**
	 * Return the row's selected target version.
	 * @throws IllegalStateException if no target version is selected.
	 */
	ArtifactVersion getRequiredUpdateTo(TableRow row) {

		ArtifactVersion updateTo = getSelection(row).getTargetVersion();
		if (updateTo == null) {
			throw new IllegalStateException(
					"Update version for " + row.getArtifactId().artifactId() + " is required but not set");
		}
		return updateTo;
	}

	/**
	 * Return whether the row is selected to be applied.
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

			if (isApplyUpdate(row)) {
				updates.addAll(row.createUpdates(getRequiredUpdateTo(row)));
			}
		}

		return updates;
	}

	/**
	 * Select the given target version for the row.
	 */
	void selectTarget(TableRow row, ArtifactVersion version) {
		getSelection(row).selectTarget(row.getUpgrade().selectTarget(version).version());
		listeners.getMulticaster().changed(ReviewChange.row(row));
	}

	/**
	 * Select the row's target for the given strategy, if one is visible.
	 */
	void applyStrategyTarget(TableRow row, UpgradeStrategy strategy) {
		if (doApplyStrategyTarget(row, strategy)) {
			listeners.getMulticaster().changed(ReviewChange.row(row));
		}
	}

	/**
	 * Apply the given strategy selection to every visible row.
	 */
	void applyStrategyToAll(UpgradeStrategies selection) {

		this.upgradeStrategy = selection;
		UpgradeStrategy strategy = selection.getStrategy();
		if (strategy == null) {
			return;
		}

		for (TableRow row : getCandidates()) {
			doApplyStrategyTarget(row, strategy);
		}
		listeners.getMulticaster().changed(ReviewChange.allRows());
	}

	/**
	 * Set whether up-to-date rows and noise releases are hidden.
	 */
	void setHideUpToDate(boolean hide) {
		this.filter = hide ? VisibilityFilter.HIDE_UP_TO_DATE : VisibilityFilter.SHOW_ALL;
		listeners.getMulticaster().changed(ReviewChange.reloadVisible());
	}

	/**
	 * Set whether the row should be applied.
	 */
	void setSelected(TableRow row, boolean apply) {
		getSelection(row).setApplyUpdate(apply);
		listeners.getMulticaster().changed(ReviewChange.row(row));
	}

	/**
	 * Set whether all visible candidates should be applied.
	 */
	void selectAll(boolean apply) {

		for (TableRow row : getCandidates()) {
			getSelection(row).setApplyUpdate(apply);
		}
		listeners.getMulticaster().changed(ReviewChange.allRows());
	}

	private boolean doApplyStrategyTarget(TableRow row, UpgradeStrategy strategy) {

		Release target = resolveTarget(row, strategy);
		if (target == null) {
			return false;
		}

		getSelection(row).selectTarget(row.getUpgrade().selectTarget(target.version()).version());
		return true;
	}

	public DependencyRuleEvaluator getResult(DependencyRuleEvaluator rule) {

		if (!rule.isPresent() && hasRule) {
			return DependencyRuleEvaluator.absent();
		}
		return rule;
	}

	enum UpgradeStrategies {

		MANUAL("dialog.upgradeStrategy.manual"), //
		BUGFIX("dialog.upgradeStrategy.bugfix", UpgradeStrategy.PATCH), //
		MINOR("dialog.upgradeStrategy.minor", UpgradeStrategy.MINOR), //
		LATEST("dialog.upgradeStrategy.latest", UpgradeStrategy.LATEST), //
		SAFE("dialog.upgradeStrategy.safe", UpgradeStrategy.SAFE);

		private final String messageKey;

		private final @Nullable UpgradeStrategy strategy;

		UpgradeStrategies(String messageKey) {
			this.messageKey = messageKey;
			this.strategy = null;
		}

		UpgradeStrategies(String messageKey, UpgradeStrategy strategy) {
			this.messageKey = messageKey;
			this.strategy = strategy;
		}

		/**
		 * Return the upgrade strategy represented by this selection, or {@literal null}
		 * for manual selection.
		 */
		public @Nullable UpgradeStrategy getStrategy() {
			return strategy;
		}

		String getMessageKey() {
			return messageKey;
		}

		/**
		 * Same visual language as
		 * {@link DependencyCheckDialog.VersionOptionCellRenderer} / {@link VersionAge}
		 * for version steps.
		 */
		Icon getIcon() {

			if (this == SAFE) {
				return CheckerIcons.SAFE;
			}
			if (this == MANUAL || this.strategy == null) {
				return DependencyUpgradeIcons.resolveIcon(VersionAge.SAME_OR_UNKNOWN);
			}
			return DependencyUpgradeIcons.resolveIcon(this.strategy);
		}

	}

}
