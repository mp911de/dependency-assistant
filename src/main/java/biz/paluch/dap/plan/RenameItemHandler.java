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
 * Rename the single plan item published as
 * {@link UpgradePlanItem#RENAME_TARGET}.
 * <p>The plan rename is undoable. Optional name hints include implicit members.
 * Declining to remember a name preserves existing hints. Descriptor updates
 * require an existing descriptor.
 * <p>The handler is {@link DumbAware} because plan renaming uses no index.
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
	 * The tool window invokes {@link #invoke(Project, PsiElement[], DataContext)}.
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

	private static List<ArtifactId> getArtifactIds(UpgradePlanItem item) {

		List<ArtifactId> artifactIds = new ArrayList<>();
		for (ItemDependency member : item.getMembers()) {
			artifactIds.add(member.getArtifactId());
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

	private static void updateDependencyfile(Project project, UpgradePlanItem item, String name) {

		VirtualFile descriptor = DependencyfileService.getInstance(project).getDescriptor();
		if (descriptor == null) {
			return;
		}

		String commandName = MessageBundle.message("plan.rename.dependencyfile.command");
		WriteCommandAction.writeCommandAction(project)
				.withName(commandName)
				.run(() -> {
					PsiFile psiFile = BetterPsiManager.getInstance(project).findFile(descriptor);
					DependencyfileArtifacts.setName(project, psiFile, getArtifactIds(item), name);
				});
	}

}
