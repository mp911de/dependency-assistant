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
 * A sequence of dependency updates.
 *
 * @author Mark Paluch
 */
public class DependencyUpdates implements Sequence<DependencyUpdate> {

	private final List<DependencyUpdate> updates;

	public DependencyUpdates(DependencyUpdate updates) {
		this(List.of(updates));
	}

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
	 * Update all dependencies.
	 *
	 * @param file file being updated.
	 * @param updateTask task to apply to each dependency update.
	 */
	public final void updateAll(PsiFile file, Consumer<DependencyUpdate> updateTask) {
		for (DependencyUpdate update : updates) {
			update(file, update, updateTask);
		}
	}

	/**
	 * Template-method called to update each dependency.
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
	 * Template-method called after a dependency update.
	 *
	 * @param file file that is being updated.
	 * @param update the dependency update.
	 */
	protected void afterDependencyUpdate(PsiFile file, DependencyUpdate update) {

	}

}
