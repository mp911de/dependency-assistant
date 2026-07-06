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

package biz.paluch.dap.plan;

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
 * Version-control operations for applying the Upgrade Plan, built entirely on
 * the general IntelliJ VCS and DVCS abstractions: commits go through the VCS's
 * {@link CheckinEnvironment}, repositories and branches come from
 * {@link VcsRepositoryManager}, and push goes through the VCS's registered
 * {@link PushSupport}. No VCS-specific plugin classes are referenced, so this
 * class loads and degrades gracefully when a particular VCS plugin is absent.
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

	/**
	 * Return whether the project has any active version control.
	 */
	boolean hasVcs() {
		return vcsManager.hasActiveVcss();
	}

	/**
	 * Return whether the plan can be pushed, that is, its repository's VCS
	 * contributes the platform push support.
	 */
	boolean canPush() {

		Repository repository = repository();
		return repository != null && DvcsUtil.getPushSupport(repository.getVcs()) != null;
	}

	/**
	 * Return the scope files that already carry uncommitted changes on any
	 * changelist.
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
	 * Shelve the uncommitted changes on the scope files and roll them back, leaving
	 * the scope clean for the apply run. Must run on a background thread; reports
	 * progress through the current indicator.
	 *
	 * @return the created shelf, or {@literal null} when the scope carries no
	 * changes.
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
	 * Restore a shelf created by {@link #shelve}. Asynchronous: the shelf manager
	 * runs its own background task and posts its own result notification.
	 */
	void unshelve(ShelvedChangeList shelf) {
		ShelveChangesManager.getInstance(project)
				.unshelveSilentlyAsynchronously(project, List.of(shelf), List.of(), List.of(), null);
	}

	/**
	 * Commit the current changes to the scope files with the given message,
	 * capturing only what changed since the previous commit.
	 *
	 * @return whether version control found and committed changes in the scope.
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

	/**
	 * Return whether the scope still carries changes after a commit attempt.
	 */
	boolean hasChanges(FileScope scope) {
		return !refreshedChanges(scope).isEmpty();
	}

	/**
	 * Push the current branch of the plan's repository through the VCS's push
	 * support, targeting the tracked branch or a same-named new branch on the
	 * default remote. Asynchronous: the push support runs its own background task
	 * and posts its own result notification.
	 *
	 * @throws IllegalStateException when the repository has no push support or no
	 * resolvable push target (fresh repository, no remotes).
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
	 * Return the current branch name of the plan's repository, used to default the
	 * milestone, or {@literal null} when the scope is not on a named branch. Must
	 * run on a background thread.
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
