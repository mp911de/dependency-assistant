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

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.DependencyUpdate;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.ProjectBuildContext;
import biz.paluch.dap.support.VersionUpgradeLookup;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * File-scoped operational handle for a build-tool integration.
 *
 * <p>A context is created after a {@link DependencyAssistant} has supported an
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
	 * Scan the build files reachable from the anchor file and return the aggregated
	 * dependency data.
	 * @param indicator the progress indicator to report to; must not be
	 * {@literal null}.
	 * @return the collected dependency data.
	 */
	DependencyCollector scanDependencies(ProgressIndicator indicator);

	/**
	 * Resolve the given declared dependency to a concrete dependency by providing
	 * the current list of releases.
	 * @param declaredDependency the declared dependency to resolve; must not be
	 * {@literal null}.
	 * @param releases current list of releases; must not be {@literal null}.
	 * @return the resolved dependency or {@literal null} if the dependency could
	 * not be resolved.
	 */
	default @Nullable Dependency resolveDependency(DeclaredDependency declaredDependency, List<Release> releases) {
		return null;
	}

	default @Nullable ArtifactVersion resolveVersion(VersionSource source, List<Release> releases) {
		return null;
	}

	/**
	 * Return whether the given element represents an editable dependency version.
	 * @param element the PSI element to inspect; must not be {@literal null}.
	 */
	boolean isVersionElement(PsiElement element);

	/**
	 * Return the version-upgrade lookup for the given PSI element.
	 *
	 * @param element the PSI element at the cursor position; must not be
	 * {@literal null}.
	 * @param file the containing file; must not be {@literal null}.
	 * @return the lookup for the element.
	 */
	VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file);

	/**
	 * Apply a single dependency update at the given PSI element.
	 * <p>
	 * The method rewrites only that literal in place and never re-traverses the
	 * containing file.
	 * @param versionLiteral the version PSI element that triggered the update; must
	 * not be {@literal null}.
	 * @param update the update to apply; must not be {@literal null}.
	 * @throws IllegalStateException when the anchor resolves to an unexpected
	 * element kind.
	 */
	void applyUpdate(PsiElement versionLiteral, DependencyUpdate update);

	/**
	 * Apply the given dependency updates to the appropriate build files.
	 * @param psiFile the anchor PSI file used for the write action; must not be
	 * {@literal null}.
	 * @param updates the updates to apply; must not be {@literal null}.
	 */
	void applyUpdates(PsiFile psiFile, List<DependencyUpdate> updates);

	/**
	 * Return an absent {@link ProjectDependencyContext}.
	 */
	static ProjectDependencyContext absent() {
		return AbsentDependencyContext.ABSENT;
	}

}
