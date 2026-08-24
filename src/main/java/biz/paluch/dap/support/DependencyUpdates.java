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
 * Ordered sequence of dependency updates passed to a build-file writer.
 *
 * <p>The default {@link #update(PsiFile, DependencyUpdate, Consumer)}
 * implementation invokes the writer task and then the
 * {@link #afterDependencyUpdate(PsiFile, DependencyUpdate)} hook, even when the
 * writer task makes no file-text change. Subclasses may override that
 * orchestration. {@link #updateAll(PsiFile, Consumer)} delegates each update in
 * sequence order to {@code update}.
 *
 * @author Mark Paluch
 */
public class DependencyUpdates implements Sequence<DependencyUpdate> {

	private final List<DependencyUpdate> updates;

	/**
	 * Create a sequence containing one dependency update.
	 *
	 * @param updates the dependency update to expose.
	 */
	public DependencyUpdates(DependencyUpdate updates) {
		this(List.of(updates));
	}

	/**
	 * Create a sequence backed by the given update list.
	 *
	 * <p>The list is retained without copying.
	 *
	 * @param updates the updates in traversal order.
	 */
	public DependencyUpdates(List<DependencyUpdate> updates) {
		this.updates = updates;
	}

	/**
	 * Create a sequence containing one dependency update.
	 *
	 * @param update the dependency update to expose.
	 * @return a single-update sequence.
	 */
	public static DependencyUpdates of(DependencyUpdate update) {
		return new DependencyUpdates(update);
	}

	@Override
	public Iterator<DependencyUpdate> iterator() {
		return updates.iterator();
	}

	/**
	 * Apply the given task to every dependency update in sequence order.
	 *
	 * @param file the file being updated.
	 * @param updateTask task to apply to each dependency update.
	 */
	public final void updateAll(PsiFile file, Consumer<DependencyUpdate> updateTask) {
		for (DependencyUpdate update : updates) {
			update(file, update, updateTask);
		}
	}

	/**
	 * Apply one update and invoke the post-update hook.
	 *
	 * <p>Subclasses may override this template method to change orchestration. The
	 * default always invokes {@code updateTask} before
	 * {@link #afterDependencyUpdate(PsiFile, DependencyUpdate)}.
	 *
	 * @param file the file being updated.
	 * @param update the dependency update.
	 * @param updateTask update task to apply to the dependency update.
	 */
	public void update(PsiFile file, DependencyUpdate update, Consumer<DependencyUpdate> updateTask) {
		updateTask.consume(update);
		afterDependencyUpdate(file, update);
	}

	/**
	 * Hook invoked after the writer task has processed a dependency update.
	 *
	 * <p>The writer task may have made no change. Implementations that report or
	 * record applied updates must compare file state before treating the update as
	 * applied.
	 *
	 * @param file the file that was passed to the writer task.
	 * @param update the dependency update.
	 */
	protected void afterDependencyUpdate(PsiFile file, DependencyUpdate update) {

	}

}
