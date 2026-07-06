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

import java.util.Collection;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.assistant.check.UpgradeCandidate;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Effect boundary used by the review dialog to delegate writes and
 * notifications back to the action module.
 *
 * @author Mark Paluch
 */
public interface ReviewActions {

	/**
	 * Return whether the candidate can be added to {@code dependencyfile.json}.
	 */
	boolean canAddToDependencyfile(UpgradeCandidate candidate);

	/**
	 * Add the candidate to {@code dependencyfile.json}.
	 */
	void addToDependencyfile(UpgradeCandidate candidate);

	/**
	 * Apply the selected updates to the reviewed files.
	 */
	void applyUpdates(Collection<VirtualFile> files, List<DependencyUpdate> updates,
			ProgressIndicator indicator);

	/**
	 * Report a failure from the review apply task.
	 */
	void reportApplyError(Throwable error);

	/**
	 * Transfer the armed review rows into the Upgrade Plan tool window, replacing any
	 * previously planned upgrade and revealing the tool window.
	 *
	 * @param upgrades the upgrades armed in the review, each with its pinned target.
	 * @param files    the build files in the reviewed scope, as paths, that
	 *                 become the plan's scope.
	 */
	void openInUpgradePlan(Map<UpgradeCandidate, UpgradeSelection> upgrades, List<VirtualFile> files);

}
