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

package biz.paluch.dap.assistant;

import java.util.Comparator;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.assistant.presentation.DependencyPresentation;
import biz.paluch.dap.metadata.ProjectName;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.ArtifactVersionChange;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.MessageBundle;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.PropertyKey;

/**
 * Summary of an update that changed a build file.
 * <p>Natural ordering compares display labels only and is inconsistent with
 * equality.
 *
 * @author Mark Paluch
 */
public record AppliedUpdate(ArtifactVersionChange update,
		String displayName, Flag flag)
		implements Comparable<AppliedUpdate> {

	private static final Comparator<AppliedUpdate> COMPARATOR = Comparator
			.comparing(AppliedUpdate::displayName);

	/**
	 * Classify an applied update against its governing rule.
	 */
	public static AppliedUpdate from(DependencyUpdate update, DependencyRule rule,
			DependencyPresentation presentation) {
		return new AppliedUpdate(update, getDisplayName(presentation),
				flagFor(update, rule, update.getUpgradeStrategy()));
	}

	private static String getDisplayName(DependencyPresentation presentation) {
		if (presentation.hasDependencyName()) {
			return presentation.getDependencyName();
		}
		ProjectName projectName = presentation.getProjectName();
		if (projectName.hasDisplayName()) {
			return projectName.getDisplayName();
		}
		return presentation.getDisplayName();
	}

	/**
	 * Classify an applied update without a governing rule.
	 * <p>Only major version crossings receive a follow-up flag.
	 */
	public static AppliedUpdate from(DependencyUpdate update,
			String displayName) {
		return new AppliedUpdate(update, displayName,
				flagFor(update, DependencyRule.absent(), null));
	}

	public ArtifactVersion getFromVersion() {
		return update.from().getVersion();
	}

	public ArtifactVersion getTargetVersion() {
		return update.to();
	}

	/**
	 * Return whether the update requires a follow-up notification.
	 */
	public boolean isFlagged() {
		return flag != Flag.NONE;
	}

	public boolean isUpgrade() {
		return getTargetVersion().isNewer(getFromVersion());
	}

	public boolean isDowngrade() {
		return getFromVersion().isNewer(getTargetVersion());
	}

	@Override
	public int compareTo(AppliedUpdate o) {
		return COMPARATOR.compare(this, o);
	}

	/**
	 * Render a message for the given key using the display name and target version.
	 */
	@Nls
	public String getMessage(@PropertyKey(resourceBundle = MessageBundle.BUNDLE) String messageKey) {
		return MessageBundle.message(messageKey,
				displayName(), getTargetVersion());
	}

	@Override
	public String toString() {
		if (isUpgrade()) {
			return getMessage("AppliedUpdate.upgrade");
		}
		if (isDowngrade()) {
			return getMessage("AppliedUpdate.downgrade");
		}
		return getMessage("AppliedUpdate.update");
	}

	/**
	 * Why an applied update is called out in the after-apply balloon.
	 */
	public enum Flag {

		/**
		 * No follow-up is required.
		 */
		NONE,

		/**
		 * The target is rejected by the governing rule, or its classified upgrade
		 * strategy is disabled by that rule.
		 */
		COMPLIANCE,

		/**
		 * No governing rule, and the upgrade crosses a major version line.
		 */
		MAJOR_CROSSING

	}

	private static Flag flagFor(ArtifactVersionChange update, DependencyRule rule,
			@Nullable UpgradeStrategy upgradeStrategy) {

		if (rule.isPresent()) {

			boolean compliant = rule.test(update.to());
			if (upgradeStrategy != null) {
				compliant &= rule.isEnabled(upgradeStrategy);
			}
			return compliant ? Flag.NONE : Flag.COMPLIANCE;
		}

		return update.crossesMajor() ? Flag.MAJOR_CROSSING : Flag.NONE;
	}

}
