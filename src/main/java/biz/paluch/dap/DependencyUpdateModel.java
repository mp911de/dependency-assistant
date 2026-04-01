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

import biz.paluch.dap.artifact.DependencyUpdateOption;
import biz.paluch.dap.artifact.DependencyUpdates;
import biz.paluch.dap.artifact.UpgradeStrategy;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;

import org.jspecify.annotations.Nullable;

/**
 * Model to capture the dependency update dialog state.
 */
class DependencyUpdateModel {

	private final DependencyUpdates updateCheckResult;

	private final List<DependencyUpdateOption> updateOptions = new ArrayList<>();

	private UpgradeStrategies upgradeStrategy = UpgradeStrategies.MANUAL;

	private boolean filterVersionSuggestions = true;

	public DependencyUpdateModel(DependencyUpdates updateCheckResult) {
		this.updateCheckResult = updateCheckResult;
		setFilterVersionSuggestions(true);
	}

	public void setUpdateAll(boolean state) {
		for (DependencyUpdateOption option : getUpdates()) {
			option.setApplyUpdate(state);
		}
	}

	public UpgradeStrategies getUpgradeStrategy() {
		return upgradeStrategy;
	}

	public void setUpgradeStrategy(UpgradeStrategies upgradeStrategy) {
		this.upgradeStrategy = upgradeStrategy;
	}

	public boolean isFilterVersionSuggestions() {
		return filterVersionSuggestions;
	}

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

	public List<DependencyUpdateOption> getUpdates() {
		return updateOptions;
	}

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
				case PATCH -> VersionAge.NEWER_PATCH;
				case MINOR -> VersionAge.NEWER_MINOR;
				case MAJOR, LATEST -> VersionAge.NEWER_MAJOR;
				case PREVIEW -> VersionAge.PREVIEW;
			}).getIcon();
		}

	}

}
