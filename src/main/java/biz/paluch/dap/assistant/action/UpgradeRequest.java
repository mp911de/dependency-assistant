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

import java.util.List;

import biz.paluch.dap.artifact.ArtifactId;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Value object capturing the context (file selection, editor file) of a
 * dependency upgrade request.
 *
 * @param selection the Project View selection; empty when invoked without a
 * selection.
 * @param editorFile the build file open in the active editor, or
 * {@literal null} when no editor is open.
 * @param focusArtifact the artifact whose dialog row to select and reveal after
 * the check, or {@literal null} when the request does not originate from a
 * single declaration (e.g. a plain menu action).
 * @author Mark Paluch
 * @see UpgradeScopeResolver
 */
public record UpgradeRequest(List<VirtualFile> selection, @Nullable PsiFile editorFile,
		@Nullable ArtifactId focusArtifact) {

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

	public PsiFile getEditorFile() {
		Assert.state(editorFile != null, "No editor file");
		return editorFile;
	}

}
