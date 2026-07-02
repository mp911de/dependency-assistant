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

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.assistant.check.UpgradeCandidate;
import biz.paluch.dap.assistant.check.UpgradeGroup;
import biz.paluch.dap.rule.ArtifactPattern;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.json.psi.JsonElementGenerator;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.util.IncorrectOperationException;
import org.jspecify.annotations.Nullable;

/**
 * Writes artifact entries into the {@code artifacts} section of a
 * {@code dependencyfile.json}, creating the descriptor when none exists. The
 * single home for descriptor creation, entry insertion, and opening: the
 * dependency-check dialog adds one row via {@link #add(UpgradeCandidate)} and
 * the "Create Dependencyfile" action seeds a starter descriptor via
 * {@link #createOrOpen(Collection)}.
 *
 * <p>For a regular row the entry key is the narrowest
 * {@link ArtifactPattern#keyFor(ArtifactId) pattern key}. For an
 * {@link UpgradeGroup} whose members share a groupId and a word-boundary common
 * prefix, a single wildcard entry (for example
 * {@code org.springframework.boot:spring-boot-starter-*}) covers them all;
 * otherwise one entry per member is written. Entries already present are
 * skipped, and the new key is inserted before the first existing key that sorts
 * after it (case-insensitive), so the descriptor stays loosely ordered without
 * a full rewrite.
 *
 * @author Mark Paluch
 */
class DependencyfileArtifactWriter {

	private final Project project;

	private final DependencyfileService dependencyfileservice;

	public DependencyfileArtifactWriter(Project project) {
		this.project = project;
		this.dependencyfileservice = DependencyfileService.getInstance(project);
	}

	/**
	 * Return whether adding the candidate would write at least one new entry: when
	 * no descriptor exists yet, or when at least one computed entry key is not
	 * already declared in the active descriptor.
	 *
	 * @param candidate the right-clicked row.
	 * @return {@literal true} if the add action has something to write;
	 * {@literal false} otherwise.
	 */
	public boolean canAdd(UpgradeCandidate candidate) {

		VirtualFile descriptor = dependencyfileservice.getDescriptor();
		if (descriptor == null) {
			return true;
		}

		Set<String> existing = existingKeys(descriptor);
		return entries(candidate).stream().anyMatch(entry -> !existing.contains(entry.key()));
	}

	/**
	 * Add the candidate's entries to the active descriptor (creating it if absent),
	 * then open it in the editor with the caret selecting the first new entry's
	 * {@code name} value.
	 *
	 * @param candidate the right-clicked row.
	 */
	public void add(UpgradeCandidate candidate) {

		try {
			VirtualFile descriptor = findOrCreateDescriptor();
			if (descriptor == null) {
				return;
			}

			TextRange selection = WriteCommandAction.writeCommandAction(project)
					.withName(MessageBundle.message("dialog.action.addToDependencyfile"))
					.compute(() -> insertEntries(project, PsiManager.getInstance(project).findFile(descriptor),
							entries(candidate)));

			openInEditor(descriptor, selection);
		} catch (IOException | IncorrectOperationException ex) {
			Notifications.error(project, MessageBundle.message("dialog.action.addToDependencyfile"),
					Notifications.errorMessage(ex));
		}
	}

	/**
	 * Insert the entries not already present into the descriptor's
	 * {@code artifacts} object (creating that object when absent), reformat the
	 * file, and return the range covering the first inserted entry's {@code name}
	 * value for the caret.
	 *
	 * @return the {@code name}-value range to select, or {@literal null} when the
	 * file is not a JSON object or every entry was already present.
	 */
	static @Nullable TextRange insertEntries(Project project, @Nullable PsiFile psiFile, List<ArtifactEntry> entries) {

		if (!(psiFile instanceof JsonFile jsonFile) || !(jsonFile.getTopLevelValue() instanceof JsonObject root)) {
			return null;
		}

		JsonElementGenerator generator = new JsonElementGenerator(project);
		JsonObject artifacts = artifactsObject(root, generator);

		Set<String> existing = new HashSet<>();
		for (JsonProperty property : artifacts.getPropertyList()) {
			existing.add(property.getName());
		}

		String firstKey = null;
		for (ArtifactEntry entry : entries) {
			if (!existing.add(entry.key())) {
				continue;
			}
			insertSorted(artifacts, entry, generator);
			if (firstKey == null) {
				firstKey = entry.key();
			}
		}

		if (firstKey == null) {
			return null;
		}

		CodeStyleManager.getInstance(project).reformat(jsonFile);

		return nameValueRange(artifacts, firstKey);
	}

	/**
	 * Return the active descriptor object's {@code artifacts} value, creating an
	 * empty {@code artifacts} object when the descriptor does not declare one.
	 */
	private static JsonObject artifactsObject(JsonObject root, JsonElementGenerator generator) {

		JsonProperty artifacts = root.findProperty("artifacts");
		if (artifacts != null && artifacts.getValue() instanceof JsonObject object) {
			return object;
		}

		JsonProperty created = generator.createProperty("artifacts", "{}");
		JsonProperty inserted = (JsonProperty) insertProperty(root, created, null, generator);
		return (JsonObject) inserted.getValue();
	}

	private static void insertSorted(JsonObject artifacts, ArtifactEntry entry, JsonElementGenerator generator) {

		JsonProperty property = generator.createProperty(entry.key(),
				"{\"name\": \"" + StringUtil.escapeStringCharacters(entry.name()) + "\"}");

		JsonProperty anchor = null;
		for (JsonProperty sibling : artifacts.getPropertyList()) {
			if (sibling.getName().compareToIgnoreCase(entry.key()) > 0) {
				anchor = sibling;
				break;
			}
		}

		insertProperty(artifacts, property, anchor, generator);
	}

	/**
	 * Insert {@code property} into {@code object}: before {@code anchor} when
	 * given, otherwise appended after the last property; an empty object receives
	 * it directly after the opening brace. The required comma is added on the side
	 * that borders an existing property.
	 */
	private static PsiElement insertProperty(JsonObject object, JsonProperty property, @Nullable JsonProperty anchor,
			JsonElementGenerator generator) {

		List<JsonProperty> properties = object.getPropertyList();
		if (properties.isEmpty()) {
			return object.addAfter(property, object.getFirstChild());
		}

		if (anchor == null) {
			PsiElement added = object.addAfter(property, properties.getLast());
			object.addBefore(generator.createComma(), added);
			return added;
		}

		PsiElement added = object.addBefore(property, anchor);
		object.addAfter(generator.createComma(), added);
		return added;
	}

	private static @Nullable TextRange nameValueRange(JsonObject artifacts, String key) {

		for (JsonProperty property : artifacts.getPropertyList()) {
			if (!property.getName().equals(key)) {
				continue;
			}
			if (property.getValue() instanceof JsonObject value
					&& value.findProperty("name") instanceof JsonProperty name
					&& name.getValue() instanceof JsonStringLiteral literal) {
				TextRange range = literal.getTextRange();
				return new TextRange(range.getStartOffset() + 1, range.getEndOffset() - 1);
			}
		}
		return null;
	}

	private void openInEditor(VirtualFile descriptor, @Nullable TextRange selection) {

		int offset = selection != null ? selection.getStartOffset() : 0;
		Editor editor = FileEditorManager.getInstance(project)
				.openTextEditor(new OpenFileDescriptor(project, descriptor, offset), true);

		if (editor != null && selection != null) {
			editor.getCaretModel().moveToOffset(selection.getEndOffset());
			editor.getSelectionModel().setSelection(selection.getStartOffset(), selection.getEndOffset());
		}
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
				.compute(() -> insertEntries(project, PsiManager.getInstance(project).findFile(descriptor),
						templateEntries(artifactIds)));
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
		if (root == null || !root.isDirectory()) {
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

	private Set<String> existingKeys(VirtualFile descriptor) {

		PsiFile psiFile = PsiManager.getInstance(project).findFile(descriptor);
		if (!(psiFile instanceof JsonFile jsonFile) || !(jsonFile.getTopLevelValue() instanceof JsonObject root)) {
			return Set.of();
		}

		if (!(root.findProperty("artifacts") instanceof JsonProperty artifacts)
				|| !(artifacts.getValue() instanceof JsonObject object)) {
			return Set.of();
		}

		Set<String> keys = new HashSet<>();
		for (JsonProperty property : object.getPropertyList()) {
			keys.add(property.getName());
		}
		return keys;
	}

	/**
	 * Compute the starter entries seeding a fresh descriptor: one unconstrained
	 * rule per artifact id, keyed by its {@link ArtifactPattern#keyFor(ArtifactId)
	 * pattern key} and named after that key (npm-style {@code @scope} prefixes
	 * stripped). Duplicate keys collapse and the result is ordered.
	 */
	static List<ArtifactEntry> templateEntries(Collection<? extends ArtifactId> artifactIds) {

		TreeSet<String> patterns = new TreeSet<>();
		for (ArtifactId artifactId : artifactIds) {
			patterns.add(ArtifactPattern.keyFor(artifactId));
		}

		return patterns.stream()
				.map(pattern -> new ArtifactEntry(pattern, pattern.startsWith("@") ? pattern.substring(1) : pattern))
				.toList();
	}

	/**
	 * Compute the entries to write for the candidate: a single wildcard entry for a
	 * group with a shared groupId and word-boundary prefix, one entry per member as
	 * a fallback, or a single entry for a regular row.
	 */
	static List<ArtifactEntry> entries(UpgradeCandidate candidate) {

		if (candidate instanceof UpgradeGroup group) {

			String wildcardKey = wildcardKey(group.getMembers());
			if (wildcardKey != null) {
				return List.of(new ArtifactEntry(wildcardKey, group.getDependencyName()));
			}

			List<ArtifactEntry> entries = new ArrayList<>(group.getMembers().size());
			for (UpgradeCandidate member : group.getMembers()) {
				entries.add(new ArtifactEntry(ArtifactPattern.keyFor(member.getArtifactId()),
						member.getDependencyName()));
			}
			return entries;
		}

		return List.of(new ArtifactEntry(ArtifactPattern.keyFor(candidate.getArtifactId()),
				candidate.getDependencyName()));
	}

	/**
	 * Return the {@code groupId:prefix*} wildcard key for the members, or
	 * {@literal null} when they do not share a groupId or their artifactIds have no
	 * common prefix ending on a {@code -} or {@code .} word boundary.
	 */
	private static @Nullable String wildcardKey(List<UpgradeCandidate> members) {

		String groupId = members.getFirst().getArtifactId().groupId();
		List<String> artifactIds = new ArrayList<>(members.size());
		for (UpgradeCandidate member : members) {
			if (!groupId.equals(member.getArtifactId().groupId())) {
				return null;
			}
			artifactIds.add(member.getArtifactId().artifactId());
		}

		String commonPrefix = StringUtils.longestCommonPrefix(artifactIds);
		int separator = Math.max(commonPrefix.lastIndexOf('-'), commonPrefix.lastIndexOf('.'));
		if (separator < 0) {
			return null;
		}

		return groupId + ":" + commonPrefix.substring(0, separator + 1) + "*";
	}

	record ArtifactEntry(String key, String name) {
	}

}
