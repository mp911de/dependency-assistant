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

package biz.paluch.dap.gradle.wrapper;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.function.Consumer;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.state.ProjectId;
import biz.paluch.dap.state.ProjectState;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.ProjectScope;

/**
 * Service to read Gradle wrapper files and update dependency state.
 *
 * @author Mark Paluch
 */
class UpdateGradleWrapperPropertiesProjectState {

	private final Project project;

	private final StateService service;

	private final PsiManager psiManager;

	private final Set<ReleaseSource> releaseSources = Collections.synchronizedSet(new LinkedHashSet<>());

	UpdateGradleWrapperPropertiesProjectState(Project project) {
		this(project, PsiManager.getInstance(project), StateService.getInstance(project));
	}

	UpdateGradleWrapperPropertiesProjectState(Project project, PsiManager psiManager, StateService service) {
		this.project = project;
		this.psiManager = psiManager;
		this.service = service;
	}

	Set<ReleaseSource> getReleaseSources() {
		return releaseSources;
	}

	void readAndUpdateAll(ProgressIndicator indicator) {
		ApplicationManager.getApplication().runReadAction(() -> updateAll(indicator));
	}

	void updateAll(ProgressIndicator indicator) {
		doWithAllFiles(this::update, indicator);
	}

	DependencyCollector getAllDependencies(ProgressIndicator indicator) {

		DependencyCollector collector = new DependencyCollector();
		GradleWrapperParser parser = new GradleWrapperParser(collector);
		doWithAllFiles(file -> {
			if (file instanceof PropertiesFile propertiesFile) {
				parser.collect(propertiesFile);
			}
		}, indicator);

		return collector;
	}

	void doWithAllFiles(Consumer<PsiFile> action, ProgressIndicator indicator) {

		double current = 0;
		Collection<VirtualFile> files = FilenameIndex.getVirtualFilesByName(GradleWrapperUtils.WRAPPER_FILENAME,
				ProjectScope.getProjectScope(project));
		for (VirtualFile file : files) {
			indicator.checkCanceled();
			indicator.setText(MessageBundle.message("action.index-dependencies.indexing.assistant",
					"Gradle Wrapper"));

			PsiFile psiFile = psiManager.findFile(file);
			if (GradleWrapperUtils.isWrapperFile(psiFile)) {
				action.accept(psiFile);
			}

			current += 1;
			indicator.setFraction(files.isEmpty() ? 1 : current / files.size());
		}
	}

	DependencyCollector update(PsiFile file) {

		if (!GradleWrapperUtils.isWrapperFile(file) || !(file instanceof PropertiesFile propertiesFile)) {
			return new DependencyCollector();
		}

		ProjectId projectId = GradleWrapperUtils.createProjectId(file.getVirtualFile());
		DependencyCollector collector = new DependencyCollector();
		new GradleWrapperParser(collector).collect(propertiesFile);
		ProjectState projectState = this.service.getProjectState(projectId);
		projectState.invalidateDependencies();
		projectState.setDependencies(collector);

		this.releaseSources.addAll(collector.getReleaseSources());

		return collector;
	}

}
