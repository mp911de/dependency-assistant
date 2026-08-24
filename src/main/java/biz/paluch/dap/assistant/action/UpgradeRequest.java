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

package biz.paluch.dap.assistant.action;

import java.util.List;

import biz.paluch.dap.artifact.PackageIdentity;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Value object carrying the file selection, active editor, and optional dialog
 * focus of a dependency upgrade request.
 *
 * <p>The selection list is retained and exposed directly. Callers must not
 * modify it after construction.
 *
 * @author Mark Paluch
 * @param selection the Project View selection. Empty when invoked without a
 * selection.
 * @param editorFile the file open in the active editor, or {@literal null} when
 * no editor is open.
 * @param focusArtifact the package identity whose dialog row to select and
 * reveal after the check, or {@literal null} when the request does not
 * originate from a single declaration (for example, a plain menu action).
 * @see UpgradeScopeResolver
 */
public record UpgradeRequest(List<VirtualFile> selection, @Nullable PsiFile editorFile,
		@Nullable PackageIdentity focusArtifact) {

	public UpgradeRequest(List<VirtualFile> selection, @Nullable PsiFile editorFile) {
		this(selection, editorFile, null);
	}

	public boolean hasSingleSource() {
		return selection.size() == 1 || (selection().isEmpty() && editorFile != null);
	}

	public boolean hasSelection() {
		return !selection.isEmpty();
	}

	public boolean hasEditorFile() {
		return editorFile != null;
	}

	/**
	 * Return the active editor file.
	 *
	 * @return the editor file carried by this request.
	 * @throws IllegalStateException if this request has no editor file.
	 */
	public PsiFile getEditorFile() {
		Assert.state(editorFile != null, "No editor file");
		return editorFile;
	}

}
