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

package biz.paluch.dap.rule;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Source file used to resolve branch-specific dependency rules.
 *
 * @author Mark Paluch
 */
public interface BranchSource {

	/**
	 * Return the file used for branch lookup, or {@literal null} when no branch
	 * context is available.
	 */
	@Nullable
	VirtualFile getFile();

	/**
	 * Create a branch source from the given virtual file.
	 * @param file the file used for branch lookup, can be {@literal null}.
	 * @return a branch source for the file.
	 */
	static BranchSource of(@Nullable VirtualFile file) {
		return () -> file;
	}

	/**
	 * Create a branch source from the containing file of the given element.
	 * @param element the PSI element used for branch lookup.
	 * @return a branch source for the element's containing file.
	 */
	static BranchSource of(PsiElement element) {
		PsiFile file = element.getContainingFile();
		return of(file != null ? file.getVirtualFile() : null);
	}

}
