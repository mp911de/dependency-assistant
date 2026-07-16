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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.upgrade.UpgradeDecision;
import org.jspecify.annotations.Nullable;

/**
 * The user's in-progress pick for one {@link UpgradeRow} in the review dialog:
 * the chosen target version and whether to apply it.
 *
 * <p>Owned by {@link UpgradeReview}, keyed by candidate. Sits between the
 * {@link UpgradeDecision} (what can be chosen) and the {@code DependencyUpdate}
 * written on confirm (what was chosen). Selecting a target other than the
 * current version arms the apply flag; reselecting the current version clears
 * it.
 *
 * @author Mark Paluch
 */
public class UpgradeSelection {

	private final ArtifactVersion currentVersion;

	private @Nullable ArtifactVersion targetVersion;

	private boolean applyUpdate;

	UpgradeSelection(ArtifactVersion currentVersion) {
		this.currentVersion = currentVersion;
		this.targetVersion = currentVersion;
		this.applyUpdate = false;
	}

	/**
	 * Return the selected target version, or {@literal null} if cleared.
	 *
	 * @return the selected target version, or {@literal null}.
	 */
	@Nullable
	public ArtifactVersion getTargetVersion() {
		return targetVersion;
	}

	/**
	 * Select the given target version and arm the apply flag when it differs from
	 * the current version.
	 */
	void selectTarget(@Nullable ArtifactVersion targetVersion) {
		this.targetVersion = targetVersion;
		this.applyUpdate = !currentVersion.equals(targetVersion);
	}

	/**
	 * Return whether this selection should be applied.
	 */
	boolean isApplyUpdate() {
		return applyUpdate;
	}

	/**
	 * Set whether this selection should be applied.
	 */
	void setApplyUpdate(boolean applyUpdate) {
		this.applyUpdate = applyUpdate;
	}

}
