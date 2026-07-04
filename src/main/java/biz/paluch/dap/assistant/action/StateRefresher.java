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
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.progress.EmptyProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.util.concurrency.AppExecutorUtil;
import org.jspecify.annotations.Nullable;

/**
 * Project-level service that re-collects dependency state for changed build
 * files and restarts highlighting once the state is fresh.
 *
 * <p>Changed files accumulate in a pending map, stamped with the generation of
 * the {@link #refresh} call that recorded them. Refreshes are debounced: each
 * call re-arms a quiet-period timer of {@link #REFRESH_DELAY_MS} milliseconds,
 * so a typing burst submits no work at all until the edits pause. The timer
 * then submits a non-blocking read action that drains the map once all
 * documents are committed and indexes are available; overlapping submissions
 * coalesce. After a re-collection the daemon restarts so inspections that
 * consult cross-module state (such as version drift) re-run against fresh data
 * in all open editors, not only the edited file.
 *
 * @author Mark Paluch
 * @see FlushStateOnSave
 * @see FlushStateOnEdit
 */
@Service(Service.Level.PROJECT)
public final class StateRefresher implements Disposable {

	/**
	 * Quiet period after the last {@link #refresh} call before the pending files
	 * are re-collected.
	 */
	static final int REFRESH_DELAY_MS = 500;

	private final Project project;

	private final BetterPsiManager psiManager;

	private final Map<VirtualFile, Long> pending = new ConcurrentHashMap<>();

	private final AtomicLong generation = new AtomicLong();

	private final AtomicReference<ScheduledFuture<?>> scheduledRefresh = new AtomicReference<>();

	private StateRefresher(Project project) {
		this.project = project;
		this.psiManager = BetterPsiManager.getInstance(project);
	}

	/**
	 * Return the project-scoped refresher instance.
	 * @param project the IntelliJ project.
	 * @return the corresponding service instance.
	 */
	public static StateRefresher getInstance(Project project) {
		return project.getService(StateRefresher.class);
	}

	/**
	 * Schedule a dependency-state refresh for the given changed file.
	 * <p>Files that no integration supports are dropped during the refresh. The
	 * refresh runs asynchronously after a quiet period of {@link #REFRESH_DELAY_MS}
	 * milliseconds once all documents are committed; each call re-arms the timer
	 * and overlapping submissions coalesce into the latest one. When state was
	 * re-collected, highlighting restarts afterwards.
	 * @param file the changed files.
	 */
	public void refresh(VirtualFile file) {

		Long stamp = generation.incrementAndGet();
		pending.put(file, stamp);

		ScheduledFuture<?> scheduled = AppExecutorUtil.getAppScheduledExecutorService()
				.schedule(this::submitRefresh, REFRESH_DELAY_MS, TimeUnit.MILLISECONDS);
		cancelScheduled(scheduledRefresh.getAndSet(scheduled));
	}

	/**
	 * Schedule a dependency-state refresh for the given changed files.
	 * <p>Files that no integration supports are dropped during the refresh. The
	 * refresh runs asynchronously after a quiet period of {@link #REFRESH_DELAY_MS}
	 * milliseconds once all documents are committed; each call re-arms the timer
	 * and overlapping submissions coalesce into the latest one. When state was
	 * re-collected, highlighting restarts afterwards.
	 * @param files the changed files.
	 */
	public void refresh(Collection<VirtualFile> files) {

		if (files.isEmpty()) {
			return;
		}

		Long stamp = generation.incrementAndGet();
		for (VirtualFile file : files) {
			pending.put(file, stamp);
		}

		ScheduledFuture<?> scheduled = AppExecutorUtil.getAppScheduledExecutorService()
				.schedule(this::submitRefresh, REFRESH_DELAY_MS, TimeUnit.MILLISECONDS);
		cancelScheduled(scheduledRefresh.getAndSet(scheduled));
	}

	private void submitRefresh() {
		ReadAction.nonBlocking(this::refreshPending)
				.withDocumentsCommitted(project)
				.inSmartMode(project)
				.coalesceBy(this)
				.expireWith(this)
				.finishOnUiThread(ModalityState.nonModal(), this::restartHighlighting)
				.submit(AppExecutorUtil.getAppExecutorService());
	}

	private Collection<VirtualFile> refreshPending() {

		Map<VirtualFile, Long> snapshot = Map.copyOf(pending);
		Collection<VirtualFile> refreshed = refreshNow(snapshot.keySet());

		// remove(key, stamp) drops an entry only when its stamp is unchanged; a file
		// re-recorded by a concurrent refresh keeps the newer stamp and is
		// re-collected by the submission that recorded it.
		snapshot.forEach(pending::remove);

		return refreshed;
	}

	/**
	 * Re-collect dependency state for the given files. Must run inside a read
	 * action. Returns the files whose state was re-collected; empty when no given
	 * file is owned by an integration.
	 */
	public Collection<VirtualFile> refreshNow(Collection<VirtualFile> files) {

		if (files.isEmpty()) {
			return List.of();
		}

		List<PsiFile> psiFiles = new ArrayList<>(files.size());
		for (VirtualFile file : files) {
			psiManager.doWithFile(file, psiFiles::add);
		}

		Map<DependencyAssistant, List<PsiFile>> grouped = groupByOwner(psiFiles);
		if (grouped.isEmpty()) {
			return List.of();
		}

		ProjectStateIndexer indexer = new ProjectStateIndexer(project, new EmptyProgressIndicator());
		grouped.forEach((assistant, buildFiles) -> {

			if (buildFiles.size() > 10) {
				indexer.readAndUpdateAll(assistant);
			} else {
				for (PsiFile buildFile : buildFiles) {
					indexer.invalidate(assistant, buildFile);
				}
			}
		});

		return files;
	}

	/**
	 * Group the given files under every integration whose file-type check accepts
	 * them; files no integration supports are omitted.
	 */
	private static Map<DependencyAssistant, List<PsiFile>> groupByOwner(List<PsiFile> files) {

		List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll();
		Map<DependencyAssistant, List<PsiFile>> grouped = new LinkedHashMap<>();
		for (PsiFile file : files) {
			for (DependencyAssistant integration : assistants) {
				if (integration.supports(file)) {
					grouped.computeIfAbsent(integration, it -> new ArrayList<>()).add(file);
				}
			}
		}

		return grouped;
	}

	private void restartHighlighting(Collection<VirtualFile> refreshed) {

		if (refreshed.isEmpty()) {
			return;
		}

		DaemonCodeAnalyzer.getInstance(project).restart("Dependency state changed");
	}

	private static void cancelScheduled(@Nullable ScheduledFuture<?> scheduled) {

		if (scheduled != null) {
			scheduled.cancel(false);
		}
	}

	@Override
	public void dispose() {
		cancelScheduled(scheduledRefresh.getAndSet(null));
		pending.clear();
	}

}
