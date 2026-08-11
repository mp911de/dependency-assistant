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
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.ProjectBuildContext;
import biz.paluch.dap.util.StepsProgressIndicator;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

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

	private static final Logger LOG = Logger.getInstance(ProjectStateIndexer.class);

	private final Project project;

	private final StateService service;

	private final ProgressIndicator indicator;

	/**
	 * Create an indexer for the given project, using the project-scoped
	 * {@link StateService}.
	 * @param project the IntelliJ project.
	 * @param indicator the progress indicator to report to.
	 */
	public ProjectStateIndexer(Project project, ProgressIndicator indicator) {
		this(project, StateService.getInstance(project), indicator);
	}

	/**
	 * Create an indexer for the given project, state service, and progress
	 * indicator.
	 * @param project the IntelliJ project.
	 * @param service the state service backing this run.
	 * @param indicator the progress indicator to report to.
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
	 * Composed action running the collect-complete-store flow for all registered
	 * assistants and restarting highlighting so state derived from the import model
	 * surfaces in the editor.
	 */
	public static void refreshAfterImport(Project project, ProgressIndicator indicator,
			Predicate<DependencyAssistant> filter) {

		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll();

		if (assistants.isEmpty()) {
			return;
		}

		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, assistants.size());
		steps.setIndeterminate(false);
		List<CancellablePromise<Void>> promises = new ArrayList<>();
		ProjectStateIndexer indexer = new ProjectStateIndexer(project, indicator);
		for (DependencyAssistant assistant : assistants) {
			if (filter.test(assistant)) {
				promises.add(indexer.refreshAfterImport(assistant).onSuccess(__ -> steps.nextStep()));
			}
		}

		try {
			Promises.all(promises).onSuccess(__ -> steps.nextStep()).blockingGet(60, TimeUnit.SECONDS);
		} catch (TimeoutException e) {
			throw new RuntimeException(e);
		} catch (ExecutionException e) {
			if (e.getCause() instanceof ProcessCanceledException pce) {
				throw pce;
			}
			throw new RuntimeException(e);
		}

		DaemonCodeAnalyzer.getInstance(project).restart("Build system import finished");
	}

	/**
	 * Re-run the collect-complete-store flow for the given assistant after a
	 * build-system import and restart highlighting so state derived from the import
	 * model surfaces without plugin-side scheduling.
	 * <p>The re-index runs as a non-blocking read action in smart mode, coalesced
	 * per assistant so bursts of import events collapse into one pass.
	 *
	 * @param assistant the assistant whose project state is re-indexed.
	 * @return a promise representing the refresh operation.
	 */
	public CancellablePromise<Void> refreshAfterImport(DependencyAssistant assistant) {

		return ReadAction.nonBlocking(() -> {
			updateAll(assistant);
		}).inSmartMode(project)
				.coalesceBy(project, ProjectStateIndexer.class, assistant.getId())
				.expireWith(project)
				.submit(AppExecutorUtil.getAppExecutorService());
	}

	/**
	 * Run a full population pass with only the PSI-touching collect phase wrapped
	 * in a read action. Completion and storing run on the calling thread so a
	 * background task never holds the read lock for phase two.
	 * @param assistant the assistant to run.
	 */
	public void readAndUpdateAll(DependencyAssistant assistant) {

		Application application = ApplicationManager.getApplication();
		if (application == null) {
			updateAll(assistant);
			return;
		}

		IntrospectedDependencies introspected = assistant.introspect(project);
		List<ActiveScan> active = ReadAction.nonBlocking(() -> collectPhase(assistant, introspected))
				.executeSynchronously();
		completeAndStore(assistant, introspected, active);
	}

	/**
	 * Run a full population pass: enumerate, collect, complete, invalidate, and
	 * store. The caller is responsible for read-action wrapping of the PSI-touching
	 * collect phase.
	 * @param assistant the assistant to run.
	 */
	public void updateAll(DependencyAssistant assistant) {

		IntrospectedDependencies introspected = assistant.introspect(project);
		completeAndStore(assistant, introspected, collectPhase(assistant, introspected));
	}

	private void completeAndStore(DependencyAssistant assistant, IntrospectedDependencies introspected,
			List<ActiveScan> active) {

		for (ActiveScan scan : active) {
			introspected.complete(scan.collector());
		}

		for (ActiveScan scan : active) {
			ProjectState state = service.getProjectState(scan.context().getProjectId());
			state.invalidateDependencies();
			state.setDependencies(scan.collector());
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
			DependencyCollector collector = new DependencyCollector(context.getPackageSystem());
			assistant.collect(anchor, collector, introspected);
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
	 * @param assistant the assistant that owns the file.
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
		DependencyCollector collector = new DependencyCollector(context.getPackageSystem());
		assistant.collect(file, collector, introspected);
		introspected.complete(collector);

		ProjectState state = service.getProjectState(context.getProjectId());
		state.invalidateDependencies();
		state.setDependencies(collector);
	}

	private List<ActiveScan> collectPhase(DependencyAssistant assistant, IntrospectedDependencies introspected) {

		List<ActiveScan> active = new ArrayList<>();

		forEachAvailableEntry(assistant, (anchor, context) -> {
			DependencyCollector collector = new DependencyCollector(context.getPackageSystem());
			assistant.collect(anchor, collector, introspected);
			active.add(new ActiveScan(context, collector));
		});

		return active;
	}

	public void forEachAvailableEntry(DependencyAssistant assistant,
			BiConsumer<PsiFile, ProjectDependencyContext> action) {

		List<PsiFile> files = assistant.enumerate(project);
		if (files.isEmpty()) {
			return;
		}


		LOG.debug("[%s] Enumerated %d entries".formatted(assistant.getId(), files.size()));
		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, files.size());
		steps.setIndeterminate(false);

		for (PsiFile file : files) {

			steps.checkCanceled();
			ProjectDependencyContext context = assistant.createContext(project, file);
			if (context.isAvailable()) {
				action.accept(file, context);
			}
			else {
				LOG.warn("[%s] Skipping file '%s' because context is not available".formatted(assistant.getId(),
						file.getVirtualFile()));
			}
			steps.nextStep();
		}
	}

	private record ActiveScan(ProjectBuildContext context, DependencyCollector collector) {

	}

}
