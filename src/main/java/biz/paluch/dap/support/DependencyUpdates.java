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

package biz.paluch.dap.support;

import java.util.Iterator;
import java.util.List;

import biz.paluch.dap.util.Sequence;
import com.intellij.psi.PsiFile;
import com.intellij.util.Consumer;

/**
 * Ordered dependency updates with an overridable writer hook.
 *
 * @author Mark Paluch
 */
public class DependencyUpdates implements Sequence<DependencyUpdate> {

	private final List<DependencyUpdate> updates;

	public DependencyUpdates(DependencyUpdate updates) {
		this(List.of(updates));
	}

	/**
	 * Retain the update list without copying.
	 */
	public DependencyUpdates(List<DependencyUpdate> updates) {
		this.updates = updates;
	}

	public static DependencyUpdates of(DependencyUpdate update) {
		return new DependencyUpdates(update);
	}

	@Override
	public Iterator<DependencyUpdate> iterator() {
		return updates.iterator();
	}

	/**
	 * Apply updates in sequence order through
	 * {@link #update(PsiFile, DependencyUpdate, Consumer)}.
	 */
	public final void updateAll(PsiFile file, Consumer<DependencyUpdate> updateTask) {
		for (DependencyUpdate update : updates) {
			update(file, update, updateTask);
		}
	}

	/**
	 * Invoke the writer task, then the post-update hook. Subclasses may override
	 * this orchestration. The hook runs even if the task makes no file change.
	 */
	public void update(PsiFile file, DependencyUpdate update, Consumer<DependencyUpdate> updateTask) {
		updateTask.consume(update);
		afterDependencyUpdate(file, update);
	}

	/**
	 * Called after the writer task returns. This does not establish that a change
	 * was applied. Implementations recording applied updates must compare file
	 * state.
	 */
	protected void afterDependencyUpdate(PsiFile file, DependencyUpdate update) {

	}

}
