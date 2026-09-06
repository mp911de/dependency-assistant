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
 * File selection and editor context for an upgrade review.
 * <p>The selection is retained directly and must not be modified after
 * construction.
 *
 * @author Mark Paluch
 * @param selection the Project View selection, empty if none.
 * @param editorFile the active editor file, or {@literal null} if none.
 * @param focusArtifact the row to reveal, or {@literal null} if no row is
 * requested.
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
	 * @throws IllegalStateException if no editor file is available.
	 */
	public PsiFile getEditorFile() {
		Assert.state(editorFile != null, "No editor file");
		return editorFile;
	}

}
