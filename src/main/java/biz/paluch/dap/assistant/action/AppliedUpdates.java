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

package biz.paluch.dap.assistant.action;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.DependencyUpdate;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

/**
 * Accumulator for the updates a bulk apply wrote, retaining enough
 * per-occurrence detail to summarize them and to reverse-apply only the flagged
 * entries.
 *
 * <p>The flagged entries keep their originating {@link DependencyUpdate} and
 * target file so the undo action ({@link #getReverse()} applied to
 * {@link #getReverseFiles()}) can rewrite each back to its {@code from} version
 * through the same per-file build-file update path.
 *
 * @author Mark Paluch
 * @see AppliedDependencyUpdate
 * @see Notifications#updatesApplied(Project, java.util.Collection, Runnable)
 */
class AppliedUpdates {

	private final Set<AppliedDependencyUpdate> applied = new TreeSet<>();

	private final List<Reversible> outOfBounds = new ArrayList<>();

	/**
	 * Record an applied update with its governing rule, flagging it for undo when
	 * the applied version is out of bounds.
	 *
	 * @param file the file the update was written to.
	 * @param update the applied update.
	 * @param rule the dependency's governing rule.
	 */
	public void record(VirtualFile file, DependencyUpdate update, DependencyRule rule) {

		AppliedDependencyUpdate summary = AppliedDependencyUpdate.from(update, rule);
		applied.add(summary);
		if (summary.isFlagged()) {
			outOfBounds.add(new Reversible(file, update));
		}
	}

	/**
	 * Return the applied updates ordered by display label.
	 */
	public Set<AppliedDependencyUpdate> applied() {
		return applied;
	}

	public List<DependencyUpdate> getReverse() {
		return outOfBounds.stream().map(Reversible::reverse).toList();
	}

	public List<VirtualFile> getReverseFiles() {
		return outOfBounds.stream().map(Reversible::file).toList();
	}

	record Reversible(VirtualFile file, DependencyUpdate update) {

		DependencyUpdate reverse() {
			return new DependencyUpdate(update.artifactId(), update.version(), update.from().getVersion(),
					update.declarationSources(), update.versionSources());
		}

	}

}
