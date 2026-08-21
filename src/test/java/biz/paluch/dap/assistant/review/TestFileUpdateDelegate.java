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

package biz.paluch.dap.assistant.review;

import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.FileDependencyUpdater;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Test-variant of {@link FileUpdateDelegate}.
 *
 * @author Mark Paluch
 */
public class TestFileUpdateDelegate extends FileUpdateDelegate {

	public TestFileUpdateDelegate(Project project,
			FileDependencyUpdater updater) {
		super(project, updater::applyUpdates);
	}

	public void updateFile(VirtualFile file, DependencyUpdate update) {
		runCommand(() -> applyUpdates(file, DependencyUpdates.of(update)));
	}

	/**
	 * Update the {@link PsiFile file} with the given {@link FileDependencyUpdater
	 * updater} and {@link DependencyUpdate update}.
	 * @param file the file to update.
	 * @param updater updater to use.
	 * @param update the update to apply.
	 */
	public static void update(PsiFile file, FileDependencyUpdater updater, DependencyUpdate update) {
		new TestFileUpdateDelegate(file.getProject(), updater).updateFile(file.getVirtualFile(), update);
	}

}
