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

package biz.paluch.dap;

import java.util.ArrayList;
import java.util.List;
import javax.swing.*;

import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.DependencyUpdateOption;
import biz.paluch.dap.artifact.DependencyUpdates;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.artifact.VersionAge;
import org.jspecify.annotations.Nullable;

/**
 * Model to capture the dependency update dialog state.
 */
class DependencyUpdateModel {

	private final DependencyUpdates updateCheckResult;

	private final List<DependencyUpdateOption> updateOptions = new ArrayList<>();

	private UpgradeStrategies upgradeStrategy = UpgradeStrategies.MANUAL;

	private boolean filterVersionSuggestions = true;

	/**
	 * Create a new {@code DependencyUpdateModel}.
	 * @param updateCheckResult the dependency update check result.
	 */
	public DependencyUpdateModel(DependencyUpdates updateCheckResult) {
		this.updateCheckResult = updateCheckResult;
		setFilterVersionSuggestions(true);
	}

	/**
	 * Set whether all visible update options should be applied.
	 */
	public void setUpdateAll(boolean state) {
		for (DependencyUpdateOption option : getUpdateOptions()) {
			option.setApplyUpdate(state);
		}
	}

	/**
	 * Return the active upgrade strategy selection.
	 */
	public UpgradeStrategies getUpgradeStrategy() {
		return upgradeStrategy;
	}

	/**
	 * Set the active upgrade strategy selection.
	 */
	public void setUpgradeStrategy(UpgradeStrategies upgradeStrategy) {
		this.upgradeStrategy = upgradeStrategy;
	}

	/**
	 * Return whether version suggestions are filtered for update candidates.
	 */
	public boolean isFilterVersionSuggestions() {
		return filterVersionSuggestions;
	}

	/**
	 * Set whether version suggestions are filtered for update candidates.
	 */
	public void setFilterVersionSuggestions(boolean filterVersionSuggestions) {

		this.filterVersionSuggestions = filterVersionSuggestions;
		updateOptions.clear();

		if (filterVersionSuggestions) {
			for (DependencyUpdateOption updateOption : this.updateCheckResult.getUpdates()) {
				if (!updateOption.hasUpdateCandidate()) {
					continue;
				}

				if (updateOption.getTargets().size() == 1 && updateOption.getTargets().containsKey(UpgradeStrategy.PREVIEW)) {
					continue;
				}
				updateOptions.add(updateOption);
			}
		} else {
			updateOptions.addAll(updateCheckResult.getUpdates());
		}
	}

	/**
	 * Return the update options shown by the dialog.
	 */
	public List<DependencyUpdateOption> getUpdateOptions() {
		return updateOptions;
	}

	/**
	 * Return the selected dependency updates.
	 */
	public List<DependencyUpdate> toDependencyUpdates() {
		return updateOptions.stream().filter(DependencyUpdateOption::isApplyUpdate).map(DependencyUpdate::from)
				.toList();
	}

	/**
	 * Return errors reported while checking dependencies.
	 */
	public List<String> getErrors() {
		return updateCheckResult.errors();
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
		 * Return the upgrade strategy represented by this selection, or {@code null}
		 * for manual selection.
		 */
		public @Nullable UpgradeStrategy getStrategy() {
			return strategy;
		}

		String getMessageKey() {
			return messageKey;
		}

		/**
		 * Same visual language as {@link VersionOptionCellRenderer} / {@link VersionAge} for version steps.
		 */
		Icon getIcon() {

			if (this == MANUAL || this.strategy == null) {
				return VersionAge.SAME_OR_UNKNOWN.getIcon();
			}
			return getIcon(this.strategy);
		}

		/**
		 * Same visual language as {@link VersionOptionCellRenderer} / {@link VersionAge} for version steps.
		 */
		Icon getIcon(UpgradeStrategy upgradeStrategy) {
			return (switch (upgradeStrategy) {
			case RELEASE, PATCH -> VersionAge.NEWER_PATCH;
				case MINOR -> VersionAge.NEWER_MINOR;
				case MAJOR, LATEST -> VersionAge.NEWER_MAJOR;
				case PREVIEW -> VersionAge.PREVIEW;
			}).getIcon();
		}

	}

}
