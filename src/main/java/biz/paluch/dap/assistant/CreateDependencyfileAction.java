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

package biz.paluch.dap.assistant;

import java.io.IOException;
import java.nio.file.Path;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.support.MessageBundle;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * Menu action that creates or opens a project-local
 * {@code dependencyfile.json}.
 *
 * @author Mark Paluch
 */
public class CreateDependencyfileAction extends AnAction implements DumbAware {

	private static final String[] DESCRIPTOR_PATHS = {
			DependencyfileService.FILE_NAME,
			Project.DIRECTORY_STORE_FOLDER + "/" + DependencyfileService.FILE_NAME
	};

	@Override
	public ActionUpdateThread getActionUpdateThread() {
		return ActionUpdateThread.BGT;
	}

	@Override
	public void update(AnActionEvent event) {

		Project project = event.getProject();
		Presentation presentation = event.getPresentation();

		presentation.setText(MessageBundle.message("dependencyfile.create.action"));
		presentation.setDescription(MessageBundle.message("dependencyfile.create.description"));
		presentation.setIcon(DependencyAssistantIcons.ICON);

		presentation.setEnabledAndVisible(project != null
				&& project.getBasePath() != null
				&& DependencyAssistantDispatcher.supports(project));
	}

	@Override
	public void actionPerformed(AnActionEvent event) {

		Project project = event.getProject();
		if (project == null) {
			return;
		}

		try {
			VirtualFile descriptor = findOrCreateDescriptor(project);
			if (descriptor != null) {
				FileEditorManager.getInstance(project).openFile(descriptor, true);
			}
		} catch (IOException | IncorrectOperationException ex) {
			Notifications.error(project,
					MessageBundle.message("dependencyfile.create.error.title"),
					MessageBundle.message("dependencyfile.create.error", Notifications.errorMessage(ex)));
		}
	}

	private static @Nullable VirtualFile findOrCreateDescriptor(Project project) throws IOException {

		VirtualFile root = projectRoot(project);
		if (root == null) {
			return null;
		}

		VirtualFile existing = findExistingDescriptor(root);
		if (existing != null) {
			return existing;
		}

		String content = DependencyfileTemplate.render(project);

		return WriteCommandAction.writeCommandAction(project)
				.withName(MessageBundle.message("dependencyfile.create.action"))
				.compute(() -> createDescriptor(project, root, content));
	}

	private static @Nullable VirtualFile projectRoot(Project project) {
		String basePath = project.getBasePath();
		if (basePath == null) {
			return null;
		}
		return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(basePath));
	}

	private static @Nullable VirtualFile findExistingDescriptor(VirtualFile root) {
		for (String path : DESCRIPTOR_PATHS) {
			VirtualFile file = root.findFileByRelativePath(path);
			if (isFile(file)) {
				return file;
			}
		}
		return null;
	}

	private static VirtualFile createDescriptor(Project project, VirtualFile root, String content) throws IOException {

		if (!root.isDirectory()) {
			throw new IOException("Project root is not a directory: " + root.getPath());
		}

		VirtualFile ideaDirectory = findOrCreateDirectory(root, Project.DIRECTORY_STORE_FOLDER);
		VirtualFile descriptor = findOrCreateFile(ideaDirectory, DependencyfileService.FILE_NAME);

		writeFormattedJson(project, descriptor, content);

		return descriptor;
	}

	private static VirtualFile findOrCreateDirectory(VirtualFile parent, String name) throws IOException {

		VirtualFile child = parent.findChild(name);

		if (child == null) {
			return parent.createChildDirectory(CreateDependencyfileAction.class, name);
		}

		if (!child.isDirectory()) {
			throw new IOException(child.getPath() + " is not a directory");
		}

		return child;
	}

	private static VirtualFile findOrCreateFile(VirtualFile parent, String name) throws IOException {

		VirtualFile child = parent.findChild(name);

		if (child == null) {
			return parent.createChildData(CreateDependencyfileAction.class, name);
		}

		if (child.isDirectory()) {
			throw new IOException(child.getPath() + " is a directory");
		}

		return child;
	}

	private static void writeFormattedJson(Project project, VirtualFile file, String content) throws IOException {

		Document document = FileDocumentManager.getInstance().getDocument(file);
		if (document == null) {
			throw new IOException("Cannot obtain document for " + file.getPath());
		}

		PsiDocumentManager psiDocuments = PsiDocumentManager.getInstance(project);

		document.setText(content);
		psiDocuments.commitDocument(document);

		PsiFile psiFile = psiDocuments.getPsiFile(document);
		if (psiFile == null) {
			throw new IOException("Cannot obtain PSI file for " + file.getPath());
		}

		CodeStyleManager.getInstance(project).reformat(psiFile);

		psiDocuments.commitDocument(document);
		FileDocumentManager.getInstance().saveDocument(document);
	}


	private static boolean isFile(@Nullable VirtualFile file) {
		return file != null && file.isValid() && !file.isDirectory();
	}

}
