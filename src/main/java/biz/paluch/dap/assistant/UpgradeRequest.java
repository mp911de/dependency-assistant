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

package biz.paluch.dap.assistant;

import java.util.List;

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
 * @author Mark Paluch
 * @see UpgradeScopeResolver
 */
record UpgradeRequest(List<VirtualFile> selection, @Nullable PsiFile editorFile) {

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
