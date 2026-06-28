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

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ProjectBuildContext;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Cross-ecosystem coordinator that owns the collect-complete-store flow for one
 * {@link DependencyAssistant} run.
 *
 * <p>The indexer enumerates anchor files through the assistant, derives a
 * {@link biz.paluch.dap.support.ProjectBuildContext} per anchor on demand, runs
 * phase-one collection for available anchors, then applies phase-two completion
 * via {@link IntrospectedDependencies}, and finally invalidates and stores the
 * resulting collectors per project in the
 * {@link biz.paluch.dap.state.ProjectState}. Read-action wrapping, progress
 * reporting, availability guards, and cancellation are handled here so
 * build-tool integrations contribute only the source component.
 *
 * @author Mark Paluch
 * @see DependencyAssistant
 * @see IntrospectedDependencies
 */
public class ProjectStateIndexer {

	private final Project project;

	private final StateService service;

	private final ProgressIndicator indicator;

	/**
	 * Create an indexer for the given project, using the project-scoped
	 * {@link StateService}.
	 * @param project the IntelliJ project.
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 */
	public ProjectStateIndexer(Project project, ProgressIndicator indicator) {
		this(project, StateService.getInstance(project), indicator);
	}

	/**
	 * Create an indexer for the given project, state service, and progress
	 * indicator.
	 * @param project the IntelliJ project.
	 * @param service the state service backing this run; must not be
	 * {@literal null}.
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 */
	public ProjectStateIndexer(Project project, StateService service, ProgressIndicator indicator) {
		this.project = project;
		this.service = service;
		this.indicator = indicator;
	}

	public Project getProject() {
		return project;
	}

	public StateService getService() {
		return service;
	}

	/**
	 * Run a full population pass wrapped in a read action.
	 * @param assistant the assistant to run.
	 */
	public void readAndUpdateAll(DependencyAssistant assistant) {

		Application application = ApplicationManager.getApplication();
		if (application == null) {
			updateAll(assistant);
			return;
		}
		application.runReadAction(() -> updateAll(assistant));
	}

	/**
	 * Run a full population pass: enumerate, collect, complete, invalidate, and
	 * store.
	 * @param assistant the assistant to run.
	 */
	public void updateAll(DependencyAssistant assistant) {

		IntrospectedDependencies introspected = assistant.introspect(project);
		List<ActiveScan> active = collectPhase(assistant, introspected);

		for (ActiveScan scan : active) {
			introspected.complete(scan.collector());
		}

		for (ActiveScan scan : active) {
			ProjectState state = service.getProjectState(scan.context().getProjectId());
			state.invalidateDependencies();
			state.setDependencies(scan.collector(), assistant.getPackageSystem());
		}
	}

	/**
	 * Run a full scan: enumerate, collect, complete, and deliver one collector per
	 * anchor file to the consumer.
	 * <p>The aggregate result is not stored in the {@link ProjectState} and does
	 * not invoke the cache update.
	 * @param assistant the assistant to run.
	 * @param consumer the per-file callback.
	 */
	public void forEach(DependencyAssistant assistant, BiConsumer<VirtualFile, DependencyCollector> consumer) {

		record Entry(VirtualFile file, DependencyCollector collector) {
		}

		IntrospectedDependencies introspected = assistant.introspect(project);
		List<Entry> active = new ArrayList<>();

		forEachAvailableEntry(assistant, (anchor, context) -> {
			DependencyCollector collector = new DependencyCollector();
			assistant.collect(anchor, collector, introspected);
			collector.addAllReleaseSources(context.getReleaseSources());
			active.add(new Entry(anchor.getVirtualFile(), collector));
		});

		for (Entry entry : active) {
			introspected.complete(entry.collector());
		}

		for (Entry entry : active) {
			consumer.accept(entry.file(), entry.collector());
		}
	}

	/**
	 * Run a file-scoped invalidation: re-collect the state owned by the given file
	 * and route it through the same complete-store flow.
	 * @param assistant the assistant that owns the file; must not be
	 * {@literal null}.
	 * @param file the saved PSI file.
	 */
	public void invalidate(DependencyAssistant assistant, PsiFile file) {

		if (!assistant.supports(file)) {
			return;
		}

		ProjectBuildContext context = assistant.createContext(project, file);
		if (context.isAbsent()) {
			return;
		}

		IntrospectedDependencies introspected = assistant.introspect(project);
		DependencyCollector collector = new DependencyCollector();
		assistant.collect(file, collector, introspected);
		introspected.complete(collector);

		ProjectState state = service.getProjectState(context.getProjectId());
		state.invalidateDependencies();
		state.setDependencies(collector, assistant.getPackageSystem());
	}

	private List<ActiveScan> collectPhase(DependencyAssistant assistant, IntrospectedDependencies introspected) {

		List<ActiveScan> active = new ArrayList<>();

		forEachAvailableEntry(assistant, (anchor, context) -> {
			DependencyCollector collector = new DependencyCollector();
			assistant.collect(anchor, collector, introspected);
			active.add(new ActiveScan(context, collector));
		});

		return active;
	}

	public void forEachAvailableEntry(DependencyAssistant assistant,
			BiConsumer<PsiFile, ProjectDependencyContext> action) {

		List<PsiFile> anchors = assistant.enumerate(project);
		int processed = 0;

		for (PsiFile anchor : anchors) {

			indicator.checkCanceled();
			ProjectDependencyContext context = assistant.createContext(project, anchor);
			if (context.isAvailable()) {
				action.accept(anchor, context);
			}
			processed += 1;
			indicator.setFraction((double) processed / anchors.size());
		}
	}

	private record ActiveScan(ProjectBuildContext context, DependencyCollector collector) {

	}

}
