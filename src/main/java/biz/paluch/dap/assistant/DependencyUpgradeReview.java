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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.swing.Icon;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionAge;
import com.intellij.openapi.Disposable;
import com.intellij.util.EventDispatcher;
import org.jspecify.annotations.Nullable;

/**
 * Editable state of the dependency upgrade review dialog.
 *
 * @author Mark Paluch
 */
class DependencyUpgradeReview {

	private final List<UpdateCandidate> candidates;

	private final List<String> errors;

	private final Map<UpdateCandidate, UpgradeSelection> selections = new LinkedHashMap<>();

	private final boolean hasRule;

	private final EventDispatcher<ReviewListener> listeners = EventDispatcher.create(ReviewListener.class);

	private VisibilityFilter filter = VisibilityFilter.HIDE_UP_TO_DATE;

	private UpgradeStrategies upgradeStrategy = UpgradeStrategies.MANUAL;

	/**
	 * Create a new {@code DependencyUpgradeReview}.
	 * @param candidates the update candidates to display.
	 * @param errors release-fetch errors collected while resolving.
	 */
	DependencyUpgradeReview(List<UpdateCandidate> candidates, List<String> errors) {
		this.candidates = candidates;
		this.errors = errors;

		boolean hasRule = false;
		for (UpdateCandidate candidate : candidates) {
			if (candidate.rule().isPresent()) {
				hasRule = true;
			}
			selections.put(candidate, new UpgradeSelection(candidate.currentVersion()));
		} this.hasRule = hasRule;
	}

	private UpgradeSelection selection(UpdateCandidate candidate) {
		return selections.computeIfAbsent(candidate, it -> new UpgradeSelection(it.currentVersion()));
	}

	/**
	 * Register a listener notified when the review state changes. The listener is
	 * removed when {@code parent} is disposed.
	 */
	void addListener(ReviewListener listener, Disposable parent) {
		listeners.addListener(listener, parent);
	}

	/**
	 * Return the candidates currently shown by the dialog under the active filter.
	 */
	List<UpdateCandidate> visibleCandidates() {
		return candidates.stream().filter(candidate -> filter.includes(candidate.option())).toList();
	}

	/**
	 * Return the release options shown for the given candidate under the active
	 * filter.
	 */
	List<Release> visibleReleases(UpdateCandidate candidate) {
		return filter.visibleReleases(candidate.option());
	}

	/**
	 * Return the release options shown for the given option under the active
	 * filter.
	 */
	List<Release> visibleReleases(DependencyUpdateCandidate option) {
		return filter.visibleReleases(option);
	}

	/**
	 * Return the strategy target for the candidate, or {@literal null} if no target
	 * exists or it is hidden by the active filter. Keeps strategy selection
	 * consistent with what the buttons and combo offer.
	 */
	@Nullable
	Release resolveTarget(UpdateCandidate candidate, UpgradeStrategy strategy) {

		Release target = candidate.option().getTargets().get(strategy);
		return target != null && visibleReleases(candidate).contains(target) ? target : null;
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
	ArtifactVersion getUpdateTo(UpdateCandidate candidate) {
		return selection(candidate).getTargetVersion();
	}

	/**
	 * Return the candidate's selected target version.
	 * @throws IllegalStateException if no target version is selected.
	 */
	ArtifactVersion getRequiredUpdateTo(UpdateCandidate candidate) {

		ArtifactVersion updateTo = selection(candidate).getTargetVersion();
		if (updateTo == null) {
			throw new IllegalStateException(
					"Update version for " + candidate.getArtifactId().artifactId() + " is required but not set");
		}
		return updateTo;
	}

	/**
	 * Return whether the candidate is selected to be applied.
	 */
	boolean isApplyUpdate(UpdateCandidate candidate) {
		return selection(candidate).isApplyUpdate();
	}

	/**
	 * Select the given target version for the candidate.
	 */
	void selectTarget(UpdateCandidate candidate, ArtifactVersion version) {
		selection(candidate).selectTarget(version);
		listeners.getMulticaster().changed(ReviewChange.row(candidate));
	}

	/**
	 * Select the candidate's target for the given strategy, if one is visible.
	 */
	void applyStrategyTarget(UpdateCandidate candidate, UpgradeStrategy strategy) {
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

		for (UpdateCandidate candidate : visibleCandidates()) {
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
	void setSelected(UpdateCandidate candidate, boolean apply) {
		selection(candidate).setApplyUpdate(apply);
		listeners.getMulticaster().changed(ReviewChange.row(candidate));
	}

	/**
	 * Set whether all visible candidates should be applied.
	 */
	void selectAll(boolean apply) {

		for (UpdateCandidate candidate : visibleCandidates()) {
			selection(candidate).setApplyUpdate(apply);
		}
		listeners.getMulticaster().changed(ReviewChange.allRows());
	}

	private boolean doApplyStrategyTarget(UpdateCandidate candidate, UpgradeStrategy strategy) {

		Release target = resolveTarget(candidate, strategy);
		if (target == null) {
			return false;
		}

		selection(candidate).selectTarget(target.version());
		return true;
	}

	public EvaluatedDependencyRule getResult(EvaluatedDependencyRule rule) {

		if (!rule.isPresent() && hasRule) {
			return EvaluatedDependencyRule.absent();
		} return rule;
	}

	enum UpgradeStrategies {

		MANUAL("dialog.upgradeStrategy.manual"), //
		BUGFIX("dialog.upgradeStrategy.bugfix", UpgradeStrategy.PATCH), //
		MINOR("dialog.upgradeStrategy.minor", UpgradeStrategy.MINOR), //
		LATEST("dialog.upgradeStrategy.latest", UpgradeStrategy.LATEST);

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
		 * Same visual language as {@link VersionOptionCellRenderer} /
		 * {@link VersionAge} for version steps.
		 */
		Icon getIcon() {

			if (this == MANUAL || this.strategy == null) {
				return VersionAge.SAME_OR_UNKNOWN.getIcon();
			}
			return getIcon(this.strategy);
		}

		/**
		 * Same visual language as {@link VersionOptionCellRenderer} /
		 * {@link VersionAge} for version steps.
		 */
		Icon getIcon(UpgradeStrategy upgradeStrategy) {
			return (switch (upgradeStrategy) {
			case RELEASE, PATCH -> VersionAge.NEWER_PATCH;
			case MINOR -> VersionAge.NEWER_MINOR;
			case MAJOR, LATEST -> VersionAge.NEWER_MAJOR;
				case PREVIEW -> VersionAge.PREVIEW; case RULE -> VersionAge.RULE;
			}).getIcon();
		}

	}

}
