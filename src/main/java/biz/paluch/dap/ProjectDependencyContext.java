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
import biz.paluch.dap.artifact.ReleaseSource;
import biz.paluch.dap.support.ArtifactReference;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;

/**
 * Per-project operational handle for one build-tool integration.
 * <p>A context is obtained from a {@link DependencyAssistant}. It keeps callers
 * away from build-tool specific types while exposing state lifecycle,
 * dependency scanning, editor reference resolution, and update application.
 *
 * @author Mark Paluch
 * @see DependencyAssistant
 */
public interface ProjectDependencyContext extends ProjectBuildContext {

	/**
	 * Invalidate and re-collect the state affected by the changed file.
	 *
	 * @param file the changed file; must not be {@literal null}.
	 */
	void invalidateState(PsiFile file);

	/**
	 * Scan the project slice owned by this context.
	 *
	 * @param indicator the progress indicator; must not be {@literal null}.
	 * @return the collected dependencies; guaranteed to be not {@literal null}.
	 */
	DependencyCollector scanDependencies(ProgressIndicator indicator);

	/**
	 * Resolve the artifact reference at the given PSI element.
	 *
	 * @param element the PSI element under inspection; must not be {@literal null}.
	 * @return a resolved reference or {@link ArtifactReference#unresolved()};
	 * guaranteed to be not {@literal null}.
	 */
	ArtifactReference resolveReference(PsiElement element);

	VersionUpgradeLookupSupport getLookup(PsiElement element);

	/**
	 * Get the release sources to check for updates.
	 *
	 * @return the release sources; guaranteed to be not {@literal null}.
	 */
	List<ReleaseSource> getReleaseSources();

	/**
	 * Apply dependency updates.
	 *
	 * @param psiFile
	 * @param updates the updates to apply; must not be {@literal null}.
	 */
	void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates);

}
