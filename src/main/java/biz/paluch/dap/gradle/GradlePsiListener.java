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

import biz.paluch.dap.state.DependencyAssistantService;

import java.util.List;

import org.jspecify.annotations.Nullable;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.AsyncFileListener;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import com.intellij.psi.PsiTreeChangeListener;

/**
 * Listener that updates the dependency state for Gradle build files.
 *
 * @author Mark Paluch
 */
public class GradlePsiListener extends PsiTreeChangeAdapter implements PsiTreeChangeListener, AsyncFileListener {

	private final UpdateProjectState updateProjectState;

	public GradlePsiListener(Project project) {

		VirtualFileManager vfm = VirtualFileManager.getInstance();
		vfm.addAsyncFileListener(this, () -> {});

		PsiManager psiManager = PsiManager.getInstance(project);
		psiManager.addPsiTreeChangeListener(this, () -> {});

		this.updateProjectState = new UpdateProjectState(project, DependencyAssistantService.getInstance(project));
	}

	/**
	 * Returns the service instance for the given project.
	 */
	public static GradlePsiListener getInstance(Project project) {
		return project.getService(GradlePsiListener.class);
	}

	@Override
	public @Nullable ChangeApplier prepareChange(List<? extends VFileEvent> list) {
		return null;
	}

	@Override
	public void childrenChanged(PsiTreeChangeEvent event) {

		PsiFile file = event.getFile();
		if (GradleUtils.isGradleFile(file)) {
			updateProjectState.readAndUpdate(file);
		}
	}

	@Override
	public void propertyChanged(PsiTreeChangeEvent event) {

	}

}
