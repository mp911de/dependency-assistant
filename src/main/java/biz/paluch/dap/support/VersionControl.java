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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Optional project version-control operations.
 * <p>Resolve the service when an operation is requested. Its presence means the
 * platform integration is installed, not that the project has a VCS mapping.
 *
 * @author Mark Paluch
 */
public interface VersionControl {

	/**
	 * Return the installed integration, or an absent implementation without a
	 * project or VCS support.
	 */
	static VersionControl find(@Nullable Project project) {
		VersionControl service = project != null ? project.getService(VersionControl.class) : null;
		return service == null ? AbsentVersionControl.INSTANCE : service;
	}

	/**
	 * Return whether a version-control system (plugin) is available.
	 */
	boolean isPresent();

	/**
	 * Return whether the project has an active VCS mapping.
	 */
	boolean isActive();

	/**
	 * Return whether any active VCS supports committing changes.
	 */
	boolean canCommit();

	/**
	 * Return whether all scope files exist and belong to one VCS that supports
	 * committing changes. An empty scope cannot be committed.
	 */
	boolean canCommit(FileScope scope);

	/**
	 * Return whether the first repository has push support, a source and a default
	 * target.
	 */
	boolean canPush();

	/**
	 * Return whether the active VCS marks the file as ignored.
	 */
	boolean isIgnored(VirtualFile file);

	/**
	 * Return scope files with known uncommitted changes in any change list. Return
	 * an empty scope without an active VCS mapping.
	 */
	FileScope dirtyInScope(FileScope scope);

	/**
	 * Shelve and roll back scope changes on a background thread.
	 * @return the recovery operation, or {@literal null} if no changes were
	 * shelved.
	 * @throws IOException if shelving is unavailable or fails.
	 */
	@Nullable
	Runnable shelve(FileScope scope, String message) throws IOException;

	/**
	 * Commit current scope changes on a background thread.
	 * @return whether changes were found and committed.
	 * @throws IOException if commit is unsupported or fails.
	 */
	boolean commit(FileScope scope, String message) throws IOException;

	/**
	 * Refresh change lists and return whether the scope has uncommitted changes.
	 * Invoke on a background thread. Return false without an active VCS mapping.
	 */
	boolean hasChanges(FileScope scope);

	/**
	 * Open the platform commit dialog for the default change list if commit is
	 * available.
	 */
	void openCommitDialog(String message);

	/**
	 * Push the first repository using its default target.
	 * @throws IllegalStateException if push support, source or target is
	 * unavailable.
	 */
	void push();

	/**
	 * Return the branch of the first repository.
	 * @return the current branch or {@literal null} without a named branch.
	 */
	@Nullable
	String getCurrentBranch();

	/**
	 * Return the branch for the file.
	 * @param file the file to get the branch for.
	 * @return the current branch for the {@link VirtualFile} or {@literal null}
	 * without a named branch.
	 */
	@Nullable
	String getCurrentBranch(VirtualFile file);

}
