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
import java.util.Collection;
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
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidates;
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

	private final List<UpgradeRow> candidates;

	private final List<String> errors;

	private final Map<UpgradeRow, UpgradeSelection> selections = new LinkedHashMap<>();

	private final boolean hasRule;

	private final Set<String> ambiguousArtifactIds = new HashSet<>();

	private final Map<UpgradeRow, List<UpgradeRow>> sharedPropertyPeers = new HashMap<>();

	private final EventDispatcher<ReviewListener> listeners = EventDispatcher.create(ReviewListener.class);

	private VisibilityFilter filter = VisibilityFilter.HIDE_UP_TO_DATE;

	private UpgradeStrategies upgradeStrategy = UpgradeStrategies.MANUAL;

	private final boolean hasSafeVersion;

	UpgradeReview(DependencyUpgradeCandidates result) {
		this(createRows(result), result.errors());
	}

	private static List<UpgradeRow> createRows(DependencyUpgradeCandidates result) {

		List<UpgradeRow> rows = new ArrayList<>(result.decisions().size());
		result.decisions().forEach(decision -> {
			rows.add(new UpgradeRow(decision, result.assistants().get(decision),
					result.declaredVersions().get(decision)));
		});
		return UpgradeRows.of(rows).toList();
	}

	/**
	 * Create a new {@code UpgradeReview}.
	 * @param candidates the update candidates to display.
	 */
	UpgradeReview(UpgradeRow... candidates) {
		this(List.of(candidates), List.of());
	}

	/**
	 * Create a new {@code UpgradeReview}.
	 * @param candidates the update candidates to display.
	 * @param errors release-fetch errors collected while resolving.
	 */
	UpgradeReview(Collection<UpgradeRow> candidates, List<String> errors) {

		this.candidates = List.copyOf(candidates);
		this.errors = errors;

		boolean hasRule = false;
		Set<String> coordinateLabels = new HashSet<>();
		Map<UpgradeRow, Set<String>> versionProperties = new LinkedHashMap<>();
		for (UpgradeRow candidate : candidates) {

			if (candidate.getRule().isPresent()) {
				hasRule = true;
			}

			if (!candidate.isLabeledByDependencyName()
					&& !coordinateLabels.add(candidate.getArtifactId().artifactId())) {
				ambiguousArtifactIds.add(candidate.getArtifactId().artifactId());
			}

			Set<String> propertyNames = candidate.getVersionPropertyNames();
			if (!propertyNames.isEmpty()) {
				versionProperties.put(candidate, propertyNames);
			}

			selections.put(candidate, new UpgradeSelection(candidate.getCurrentVersion()));
		}
		this.hasRule = hasRule;

		versionProperties.forEach((candidate, propertyNames) -> {

			List<UpgradeRow> peers = new ArrayList<>();
			versionProperties.forEach((other, otherNames) -> {
				if (other != candidate && !Collections.disjoint(propertyNames, otherNames)) {
					peers.add(other);
				}
			});

			if (!peers.isEmpty()) {
				sharedPropertyPeers.put(candidate, peers);
			}
		});

		boolean hasSafeVersion = false;

		for (UpgradeRow upgradeCandidate : this.candidates) {
			if (upgradeCandidate.getDecision().isVulnerable()) {
				hasSafeVersion = true;
				break;
			}
		}

		this.hasSafeVersion = hasSafeVersion;
	}

	/**
	 * Return whether the candidate's bare artifactId collides with another row
	 * labeled by its coordinate. Computed once over the full candidate set so
	 * labels stay stable while filters toggle.
	 */
	boolean isAmbiguous(UpgradeRow candidate) {
		return !candidate.isLabeledByDependencyName()
				&& ambiguousArtifactIds.contains(candidate.getArtifactId().artifactId());
	}

	/**
	 * Return the other rows coupled to the candidate through a Shared Version
	 * Property: one bare property name backing the declared version of more than
	 * one row. Computed once over the full candidate set so the rendering path
	 * stays cheap; informative only, coupled rows are never pulled into a group or
	 * blocked from applying.
	 *
	 * @return the coupled rows in row order; empty when the candidate's version
	 * properties back no other row.
	 */
	List<UpgradeRow> getSharedPropertyPeers(UpgradeRow candidate) {
		return sharedPropertyPeers.getOrDefault(candidate, List.of());
	}

	public UpgradeSelection getSelection(UpgradeRow candidate) {
		return selections.computeIfAbsent(candidate, it -> new UpgradeSelection(it.getCurrentVersion()));
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
	List<UpgradeRow> getCandidates() {
		return candidates.stream().filter(this::isVisible).toList();
	}

	/**
	 * Return all candidates regardless of the active visibility filter.
	 */
	List<UpgradeRow> getAllCandidates() {
		return candidates;
	}

	private boolean isVisible(UpgradeRow candidate) {
		return filter.includes(candidate.getDecision());
	}

	/**
	 * Return the release options shown for the given candidate under the active
	 * filter. A vulnerable row's Safe Version is pinned in so it stays selectable
	 * even when the filter would otherwise hide every newer release.
	 */
	public Releases getReleases(UpgradeRow candidate) {
		return filter.visibleReleases(candidate.getDecision());
	}

	public UpgradeSuggestions getTargets(UpgradeRow candidate) {
		return filter.visibleTargets(candidate.getDecision());
	}

	/**
	 * Return the strategy target for the candidate, or {@literal null} if no target
	 * exists or it is hidden by the active filter. Keeps strategy selection
	 * consistent with what the buttons and combo offer.
	 */
	@Nullable
	Release resolveTarget(UpgradeRow candidate, UpgradeStrategy strategy) {
		return filter.hideUpToDate() ? candidate.getDecision().resolveDisplayTarget(strategy)
				: candidate.getDecision().resolveTarget(strategy);
	}

	/**
	 * Return whether the {@code Safe} upgrade-strategy entry should be offered:
	 * only when at least one unfiltered candidate is vulnerable. Evaluated over the
	 * full candidate set so the entry stays available while filters toggle.
	 *
	 * @return {@literal true} if any candidate is vulnerable; {@literal false}
	 * otherwise.
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
	 * Return the candidate's selected target version, or {@literal null} if
	 * cleared.
	 */
	@Nullable
	ArtifactVersion getUpdateTo(UpgradeRow candidate) {
		return getSelection(candidate).getTargetVersion();
	}

	/**
	 * Return the visible release matching the candidate's selected target version
	 * (falling back to the current version), or {@literal null} if no visible
	 * release matches.
	 */
	@Nullable
	Release getSelectedRelease(UpgradeRow candidate) {

		ArtifactVersion updateTo = getUpdateTo(candidate);
		ArtifactVersion shown = updateTo != null ? updateTo : candidate.getCurrentVersion();
		return getReleases(candidate).getRelease(shown);
	}

	/**
	 * Return the candidate's selected target version.
	 * @throws IllegalStateException if no target version is selected.
	 */
	ArtifactVersion getRequiredUpdateTo(UpgradeRow candidate) {

		ArtifactVersion updateTo = getSelection(candidate).getTargetVersion();
		if (updateTo == null) {
			throw new IllegalStateException(
					"Update version for " + candidate.getArtifactId().artifactId() + " is required but not set");
		}
		return updateTo;
	}

	/**
	 * Return whether the candidate is selected to be applied.
	 */
	boolean isApplyUpdate(UpgradeRow candidate) {
		return getSelection(candidate).isApplyUpdate();
	}

	/**
	 * Return the updates for all visible candidates selected to be applied. A
	 * selected {@link UpgradeGroupRow} fans out to one update per member
	 * coordinate.
	 *
	 * @return the updates to apply in row order.
	 */
	List<DependencyUpdate> getSelectedUpdates() {

		List<DependencyUpdate> updates = new ArrayList<>();
		for (UpgradeRow candidate : getCandidates()) {

			if (isApplyUpdate(candidate)) {
				updates.addAll(candidate.createUpdates(getRequiredUpdateTo(candidate)));
			}
		}

		return updates;
	}

	/**
	 * Select the given target version for the candidate.
	 */
	void selectTarget(UpgradeRow candidate, ArtifactVersion version) {
		getSelection(candidate).selectTarget(candidate.getDecision().selectTarget(version).version());
		listeners.getMulticaster().changed(ReviewChange.row(candidate));
	}

	/**
	 * Select the candidate's target for the given strategy, if one is visible.
	 */
	void applyStrategyTarget(UpgradeRow candidate, UpgradeStrategy strategy) {
		if (doApplyStrategyTarget(candidate, strategy)) {
			listeners.getMulticaster().changed(ReviewChange.row(candidate));
		}
	}

	/**
	 * Apply the given strategy selection to every visible candidate.
	 */
	void applyStrategyToAll(UpgradeStrategies selection) {

		this.upgradeStrategy = selection;
		UpgradeStrategy strategy = selection.getStrategy();
		if (strategy == null) {
			return;
		}

		for (UpgradeRow candidate : getCandidates()) {
			doApplyStrategyTarget(candidate, strategy);
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
	 * Set whether the candidate should be applied.
	 */
	void setSelected(UpgradeRow candidate, boolean apply) {
		getSelection(candidate).setApplyUpdate(apply);
		listeners.getMulticaster().changed(ReviewChange.row(candidate));
	}

	/**
	 * Set whether all visible candidates should be applied.
	 */
	void selectAll(boolean apply) {

		for (UpgradeRow candidate : getCandidates()) {
			getSelection(candidate).setApplyUpdate(apply);
		}
		listeners.getMulticaster().changed(ReviewChange.allRows());
	}

	private boolean doApplyStrategyTarget(UpgradeRow candidate, UpgradeStrategy strategy) {

		Release target = resolveTarget(candidate, strategy);
		if (target == null) {
			return false;
		}

		getSelection(candidate).selectTarget(candidate.getDecision().selectTarget(target.version()).version());
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
