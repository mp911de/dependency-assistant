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

package biz.paluch.dap.plan;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.CoordinateShape;
import biz.paluch.dap.metadata.ProjectMetadata;
import biz.paluch.dap.metadata.ProjectMetadataService;
import biz.paluch.dap.rule.DependencyfileArtifacts;
import biz.paluch.dap.rule.DependencyfileService;
import biz.paluch.dap.state.ApplicationSettings;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.TitledHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.refactoring.rename.RenameHandler;

/**
 * Platform rename entry point for Upgrade Plan items: makes Shift+F6, Refactor
 * | Rename, and the plan popup's Rename entry open a {@link RenameItemDialog}
 * for the single selected top-level item and apply the accepted name as one
 * undoable plan transition.
 *
 * <p>Availability is a single read of {@link UpgradePlanItem#RENAME_TARGET},
 * which the Upgrade Plan panel publishes only for exactly one selected
 * top-level row while the plan is idle. The handler is {@link DumbAware}
 * because the platform filters renamers by dumb-awareness and the rename
 * touches no index.
 *
 * <p>Beyond the plan transition the handler acts on the dialog's two choices:
 * "remember name" stores the name as a
 * {@link ApplicationSettings#addNameHint(String, List) name hint} for the
 * item's explicit-member constellation (or drops the hint when unchecked, or
 * when the name is one of the item's derived names), and "update
 * dependencyfile.json" writes the name into the project's descriptor. Both
 * choices are persisted in {@link ApplicationSettings} as the preselection for
 * the next rename.
 *
 * @author Mark Paluch
 */
public class RenameItemHandler implements RenameHandler, TitledHandler, DumbAware {

	@Override
	public boolean isAvailableOnDataContext(DataContext dataContext) {
		return dataContext.getData(UpgradePlanItem.RENAME_TARGET) != null;
	}

	@Override
	public String getActionTitle() {
		return MessageBundle.message("plan.rename.title");
	}

	/**
	 * Never reached from the tool window: without an editor in the data context the
	 * platform dispatches to {@link #invoke(Project, PsiElement[], DataContext)}.
	 */
	@Override
	public void invoke(Project project, Editor editor, PsiFile file, DataContext dataContext) {
	}

	@Override
	public void invoke(Project project, PsiElement[] elements, DataContext dataContext) {

		UpgradePlanItem item = dataContext.getData(UpgradePlanItem.RENAME_TARGET);
		if (item == null) {
			return;
		}

		ApplicationSettings settings = ApplicationSettings.getInstance();
		boolean descriptorAvailable = DependencyfileService.getInstance(project).getDescriptor() != null;

		String currentName = item.getDisplayName();
		RenameItemDialog dialog = new RenameItemDialog(project, currentName, getSuggestions(project, item));
		dialog.setRememberName(settings.isRememberRenamedNames());
		dialog.setUpdateDependencyfile(descriptorAvailable, settings.isUpdateDependencyfileOnRename());
		if (!dialog.showAndGet()) {
			return;
		}

		settings.setRememberRenamedNames(dialog.isRememberName());
		if (descriptorAvailable) {
			settings.setUpdateDependencyfileOnRename(dialog.isUpdateDependencyfile());
		}

		String newName = dialog.getEnteredName();
		if (newName == null) {
			return;
		}

		if (!newName.equals(currentName)) {
			UpgradePlanService.getInstance(project).renameItem(item, newName, dialog.isRememberName());
		}

		if (dialog.isUpdateDependencyfile()) {
			updateDependencyfile(project, item, newName);
		}
	}

	/**
	 * Coordinates of the item's explicit members, excluding implicit group members.
	 */
	private static List<ArtifactId> getArtifactIds(UpgradePlanItem item) {

		List<ArtifactId> artifactIds = new ArrayList<>();
		for (ItemDependency member : item.getMembers()) {
			if (!member.isImplicit()) {
				artifactIds.add(member.getArtifactId());
			}
		}
		return artifactIds;
	}

	private static Set<String> getSuggestions(Project project, UpgradePlanItem item) {

		ProjectMetadataService service = ProjectMetadataService.getInstance(project);

		Set<String> suggestions = new LinkedHashSet<>();
		List<String> artifactIds = new ArrayList<>();

		suggestions.add(item.getDisplayName());

		for (ItemDependency itemDependency : item) {

			ArtifactId artifactId = itemDependency.getArtifactId();
			ProjectMetadata metadata = service.getMetadata(artifactId);
			if (StringUtils.hasText(metadata.getProjectName())) {
				suggestions.add(metadata.getProjectName());
			}
			artifactIds.add(artifactId.artifactId());
			suggestions.add(artifactId.artifactId());
		}

		CoordinateShape shape = CoordinateShape.of(artifactIds);

		for (ItemDependency itemDependency : item) {
			String derived = shape.deriveGroupName(itemDependency.getArtifactId().groupId());
			if (StringUtils.hasText(derived)) {
				suggestions.add(derived);
			}
		}
		return suggestions;
	}

	/**
	 * Set {@code name} on the descriptor entries covering the item's explicit
	 * members (one wildcard entry for a shared groupId and word-boundary prefix,
	 * otherwise one entry per member), inserting entries that do not exist yet.
	 * Never creates the descriptor and does not open it in the editor.
	 */
	private static void updateDependencyfile(Project project, UpgradePlanItem item, String name) {

		VirtualFile descriptor = DependencyfileService.getInstance(project).getDescriptor();
		if (descriptor == null) {
			return;
		}

		String commandName = MessageBundle.message("plan.rename.dependencyfile.command");
		WriteCommandAction.writeCommandAction(project)
				.withName(commandName)
				.run(() -> {
					PsiFile psiFile = BetterPsiManager.getInstance(project)
							.findFile(descriptor);
					List<DependencyfileArtifacts.ArtifactEntry> entries = DependencyfileArtifacts
							.entries(getArtifactIds(item), name);
					DependencyfileArtifacts.setNames(project, psiFile,
							entries);
				});

	}

}
