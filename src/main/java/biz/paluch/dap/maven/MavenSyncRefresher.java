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

package biz.paluch.dap.maven;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import biz.paluch.dap.ProjectStateIndexer;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import com.intellij.util.JavaCoroutines;
import com.intellij.util.concurrency.AppExecutorUtil;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jetbrains.idea.maven.project.MavenProject;
import org.jetbrains.idea.maven.project.MavenSyncListener;
import org.jspecify.annotations.Nullable;

/**
 * Refreshes dependency state after Maven imports and syncs.
 * <p>Both completion events are observed because a plain re-import may not
 * finish with a sync event. Nearby events are debounced. Events for other
 * projects are ignored, and disposal cancels pending refreshes.
 *
 * @author Mark Paluch
 * @see ProjectStateIndexer#refreshAfterImport
 */
@Service(Service.Level.PROJECT)
final class MavenSyncRefresher implements MavenSyncListener, Disposable {

	static final int REFRESH_DELAY_MS = 500;

	private static final Logger LOG = Logger.getInstance(MavenSyncRefresher.class);

	private final Project project;

	private final AtomicReference<ScheduledFuture<?>> scheduledRefresh = new AtomicReference<>();

	private MavenSyncRefresher(Project project) {
		this.project = project;
		project.getMessageBus().connect(this).subscribe(MavenSyncListener.Companion.getTOPIC(), this);
	}

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

		new Task.Backgroundable(project, MessageBundle.message("refresh-after-import.task"), true) {

			@Override
			public void run(ProgressIndicator indicator) {
				SettingsXmlLoader.invalidate(project);
				ProjectStateIndexer.refreshAfterImport(project, indicator,
						assistant -> assistant instanceof MavenAssistant);
			}

		}.queue();
	}

	/**
	 * Installs the listener before the Maven model is available.
	 * <p>Assistant preparation cannot do this because Maven support is reported
	 * only after a model has been imported.
	 */
	public static final class Installer implements ProjectActivity {

		@Override
		public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {
			return JavaCoroutines.suspendJava(jc -> {
				getInstance(project);
				jc.resume(Unit.INSTANCE);
			}, continuation);
		}

	}

}
