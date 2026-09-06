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

package biz.paluch.dap;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
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
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;

/**
 * Coordinates dependency collection, completion, and storage across
 * integrations.
 * <p>All anchors are collected before introspection completes their collectors.
 * The indexer handles progress and cancellation so assistants can focus on
 * ecosystem-specific work.
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
	 * Create an indexer backed by the project's {@link StateService}.
	 */
	public ProjectStateIndexer(Project project, ProgressIndicator indicator) {
		this(project, StateService.getInstance(project), indicator);
	}

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
	 * Refresh selected integrations after import and restart editor highlighting.
	 */
	public static void refreshAfterImport(Project project, ProgressIndicator indicator,
			Predicate<DependencyAssistant> filter) {

		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll();

		if (assistants.isEmpty()) {
			return;
		}

		StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, assistants.size() + 1);
		steps.setIndeterminate(false);
		List<CancellablePromise<Void>> promises = new ArrayList<>();
		ProjectStateIndexer indexer = new ProjectStateIndexer(project, indicator);
		for (DependencyAssistant assistant : assistants) {
			if (filter.test(assistant)) {
				promises.add(indexer.refreshAfterImport(assistant).onSuccess(__ -> steps.nextStep()));
			}
		}

		CompletableFuture<?> future = Promises.asCompletableFuture(
				Promises.all(promises).onSuccess(__ -> steps.nextStep()));

		try {
			ProgressIndicatorUtils.awaitWithCheckCanceled((Future<?>) future, indicator);
		} catch (ProcessCanceledException e) {
			throw e;
		} catch (CancellationException e) {
			throw new ProcessCanceledException(e);
		}

		DaemonCodeAnalyzer.getInstance(project).restart();
	}

	/**
	 * Refresh an integration after import.
	 * <p>Refreshes wait for smart mode and are coalesced per project and
	 * integration.
	 */
	public CancellablePromise<Void> refreshAfterImport(DependencyAssistant assistant) {

		return ReadAction.nonBlocking(() -> {
			updateAll(assistant);
			return (Void) null;
		}).inSmartMode(project)
				.coalesceBy(project, ProjectStateIndexer.class, assistant.getId())
				.submit(AppExecutorUtil.getAppExecutorService());
	}

	/**
	 * Populate dependency state with a read action around collection.
	 * <p>Completion and storage run on the calling thread outside that read action.
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
	 * Populate dependency state. The caller must provide a read action for
	 * collection.
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
	 * Scan dependencies and deliver a completed collector for each anchor.
	 * <p>This does not store results in {@link ProjectState} or update the cache.
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
	 * Refresh state owned by the file if its dependency context is available.
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

	/**
	 * Invoke the action for each anchor with an available context.
	 * <p>The callback owns collection, completion, and storage.
	 */
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
