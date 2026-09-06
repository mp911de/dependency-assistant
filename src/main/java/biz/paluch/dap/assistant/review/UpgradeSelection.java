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

import biz.paluch.dap.artifact.ArtifactVersion;
import org.jspecify.annotations.Nullable;

/**
 * Mutable target and apply flag owned by {@link UpgradeReview}.
 * <p>Selecting a version other than the current version sets the apply flag.
 * Selecting the current version clears it.
 *
 * @author Mark Paluch
 */
class UpgradeSelection {

	private final ArtifactVersion currentVersion;

	private @Nullable ArtifactVersion targetVersion;

	private boolean applyUpdate;

	UpgradeSelection(ArtifactVersion currentVersion) {
		this.currentVersion = currentVersion;
		this.targetVersion = currentVersion;
		this.applyUpdate = false;
	}

	/**
	 * Return the target version, or {@literal null} if cleared.
	 */
	@Nullable
	ArtifactVersion getTargetVersion() {
		return targetVersion;
	}

	/**
	 * Set the target and update the apply flag by comparison with the current
	 * version.
	 * @param targetVersion the target, or {@literal null} to clear it.
	 */
	void setTargetVersion(@Nullable ArtifactVersion targetVersion) {
		this.targetVersion = targetVersion;
		this.applyUpdate = !currentVersion.equals(targetVersion);
	}

	boolean isApplyUpdate() {
		return applyUpdate;
	}

	void setApplyUpdate(boolean applyUpdate) {
		this.applyUpdate = applyUpdate;
	}

}
