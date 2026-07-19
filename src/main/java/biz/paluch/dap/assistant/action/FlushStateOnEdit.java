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

import biz.paluch.dap.DependencyAssistantDispatcher;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiTreeChangeAdapter;
import com.intellij.psi.PsiTreeChangeEvent;
import org.jetbrains.annotations.NotNull;

/**
 * PSI change listener that funnels edits of supported build files into the
 * {@link StateRefresher}.
 *
 * @author Mark Paluch
 * @see FlushStateOnSave
 */
public class FlushStateOnEdit extends PsiTreeChangeAdapter {

	@Override
	public void childAdded(@NotNull PsiTreeChangeEvent event) {
		refreshOwningState(event);
	}

	@Override
	public void childRemoved(@NotNull PsiTreeChangeEvent event) {
		refreshOwningState(event);
	}

	@Override
	public void childReplaced(@NotNull PsiTreeChangeEvent event) {
		refreshOwningState(event);
	}

	@Override
	public void childrenChanged(@NotNull PsiTreeChangeEvent event) {
		refreshOwningState(event);
	}

	@Override
	public void childMoved(@NotNull PsiTreeChangeEvent event) {
		refreshOwningState(event);
	}

	@Override
	public void propertyChanged(@NotNull PsiTreeChangeEvent event) {
		refreshOwningState(event);
	}

	private static void refreshOwningState(PsiTreeChangeEvent event) {

		PsiFile file = event.getFile();
		if (file == null || !file.isPhysical() || !DependencyAssistantDispatcher.supports(file)) {
			return;
		}

		VirtualFile virtualFile = file.getVirtualFile();
		if (virtualFile == null) {
			return;
		}

		StateRefresher.getInstance(file.getProject()).refresh(virtualFile);
	}

}
