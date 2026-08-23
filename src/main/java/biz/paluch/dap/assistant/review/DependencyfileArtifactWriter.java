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

package biz.paluch.dap.assistant.review;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.assistant.check.DependencyUpgradeCandidate;
import biz.paluch.dap.metadata.ProjectMetadata;
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.rule.ArtifactPattern;
import biz.paluch.dap.rule.DependencyfileArtifacts;
import biz.paluch.dap.rule.DependencyfileArtifacts.ArtifactEntry;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.FileUtils;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * Writes artifact entries into the {@code artifacts} section of a
 * {@code dependencyfile.json}, creating the descriptor when none exists.
 *
 * <p>A regular row is written under its narrowest
 * {@link ArtifactPattern#keyFor(ArtifactId) pattern key} with its dependency
 * name. A group is written under the group name, as one wildcard entry (for
 * example {@code org.springframework.boot:spring-boot-starter-*}) when the
 * members share a groupId and a word-boundary prefix, otherwise as one entry
 * per member, all carrying the group name so the descriptor reproduces the
 * group (see {@link GroupByRule}). Existing entries get their name replaced;
 * the PSI edits themselves live in {@link DependencyfileArtifacts}.
 *
 * @author Mark Paluch
 */
class DependencyfileArtifactWriter {

	private final Project project;

	private final BetterPsiManager psiManager;

	private final DependencyfileService dependencyfileservice;

	private final ProjectMetadataService metadataService;

	public DependencyfileArtifactWriter(Project project) {
		this.project = project;
		this.psiManager = BetterPsiManager.getInstance(project);
		this.dependencyfileservice = DependencyfileService.getInstance(project);
		this.metadataService = ProjectMetadataService.getInstance(project);
	}

	/**
	 * Write the row's name into the active descriptor (creating it if absent), then
	 * open it in the editor with the caret selecting the first entry's {@code name}
	 * value.
	 */
	public void add(TableRow row) {

		try {
			VirtualFile descriptor = findOrCreateDescriptor();
			if (descriptor == null) {
				return;
			}

			List<ArtifactId> artifactIds = row.getUpgradeCandidates().stream()
					.map(DependencyUpgradeCandidate::getArtifactId).toList();

			String name = row.getDisplayName();
			TextRange selection = WriteCommandAction.writeCommandAction(project)
					.withName(MessageBundle.message("dialog.action.addToDependencyfile"))
					.compute(() -> DependencyfileArtifacts.setName(project, psiManager.findFile(descriptor),
							artifactIds, name));

			openInEditor(descriptor, selection);
		} catch (IOException | IncorrectOperationException ex) {
			Notifications.error(project, MessageBundle.message("dialog.action.addToDependencyfile"),
					Notifications.errorMessage(ex));
		}
	}

	private void openInEditor(VirtualFile descriptor, @Nullable TextRange selection) {

		int offset = selection != null ? selection.getStartOffset() : 0;
		Editor editor = FileEditorManager.getInstance(project)
				.openTextEditor(new OpenFileDescriptor(project, descriptor, offset), true);

		if (editor != null && selection != null) {
			editor.getCaretModel().moveToOffset(selection.getEndOffset());
		}
	}

	/**
	 * Open the project-local descriptor when it already exists, otherwise create a
	 * starter {@code .idea/dependencyfile.json} populated with the used artifact
	 * ids as unconstrained rules.
	 *
	 * @throws IOException when the descriptor cannot be created.
	 */
	void createOrOpen() throws IOException {

		TreeSet<ArtifactId> artifactIds = new TreeSet<>(ArtifactId.COMPARATOR);
		StateService.getInstance(project).doWithDependencies(dependency -> artifactIds.add(dependency.getArtifactId()));
		createOrOpen(artifactIds);
	}

	/**
	 * Open the project-local descriptor when it already exists, otherwise create a
	 * starter {@code .idea/dependencyfile.json} populated with the given artifact
	 * ids as unconstrained rules.
	 *
	 * @param artifactIds the project's known artifact ids to seed the descriptor
	 * with.
	 * @throws IOException when the descriptor cannot be created.
	 */
	void createOrOpen(Collection<? extends ArtifactId> artifactIds) throws IOException {

		VirtualFile existing = findProjectDescriptor();
		if (existing != null) {
			openInEditor(existing, null);
			return;
		}

		VirtualFile descriptor = newEmptyDescriptor();
		if (descriptor == null) {
			return;
		}

		WriteCommandAction.writeCommandAction(project)
				.withName(MessageBundle.message("dependencyfile.create.action"))
				.compute(() -> DependencyfileArtifacts.setNames(project, psiManager.findFile(descriptor),
						createEntries(artifactIds)));
		saveDocument(descriptor);
		openInEditor(descriptor, null);
	}

	private @Nullable VirtualFile findOrCreateDescriptor() throws IOException {

		VirtualFile descriptor = dependencyfileservice.getDescriptor();
		return descriptor != null ? descriptor : newEmptyDescriptor();
	}

	/**
	 * Search the project-local descriptor locations (project root then
	 * {@code .idea/}); the broader trusted-project discovery used for "add" does
	 * not apply when seeding a fresh project-local descriptor.
	 */
	private @Nullable VirtualFile findProjectDescriptor() {

		VirtualFile root = projectRoot();
		if (root == null) {
			return null;
		}

		for (String path : List.of(DependencyfileService.FILE_NAME,
				Project.DIRECTORY_STORE_FOLDER + "/" + DependencyfileService.FILE_NAME)) {
			VirtualFile file = root.findFileByRelativePath(path);
			if (file != null && file.isValid() && !file.isDirectory()) {
				return file;
			}
		}
		return null;
	}

	private @Nullable VirtualFile newEmptyDescriptor() throws IOException {

		VirtualFile root = projectRoot();
		if (!FileUtils.isDirectory(root)) {
			return null;
		}

		return WriteCommandAction.writeCommandAction(project)
				.withName(MessageBundle.message("dependencyfile.create.action"))
				.compute(() -> createDescriptor(root));
	}

	private @Nullable VirtualFile projectRoot() {

		String basePath = project.getBasePath();
		if (basePath == null) {
			return null;
		}
		return LocalFileSystem.getInstance().refreshAndFindFileByNioFile(Path.of(basePath));
	}

	private void saveDocument(VirtualFile file) {

		Document document = FileDocumentManager.getInstance().getDocument(file);
		if (document != null) {
			FileDocumentManager.getInstance().saveDocument(document);
		}
	}

	private VirtualFile createDescriptor(VirtualFile root) throws IOException {

		VirtualFile ideaDirectory = root.findChild(Project.DIRECTORY_STORE_FOLDER);
		if (ideaDirectory == null) {
			ideaDirectory = root.createChildDirectory(this, Project.DIRECTORY_STORE_FOLDER);
		}

		VirtualFile descriptor = ideaDirectory.createChildData(this, DependencyfileService.FILE_NAME);

		Document document = FileDocumentManager.getInstance().getDocument(descriptor);
		if (document == null) {
			throw new IOException("Cannot obtain document for " + descriptor.getPath());
		}

		document.setText("{\n  \"artifacts\": {}\n}\n");
		PsiDocumentManager.getInstance(project).commitDocument(document);
		return descriptor;
	}

	private Collection<ArtifactEntry> createEntries(Collection<? extends ArtifactId> artifactIds) {

		Set<ArtifactEntry> entries = new TreeSet<>();
		for (ArtifactId artifactId : artifactIds) {
			ProjectMetadata metadata = metadataService.getMetadata(artifactId);
			entries.add(ArtifactEntry.create(artifactId, metadata.getProjectName()));
		}

		return entries;
	}

}
