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

package biz.paluch.dap;

import java.util.List;

import biz.paluch.dap.artifact.DeclaredDependency;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.HasPackageSystem;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.lookup.VersionUpgradeLookup;
import biz.paluch.dap.state.GitVersionResolver;
import biz.paluch.dap.support.DependencyUpdate;
import biz.paluch.dap.support.DependencyUpdates;
import biz.paluch.dap.support.ProjectBuildContext;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * File-scoped operational handle for a build-tool integration.
 *
 * <p>A context is created after a {@link DependencyAssistant} recognizes an
 * anchor file. It exposes the build-tool specific work needed by shared editor
 * features: dependency scanning, PSI lookup, dependency resolution, and update
 * application.
 *
 * <p>Contexts are lightweight and created on demand. Mutable dependency state
 * and release metadata belong to the project-scoped
 * {@link biz.paluch.dap.state.StateService}, not to the context itself.
 *
 * @author Mark Paluch
 * @see DependencyAssistant
 * @see DependencyCollector
 */
public interface ProjectDependencyContext extends ProjectBuildContext, HasPackageSystem {

	/**
	 * Return the associated assistant.
	 * @return the assistant that created this context.
	 */
	DependencyAssistant getAssistant();

	/**
	 * Return the user-interface support for this context.
	 * @return the interface metadata supplied by the associated assistant.
	 */
	default InterfaceAssistant getInterfaceAssistant() {
		return getAssistant().getInterfaceAssistant();
	}

	@Override
	default PackageSystem getPackageSystem() {
		return getAssistant().getPackageSystem();
	}

	/**
	 * Scan the build files reachable from the anchor file and return the aggregated
	 * dependency data.
	 * @param indicator the progress and cancellation indicator for the scan.
	 * @return the collected dependency data.
	 */
	DependencyCollector scanDependencies(ProgressIndicator indicator);

	/**
	 * Resolve the given declared dependency to a concrete dependency by providing
	 * the current list of releases.
	 * <p>The default matches the declared ref against the Git-resolved releases
	 * through
	 * {@link GitVersionResolver#resolveDependency(DeclaredDependency, Iterable)}.
	 * For release lists without Git versions this yields {@literal null}, so
	 * integrations resolving by other means override this method.
	 * @param declaredDependency the declared dependency to resolve.
	 * @param releases current list of releases.
	 * @return the resolved dependency or {@literal null} if the dependency could
	 * not be resolved.
	 */
	default @Nullable Dependency resolveDependency(DeclaredDependency declaredDependency, List<Release> releases) {
		return GitVersionResolver.resolveDependency(declaredDependency, releases);
	}

	/**
	 * Return whether the given element represents an editable dependency version.
	 * @param element the PSI element to inspect.
	 * @return {@literal true} if the element can anchor a single dependency update.
	 */
	boolean isVersionElement(PsiElement element);

	/**
	 * Return the version-upgrade lookup for the given PSI element.
	 *
	 * @param element the PSI element at the cursor position.
	 * @param file the containing file.
	 * @return the lookup for the element.
	 */
	VersionUpgradeLookup getLookup(PsiElement element, VirtualFile file);

	/**
	 * Apply a single dependency update at a version element recognized by
	 * {@link #isVersionElement(PsiElement)}.
	 * <p>The element anchors the update. Use
	 * {@link #applyUpdates(PsiFile, DependencyUpdates)} to match updates throughout
	 * a file.
	 *
	 * @param versionLiteral the version PSI element that triggered the update.
	 * @param update the update to apply.
	 */
	void applyUpdate(PsiElement versionLiteral, DependencyUpdate update);

	/**
	 * Apply every matching dependency update to the given build file.
	 * @param psiFile the build file to traverse and update.
	 * @param updates the updates to apply.
	 */
	void applyUpdates(PsiFile psiFile, DependencyUpdates updates);

	/**
	 * Return an absent {@link ProjectDependencyContext}.
	 * @return the shared unavailable context sentinel.
	 */
	static ProjectDependencyContext absent() {
		return AbsentDependencyContext.ABSENT;
	}

}
