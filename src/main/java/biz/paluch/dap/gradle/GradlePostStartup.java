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
package biz.paluch.dap.gradle;

import biz.paluch.dap.MessageBundle;
import kotlin.Unit;
import kotlin.coroutines.Continuation;

import org.jspecify.annotations.Nullable;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbService;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.ProjectActivity;

/**
 * Startup activity that reads the Gradle build file and updates the project state.
 *
 * @author Mark Paluch
 */
public class GradlePostStartup implements ProjectActivity {

	@Override
	public @Nullable Object execute(Project project, Continuation<? super Unit> continuation) {

		DumbService.getInstance(project).runWhenSmart(() -> {
			ProgressManager.getInstance()
					.run(new Task.Backgroundable(project, MessageBundle.message("gradle.indexing.project"), false) {
						@Override
						public void run(ProgressIndicator indicator) {
							new UpdateProjectState(project).readAndUpdateAll(indicator);
						}
					});
		});

		return null;
	}

}
