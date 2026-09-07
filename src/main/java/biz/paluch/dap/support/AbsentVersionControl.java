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

import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Absent version control.
 */
enum AbsentVersionControl implements VersionControl {

	INSTANCE;

	@Override
	public boolean isPresent() {
		return false;
	}

	@Override
	public boolean isActive() {
		return false;
	}

	@Override
	public boolean canCommit() {
		return false;
	}

	@Override
	public boolean canCommit(FileScope scope) {
		return false;
	}

	@Override
	public boolean canPush() {
		return false;
	}

	@Override
	public boolean isIgnored(VirtualFile file) {
		return false;
	}

	@Override
	public FileScope dirtyInScope(FileScope scope) {
		return FileScope.of();
	}

	@Override
	public @Nullable Runnable shelve(FileScope scope, String message) throws IOException {
		return null;
	}

	@Override
	public boolean commit(FileScope scope, String message) throws IOException {
		return false;
	}

	@Override
	public boolean hasChanges(FileScope scope) {
		return false;
	}

	@Override
	public void openCommitDialog(String message) {
	}

	@Override
	public void push() {
	}

	@Override
	public @Nullable String getCurrentBranch() {
		return null;
	}

	@Override
	public @Nullable String getCurrentBranch(VirtualFile file) {
		return null;
	}

}
