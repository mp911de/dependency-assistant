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

package biz.paluch.dap.maven;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StepsProgressIndicator;
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.concurrency.AppExecutorUtil;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.concurrency.CancellablePromise;
import org.jetbrains.concurrency.Promises;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenSyncListener;
import org.jspecify.annotations.Nullable;

/**
 * Project service re-indexing the Maven assistant's project state when a Maven
 * sync settles, so members resolved from the effective dependency management
 * surface in highlighting.
 *
 * <p>Both terminal edges of the Maven pipeline schedule a refresh:
 * {@link #importFinished} once the workspace model has been committed, and
 * {@link #syncFinished} once the surrounding sync has settled. An import is
 * nested inside a sync, so a sync reports the former first and the latter last,
 * while a plain re-import (for example folder resolution) reports only the
 * former. Refreshes are debounced by {@link #REFRESH_DELAY_MS} milliseconds and
 * each event re-arms the timer, so one sync re-indexes once, against the state
 * left by its last edge, and a sync that never reaches {@link #syncFinished}
 * still refreshes.
 *
 * <p>The topic is published on the application message bus and broadcast to
 * child buses, so events for other projects arrive here as well and are
 * filtered by project identity. The subscription is bound to this service and
 * therefore to the project lifetime.
 *
 * @author Mark Paluch
 * @see ProjectStateIndexer#refreshAfterImport
 */
@Service(Service.Level.PROJECT)
final class MavenSyncRefresher implements MavenSyncListener, Disposable {

	/**
	 * Quiet period after the last sync notification before the project state is
	 * re-indexed.
	 */
	static final int REFRESH_DELAY_MS = 500;

	private static final Logger LOG = Logger.getInstance(MavenSyncRefresher.class);

	private final Project project;

	private final AtomicReference<ScheduledFuture<?>> scheduledRefresh = new AtomicReference<>();

	private MavenSyncRefresher(Project project) {
		this.project = project;
		project.getMessageBus().connect(this).subscribe(MavenSyncListener.Companion.getTOPIC(), this);
	}

	/**
	 * Return the project-scoped refresher instance, subscribing it to Maven sync
	 * notifications on first access.
	 *
	 * @param project the IntelliJ project.
	 * @return the corresponding service instance.
	 */
	public static MavenSyncRefresher getInstance(Project project) {
		return project.getService(MavenSyncRefresher.class);
	}

	@Override
	public void importFinished(Project syncedProject, Collection<MavenProject> importedProjects,
			List<? extends Module> newModules) {
		scheduleRefresh(syncedProject, "import finished");
	}

	@Override
	public void syncFinished(Project syncedProject) {
		scheduleRefresh(syncedProject, "sync finished");
	}

	@Override
	public void dispose() {
		cancelScheduled(scheduledRefresh.getAndSet(null));
	}

	private void scheduleRefresh(Project syncedProject, String edge) {

		if (!project.equals(syncedProject)) {
			return;
		}

		LOG.debug("Maven " + edge + ", scheduling project state refresh");

		ScheduledFuture<?> scheduled = AppExecutorUtil.getAppScheduledExecutorService()
				.schedule(() -> refresh(), REFRESH_DELAY_MS, TimeUnit.MILLISECONDS);
		cancelScheduled(scheduledRefresh.getAndSet(scheduled));
	}

	private static void cancelScheduled(@Nullable ScheduledFuture<?> scheduled) {

		if (scheduled != null) {
			scheduled.cancel(false);
		}
	}

	private void refresh() {

		if (project.isDisposed()) {
			return;
		}

		LOG.debug("Refreshing Maven project state");

		new Task.Backgroundable(project, MessageBundle.message("refreshAfterImport.task"), true) {

			@Override
			public void run(ProgressIndicator indicator) {
				SettingsXmlLoader.invalidate(project);
				List<DependencyAssistant> assistants = DependencyAssistantDispatcher.findAll();

				if (assistants.isEmpty()) {
					return;
				}

				StepsProgressIndicator steps = StepsProgressIndicator.forSteps(indicator, assistants.size());
				steps.setIndeterminate(false);
				List<CancellablePromise<Void>> promises = new ArrayList<>();
				ProjectStateIndexer indexer = new ProjectStateIndexer(project, indicator);
				for (DependencyAssistant assistant : assistants) {
					if (assistant instanceof MavenAssistant) {
						promises.add(indexer.refreshAfterImport(assistant).onSuccess(__ -> steps.nextStep()));
					}
				}

				try {
					Promises.all(promises).onSuccess(__ -> steps.nextStep()).blockingGet(0);
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

		}.queue();
	}

	/**
	 * Startup activity arming the refresher for a project.
	 * <p>Registered as a startup activity rather than driven from
	 * {@link DependencyAssistant#prepare(Project)} because
	 * {@link MavenAssistant#supports(Project)} reports {@literal false} until the
	 * Maven model has been read, which is exactly the state the sync subscription
	 * must recover from.
	 */
	public static final class Installer implements ProjectActivity {

		@Override
		public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {
			getInstance(project);
			return null;
		}

	}

}
