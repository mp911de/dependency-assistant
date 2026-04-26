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

import java.util.List;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * File-scoped operational handle for a build-tool integration.
 *
 * <p>A context is created after a {@link DependencyAssistant} has claimed an
 * anchor file. It exposes the build-tool specific work needed by shared editor
 * features: state invalidation, dependency scanning, PSI lookup, and update
 * application.
 *
 * <p>Contexts are lightweight and created on demand. Mutable project state
 * belongs in the project cache, not in the context itself.
 *
 * @author Mark Paluch
 * @see DependencyAssistant
 * @see DependencyCollector
 */
public interface ProjectDependencyContext extends ProjectBuildContext {

	/**
	 * Return the user-interface support for this context.
	 */
	InterfaceAssistant getInterfaceAssistant();

	/**
	 * Invalidate and re-collect the state affected by the changed file.
	 * @param file the PSI file that was saved.
	 */
	void invalidateState(PsiFile file);

	/**
	 * Scan the build files reachable from the anchor file and return the aggregated
	 * dependency data.
	 * @param indicator the progress indicator to report to.
	 * @return the collected dependency data.
	 */
	DependencyCollector scanDependencies(ProgressIndicator indicator);

	/**
	 * Return whether the given element represents an editable dependency version.
	 * @param element the PSI element to inspect.
	 */
	boolean isVersionElement(PsiElement element);

	/**
	 * Return the version-upgrade lookup for the given PSI element.
	 * @param element the PSI element at the cursor position.
	 * @return the lookup for the element.
	 */
	VersionUpgradeLookupSupport getLookup(PsiElement element);

	/**
	 * Apply the given dependency updates to the appropriate build files.
	 * @param psiFile the anchor PSI file used for the write action.
	 * @param updates the updates to apply.
	 */
	void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates);


}
