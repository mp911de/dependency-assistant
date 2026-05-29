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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencySource;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.ProjectStateUpdater;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Coordinator that batches save-time Project State refreshes by their owning
 * {@link DependencyAssistant}.
 *
 * <p>
 * A save event typically delivers multiple changed files, and any number of
 * them can belong to the same assistant. Running the full assistant-scoped
 * {@link ProjectStateUpdater} once per saved file would repeat the same scan
 * needlessly. This coordinator groups the saved files by their owning
 * assistant and runs the full updater exactly once per represented
 * {@link DependencySource}-implementing assistant. Owners that do not
 * implement {@link DependencySource} keep the previous behavior of a
 * file-scoped {@link ProjectDependencyContext#invalidateState(PsiFile)} per
 * saved file.
 *
 * @author Mark Paluch
 * @see ProjectStateUpdater
 * @see DependencyAssistant
 */
public class SaveStateRefresh {

	private final Project project;

	private final StateService service;

	/**
	 * Create a coordinator using the project-scoped {@link StateService}.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 */
	public SaveStateRefresh(Project project) {
		this(project, StateService.getInstance(project));
	}

	/**
	 * Create a coordinator using the given state service.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 * @param service the state service backing the underlying updater runs; must
	 * not be {@literal null}.
	 */
	public SaveStateRefresh(Project project, StateService service) {
		this.project = project;
		this.service = service;
	}

	/**
	 * Group the given files under the assistants that claim them.
	 * <p>
	 * Files supported by no assistant are dropped; files supported by more than
	 * one assistant are added to each owner's list. The returned map preserves
	 * insertion order to keep refresh order deterministic.
	 * @param assistants the assistants to consult; must not be {@literal null}.
	 * @param files the saved files to group; must not be {@literal null}.
	 * @return the grouped files keyed by owning assistant.
	 */
	public static Map<DependencyAssistant, List<PsiFile>> groupByOwner(List<DependencyAssistant> assistants,
			List<PsiFile> files) {

		Map<DependencyAssistant, List<PsiFile>> grouped = new LinkedHashMap<>();
		for (PsiFile file : files) {
			for (DependencyAssistant assistant : assistants) {
				if (assistant.supports(file)) {
					grouped.computeIfAbsent(assistant, k -> new ArrayList<>()).add(file);
				}
			}
		}
		return grouped;
	}

	/**
	 * Refresh Project State for the grouped files.
	 * <p>
	 * For each {@link DependencySource}-implementing assistant the full
	 * {@link ProjectStateUpdater#updateAll(DependencySource, ProgressIndicator)}
	 * pass runs exactly once and the file list is ignored: the updater
	 * re-enumerates the project itself. Owners that do not implement
	 * {@link DependencySource} fall back to a file-scoped
	 * {@link ProjectDependencyContext#invalidateState(PsiFile)} per file so the
	 * existing per-file behavior is preserved.
	 * @param filesByAssistant the saved files grouped by owning assistant; must
	 * not be {@literal null}.
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 */
	public void refresh(Map<DependencyAssistant, List<PsiFile>> filesByAssistant, ProgressIndicator indicator) {

		for (Map.Entry<DependencyAssistant, List<PsiFile>> entry : filesByAssistant.entrySet()) {
			DependencyAssistant assistant = entry.getKey();
			if (assistant instanceof DependencySource source) {
				new ProjectStateUpdater(project, service).updateAll(source, indicator);
			}
			else {
				for (PsiFile file : entry.getValue()) {
					ProjectDependencyContext context = assistant.createContext(project, file);
					if (context.isAvailable()) {
						context.invalidateState(file);
					}
				}
			}
		}
	}

}
