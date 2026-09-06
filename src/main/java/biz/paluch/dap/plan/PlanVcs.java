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

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.support.FileScope;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.Sequence;
import com.intellij.dvcs.DvcsUtil;
import com.intellij.dvcs.push.PushSource;
import com.intellij.dvcs.push.PushSpec;
import com.intellij.dvcs.push.PushSupport;
import com.intellij.dvcs.push.PushTarget;
import com.intellij.dvcs.repo.Repository;
import com.intellij.dvcs.repo.VcsRepositoryManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerEx;
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager;
import com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList;
import com.intellij.openapi.vcs.checkin.CheckinEnvironment;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Upgrade Plan version-control operations through platform VCS abstractions. No
 * VCS-specific plugin is required.
 * <p>File operations use the supplied scope. Branch and push operations use the
 * first project repository. Multi-repository selection is not supported.
 *
 * @author Mark Paluch
 */
// TODO: consider multi-repository projects
class PlanVcs {

	private final Project project;

	private final ProjectLevelVcsManager vcsManager;

	private final ChangeListManagerEx changeListManager;

	private final VcsRepositoryManager repositoryManager;

	PlanVcs(Project project) {

		this.project = project;
		this.vcsManager = ProjectLevelVcsManager.getInstance(project);
		this.changeListManager = ChangeListManagerEx.getInstanceEx(project);
		this.repositoryManager = VcsRepositoryManager.getInstance(project);
	}

	boolean hasVcs() {
		return vcsManager.hasActiveVcss();
	}

	/**
	 * Return whether the first repository provides push support.
	 */
	boolean canPush() {

		Repository repository = repository();
		return repository != null && DvcsUtil.getPushSupport(repository.getVcs()) != null;
	}

	/**
	 * Return scope files with uncommitted changes on any changelist.
	 */
	FileScope dirtyInScope(FileScope scope) {

		if (!hasVcs()) {
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

	/**
	 * Shelve and roll back uncommitted scope changes. Call from a background
	 * thread.
	 * @return the shelf, or {@literal null} if there are no changes.
	 * @throws VcsException if shelving fails.
	 */
	@Nullable
	ShelvedChangeList shelve(FileScope scope, String message) throws VcsException {

		List<Change> changes = refreshedChanges(scope);
		if (changes.isEmpty()) {
			return null;
		}

		try {
			return ShelveChangesManager.getInstance(project).shelveChanges(changes, message, true);
		} catch (IOException e) {
			throw new VcsException(e);
		}
	}

	/**
	 * Restore a shelf asynchronously. The shelf manager reports the outcome.
	 */
	void unshelve(ShelvedChangeList shelf) {
		ShelveChangesManager.getInstance(project)
				.unshelveSilentlyAsynchronously(project, List.of(shelf), List.of(), List.of(), null);
	}

	/**
	 * Commit current scope changes.
	 * @return whether changes were found and committed.
	 * @throws VcsException if commit is unsupported or fails.
	 */
	boolean commit(FileScope scope, String message) throws VcsException {

		List<Change> changes = refreshedChanges(scope);
		if (changes.isEmpty()) {
			return false;
		}

		CheckinEnvironment checkin = checkinEnvironment(scope);
		if (checkin == null) {
			throw new VcsException(MessageBundle.message("plan.vcs.commit.unsupported"));
		}

		List<VcsException> errors = checkin.commit(changes, message);
		if (errors != null && !errors.isEmpty()) {
			throw errors.getFirst();
		}
		return true;
	}

	boolean hasChanges(FileScope scope) {
		return !refreshedChanges(scope).isEmpty();
	}

	/**
	 * Push the first repository using its VCS-provided source and default target.
	 * @throws IllegalStateException if push support, source or target is
	 * unavailable.
	 */
	@SuppressWarnings({"rawtypes", "unchecked"})
	void push() {

		Repository repository = repository();
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

	/**
	 * Return the first repository's branch name, or {@literal null} without a named
	 * branch. Call from a background thread.
	 */
	@Nullable
	String getCurrentBranch() {

		Repository repository = repository();
		return repository != null ? repository.getCurrentBranchName() : null;
	}

	private List<Change> refreshedChanges(Sequence<VirtualFile> scope) {

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

		for (VirtualFile file : scope) {

			AbstractVcs vcs = vcsManager.getVcsFor(file);
			if (vcs != null) {
				return vcs;
			}
		}

		return null;
	}

	private @Nullable Repository repository() {
		return repositoryManager.getRepositories().stream().findFirst().orElse(null);
	}

}
