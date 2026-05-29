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

package biz.paluch.dap;

import biz.paluch.dap.support.ProjectBuildContext;
import com.intellij.psi.PsiFile;

/**
 * Carrier passed between phases of the shared Project State population flow.
 *
 * <p>
 * Each entry pairs an anchor build file with the {@link ProjectBuildContext}
 * that owns dependency state for that file. The carrier is created by a
 * {@link DependencySource} during enumeration and re-used through collection,
 * completion, invalidation, and store.
 *
 * @author Mark Paluch
 * @see DependencySource
 * @see ProjectStateUpdater
 */
public record DependencyScanEntry(PsiFile anchor, ProjectBuildContext context) {

	/**
	 * Create an entry that pairs the given anchor file with its build context.
	 * @param anchor the build file or descriptor file; must not be {@literal null}.
	 * @param context the build context that owns dependency state for the anchor;
	 * must not be {@literal null}.
	 * @return the entry.
	 */
	public static DependencyScanEntry of(PsiFile anchor, ProjectBuildContext context) {
		return new DependencyScanEntry(anchor, context);
	}

	@Override
	public String toString() {
		return "DependencyScanEntry[%s, %s]".formatted(anchor.getName(), context);
	}

}
