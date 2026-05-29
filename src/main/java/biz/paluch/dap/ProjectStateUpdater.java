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
import java.util.function.Consumer;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * Cross-ecosystem coordinator that owns the collect-complete-store flow for one
 * {@link DependencySource} run.
 *
 * <p>
 * The updater enumerates scan entries through the source, runs phase-one
 * collection for available entries, then applies phase-two completion via
 * {@link IntrospectedDependencies}, invalidates and stores the resulting
 * collectors per project, and finally lets the introspection update the cache.
 * Read-action wrapping, progress reporting, availability guards, and
 * cancellation are handled here so build-tool integrations contribute only the
 * source seam.
 *
 * @author Mark Paluch
 * @see DependencySource
 * @see IntrospectedDependencies
 */
public class ProjectStateUpdater {

	private final Project project;

	private final StateService service;

	/**
	 * Create an updater for the given project, using the project-scoped
	 * {@link StateService}.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 */
	public ProjectStateUpdater(Project project) {
		this(project, StateService.getInstance(project));
	}

	/**
	 * Create an updater for the given project and state service.
	 * @param project the IntelliJ project; must not be {@literal null}.
	 * @param service the state service backing this run; must not be {@literal null}.
	 */
	public ProjectStateUpdater(Project project, StateService service) {
		this.project = project;
		this.service = service;
	}

	/**
	 * Run a full population pass wrapped in a read action.
	 * @param source the source to run; must not be {@literal null}.
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 */
	public void readAndUpdateAll(DependencySource source, ProgressIndicator indicator) {
		ApplicationManager.getApplication().runReadAction(() -> updateAll(source, indicator));
	}

	/**
	 * Run a full population pass: enumerate, collect, complete, invalidate, store,
	 * update cache.
	 * @param source the source to run; must not be {@literal null}.
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 */
	public void updateAll(DependencySource source, ProgressIndicator indicator) {

		IntrospectedDependencies introspected = source.introspect(project);

		List<ActiveScan> active = collectPhase(source, introspected, indicator);

		for (ActiveScan scan : active) {
			introspected.complete(scan.collector);
		}

		for (ActiveScan scan : active) {
			ProjectState state = service.getProjectState(scan.entry.context().getProjectId());
			state.invalidateDependencies();
			state.setDependencies(scan.collector);
		}

		introspected.updateCache(service.getCache());
	}

	/**
	 * Run an aggregate scan and return one combined collector enriched with
	 * release sources and post-scan completion.
	 * <p>
	 * The aggregate result is not stored in the {@link ProjectState} and does not
	 * invoke the cache update.
	 * @param source the source to run; must not be {@literal null}.
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 * @return the aggregated collector.
	 */
	public DependencyCollector aggregate(DependencySource source, ProgressIndicator indicator) {

		DependencyCollector aggregate = new DependencyCollector();
		IntrospectedDependencies introspected = source.introspect(project);

		forEachAvailableEntry(source, indicator, entry -> {
			source.collect(entry, aggregate, introspected);
			aggregate.addAllReleaseSources(entry.context().getReleaseSources());
		});

		introspected.complete(aggregate);
		return aggregate;
	}

	/**
	 * Run a file-scoped invalidation: re-collect the state owned by the given file
	 * and route it through the same complete-store-cache flow.
	 * @param source the source to run; must not be {@literal null}.
	 * @param file the saved PSI file; must not be {@literal null}.
	 */
	public void invalidateFile(DependencySource source, PsiFile file) {

		DependencyScanEntry entry = source.createEntry(project, file);
		if (entry == null || entry.context().isAbsent()) {
			return;
		}

		IntrospectedDependencies introspected = source.introspect(project);
		DependencyCollector collector = new DependencyCollector();
		source.collect(entry, collector, introspected);
		introspected.complete(collector);

		ProjectState state = service.getProjectState(entry.context().getProjectId());
		state.invalidateDependencies();
		state.setDependencies(collector);

		introspected.updateCache(service.getCache());
	}

	private List<ActiveScan> collectPhase(DependencySource source, IntrospectedDependencies introspected,
			ProgressIndicator indicator) {

		List<ActiveScan> active = new ArrayList<>();

		forEachAvailableEntry(source, indicator, entry -> {
			DependencyCollector collector = new DependencyCollector();
			source.collect(entry, collector, introspected);
			active.add(new ActiveScan(entry, collector));
		});

		return active;
	}

	private void forEachAvailableEntry(DependencySource source, ProgressIndicator indicator,
			Consumer<DependencyScanEntry> action) {

		List<DependencyScanEntry> entries = source.enumerate(project);
		int processed = 0;

		for (DependencyScanEntry entry : entries) {

			indicator.checkCanceled();
			if (entry.context().isAvailable()) {
				action.accept(entry);
			}
			processed += 1;
			indicator.setFraction((double) processed / entries.size());
		}
	}

	private record ActiveScan(DependencyScanEntry entry, DependencyCollector collector) {
	}

}
