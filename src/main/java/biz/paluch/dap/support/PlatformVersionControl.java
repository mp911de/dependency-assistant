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

package biz.paluch.dap.support;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.Sequence;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushSource;
import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.push.PushSupport;
import com.intellij.dvcs.push.PushTarget;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.AbstractVcsHelper;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.LocalChangeList;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Platform VCS integration, registered only when the VCS module is available.
 * <p>Branch and push operations without a file use the first project
 * repository. Multi-repository selection is not supported.
 *
 * @author Mark Paluch
 */
public class PlatformVersionControl implements VersionControl {

	private final Project project;

	private final ProjectLevelVcsManager vcsManager;

	private final ChangeListManagerEx changeListManager;

	private final VcsRepositoryManager repositoryManager;

	public PlatformVersionControl(Project project) {

		this.project = project;
		this.vcsManager = ProjectLevelVcsManager.getInstance(project);
		this.changeListManager = ChangeListManagerEx.getInstanceEx(project);
		this.repositoryManager = VcsRepositoryManager.getInstance(project);
	}

	@Override
	public boolean isPresent() {
		return true;
	}

	@Override
	public boolean isActive() {
		return vcsManager.hasActiveVcss();
	}

	@Override
	@SuppressWarnings({"rawtypes", "unchecked"})
	public boolean canPush() {

		Repository repository = getRepository();
		PushSupport support = repository != null ? DvcsUtil.getPushSupport(repository.getVcs()) : null;
		return support != null && support.getSource(repository) != null && support.getDefaultTarget(repository) != null;
	}

	/**
	 * Return scope files with uncommitted changes on any changelist.
	 */
	@Override
	public FileScope dirtyInScope(FileScope scope) {

		if (!isActive()) {
			return FileScope.of();
		}

		List<VirtualFile> dirty = new ArrayList<>();
		for (VirtualFile file : scope) {
			if (changeListManager.getChange(file) != null) {
				dirty.add(file);
			}
		}

		return FileScope.of(dirty);
	}

	@Override
	public @Nullable Runnable shelve(FileScope scope, String message) throws IOException {

		if (!isActive()) {
			throw new IOException(MessageBundle.message("plan.vcs.unavailable"));
		}
		List<Change> changes = refreshedChanges(scope);
		if (changes.isEmpty()) {
			return null;
		}
		try {
			ShelvedChangeList shelf = ShelveChangesManager.getInstance(project).shelveChanges(changes, message, true);
			return () -> {
				if (!isActive()) {
					throw new IllegalStateException(MessageBundle.message("plan.vcs.unavailable"));
				}
				ShelveChangesManager.getInstance(project)
						.unshelveSilentlyAsynchronously(project, List.of(shelf), List.of(), List.of(), null);
			};
		} catch (VcsException ex) {
			throw new IOException(ex.getMessage(), ex);
		}
	}

	@Override
	public boolean commit(FileScope scope, String message) throws IOException {

		CheckinEnvironment checkin = checkinEnvironment(scope);
		if (scope.hasMissingFiles() || checkin == null) {
			throw new IOException(MessageBundle.message("plan.vcs.commit.unsupported"));
		}
		List<Change> changes = refreshedChanges(scope);
		if (changes.isEmpty()) {
			return false;
		}
		List<VcsException> errors = checkin.commit(changes, message);
		if (errors != null && !errors.isEmpty()) {
			VcsException error = errors.getFirst();
			throw new IOException(error.getMessage(), error);
		}
		return true;
	}

	@Override
	public boolean hasChanges(FileScope scope) {
		return !refreshedChanges(scope).isEmpty();
	}

	/**
	 * Push the first repository using its VCS-provided source and default target.
	 * @throws IllegalStateException if push support, source or target is
	 * unavailable.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	@Override
	public void push() {

		Repository repository = getRepository();
		PushSupport support = repository != null ? DvcsUtil.getPushSupport(repository.getVcs()) : null;
		if (support == null) {
			throw new IllegalStateException(MessageBundle.message("plan.vcs.push.unsupported"));
		}

		PushSource source = support.getSource(repository);
		PushTarget target = support.getDefaultTarget(repository);
		if (source == null || target == null) {
			throw new IllegalStateException(MessageBundle.message("plan.vcs.push.unsupported"));
		}

		support.getPusher().push(Map.of(repository, new PushSpec<>(source, target)), null, false);
	}

	@Override
	public @Nullable String getCurrentBranch() {
		Repository repository = getRepository();
		return repository != null ? repository.getCurrentBranchName() : null;
	}

	@Override
	public @Nullable String getCurrentBranch(VirtualFile file) {
		Repository repository = repositoryManager.getRepositoryForFileQuick(file);
		return repository != null ? repository.getCurrentBranchName() : null;
	}

	@Override
	public boolean canCommit() {
		for (AbstractVcs vcs : vcsManager.getAllActiveVcss()) {
			if (vcs.getCheckinEnvironment() != null) {
				return true;
			}
		}
		return false;
	}

	@Override
	public boolean canCommit(FileScope scope) {
		return !scope.hasMissingFiles() && checkinEnvironment(scope) != null;
	}

	@Override
	public boolean isIgnored(VirtualFile file) {
		return isActive() && changeListManager.isIgnoredFile(file);
	}

	@Override
	public void openCommitDialog(String message) {
		if (!canCommit()) {
			return;
		}
		FileDocumentManager.getInstance().saveAllDocuments();
		changeListManager.invokeAfterUpdate(true, () -> {
			if (canCommit()) {
				LocalChangeList changeList = changeListManager.getDefaultChangeList();
				AbstractVcsHelper.getInstance(project).commitChanges(changeList.getChanges(), changeList, message,
						null);
			}
		});
	}

	private List<Change> refreshedChanges(Sequence<VirtualFile> scope) {

		if (!isActive()) {
			return List.of();
		}

		// reflect the just-applied, saved edits before reading changes
		changeListManager.waitForUpdate();

		List<Change> changes = new ArrayList<>();
		for (VirtualFile file : scope) {

			Change change = changeListManager.getChange(file);
			if (change != null) {
				changes.add(change);
			}
		}

		return changes;
	}

	private @Nullable CheckinEnvironment checkinEnvironment(Sequence<VirtualFile> scope) {

		AbstractVcs vcs = vcsFor(scope);
		return vcs != null ? vcs.getCheckinEnvironment() : null;
	}

	private @Nullable AbstractVcs vcsFor(Sequence<VirtualFile> scope) {
		AbstractVcs selected = null;
		for (VirtualFile file : scope) {
			AbstractVcs vcs = vcsManager.getVcsFor(file);
			if (vcs == null || (selected != null && vcs != selected)) {
				return null;
			}
			selected = vcs;
		}
		return selected;
	}

	private @Nullable Repository getRepository() {
		return isActive() ? repositoryManager.getRepositories().stream().findFirst().orElse(null) : null;
	}

}
