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

package biz.paluch.dap.assistant.action;

import biz.paluch.dap.DependencyAssistantDispatcher;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.BulkAwareDocumentListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;

/**
 * Document listener that schedules a {@link StateRefresher} pass for edits to
 * physical build files recognized by a dependency integration.
 *
 * <p>The listener observes every document change through the
 * {@link EditorFactory#getEventMulticaster() editor event multicaster} and
 * resolves only PSI already cached for its project, so documents of other
 * projects and files without loaded PSI are ignored. Bulk updates report a
 * single change once finished. Each change re-arms the refresher's quiet-period
 * timer, so a burst of keystrokes and the auto-save that follows collapse into
 * one re-collection.
 *
 * <p>Filtering runs inside the write action that changed the document and is
 * limited to cached-PSI lookup and file-type recognition. Ownership resolution
 * happens later in the refresher.
 *
 * @author Mark Paluch
 * @see StateRefresher
 */
public class FlushStateOnEdit implements BulkAwareDocumentListener.Simple {

	private final Project project;

	private final PsiDocumentManager documentManager;

	private final StateRefresher stateRefresher;

	FlushStateOnEdit(Project project) {
		this.project = project;
		this.documentManager = PsiDocumentManager.getInstance(project);
		this.stateRefresher = StateRefresher.getInstance(project);
	}

	/**
	 * Register an edit listener for the given project.
	 *
	 * <p>The listener lives as long as the project's {@link StateRefresher} and is
	 * removed when the service is disposed.
	 *
	 * @param project the project to observe.
	 */
	public static void install(Project project) {

		FlushStateOnEdit listener = new FlushStateOnEdit(project);
		EditorFactory.getInstance().getEventMulticaster()
				.addDocumentListener(listener, listener.getStateRefresher());
	}

	StateRefresher getStateRefresher() {
		return stateRefresher;
	}

	@Override
	public void afterDocumentChange(Document document) {

		if (project.isDisposed()) {
			return;
		}

		PsiFile file = documentManager.getCachedPsiFile(document);
		if (file == null || !file.isPhysical() || !DependencyAssistantDispatcher.supports(file)) {
			return;
		}

		VirtualFile virtualFile = file.getVirtualFile();
		if (virtualFile == null) {
			return;
		}

		stateRefresher.refresh(virtualFile);
	}

}
