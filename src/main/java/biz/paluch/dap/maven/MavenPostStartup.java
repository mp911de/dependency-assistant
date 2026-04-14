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

import biz.paluch.dap.MessageBundle;
import biz.paluch.dap.state.DependencyAssistantService;
import biz.paluch.dap.support.Notifications;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;
import kotlin.Unit;
import kotlin.coroutines.Continuation;
import org.jspecify.annotations.Nullable;

/**
 * Startup activity that reads the Maven build files and updates the project
 * state.
 *
 * @author Mark Paluch
 */
public class MavenPostStartup implements ProjectActivity {

	@Override
	public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {

		DumbService.getInstance(project).runWhenSmart(() -> {
			ProgressManager.getInstance()
					.run(new Task.Backgroundable(project, MessageBundle.message("maven.indexing.project"), false) {

						@Override
						public void run(ProgressIndicator indicator) {

							DependencyAssistantService service = DependencyAssistantService.getInstance(project);
							new UpdateProjectState(project, service).readAndUpdateAll(indicator);

							if (!service.getCache().hasReleases()) {
								Notifications.releaseMetadataUnavailable(project,
										ReleasesRetrievalTask::new);
							}
						}

					});
		});

		return null;
	}

}
