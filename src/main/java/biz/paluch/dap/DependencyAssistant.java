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

import biz.paluch.dap.artifact.BillOfMaterials;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.artifact.VersionedPackage;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * SPI for build-tool integrations such as Maven and Gradle.
 *
 * <p>Implementations are contributed through the
 * {@code biz.paluch.dap.assistant} extension point and are shared across
 * projects. They must not retain project, PSI, or collection-run state.
 * Immutable configuration may be retained, while per-run state belongs in the
 * {@link IntrospectedDependencies} instance returned by
 * {@link #introspect(Project)}.
 *
 * <p>An assistant supplies the integration points the
 * {@link ProjectStateIndexer} drives: it enumerates the files an ecosystem owns
 * and populates a phase-one {@link DependencyCollector} for each. The indexer
 * derives the build context for each anchor on demand through
 * {@link #createContext(Project, PsiFile)}.
 *
 * <p>Support checks are expected to be cheap and limited to project-model or
 * file shape recognition. They must not perform I/O or parse file contents.
 * Expensive preparation, parsing, and state access belongs in
 * {@link #prepare(Project)}, dependency collection, or a
 * {@link ProjectDependencyContext}.
 *
 * @author Mark Paluch
 * @see ProjectStateIndexer
 * @see IntrospectedDependencies
 * @see ProjectDependencyContext
 */
public interface DependencyAssistant {

	/**
	 * Return the stable integration id used for persisted references and refresh
	 * identity.
	 * <p>The id must remain stable across plugin versions.
	 *
	 * @return the stable id, for example {@code "maven"} or {@code "gradle"}.
	 */
	String getId();

	/**
	 * Return the human-readable integration name.
	 * @return the integration name for presentation to users.
	 */
	String getDisplayName();

	/**
	 * Return the package {@link PackageSystem ecosystem} this integration serves.
	 * <p>One assistant serves exactly one ecosystem.
	 *
	 * @return the ecosystem served by this integration.
	 */
	PackageSystem getPackageSystem();

	/**
	 * Return the user-interface metadata for this integration.
	 * @return the context-independent interface metadata.
	 */
	InterfaceAssistant getInterfaceAssistant();

	/**
	 * Return whether this integration applies to the given project.
	 * <p>This check must not trigger I/O or inspect PSI.
	 *
	 * @param project the IntelliJ project to inspect.
	 * @return {@literal true} if this integration can operate in the project.
	 */
	boolean supports(Project project);

	/**
	 * Return whether this integration owns the given file.
	 * <p>This check recognizes the file shape only. It does not guarantee that a
	 * project model is available for {@link #createContext(Project, PsiFile)}.
	 *
	 * @param file the file to inspect.
	 * @return {@literal true} if this integration recognizes the file.
	 */
	boolean supports(PsiFile file);

	/**
	 * Return whether the given element represents an editable dependency version.
	 * @param element the PSI element to inspect.
	 * @return {@literal true} if the element can anchor a single dependency update.
	 */
	boolean isVersionElement(PsiElement element);

	/**
	 * Prepare this integration after project startup and before the initial Project
	 * State population.
	 * <p>The default implementation performs no preparation.
	 *
	 * @param project the IntelliJ project.
	 */
	default void prepare(Project project) {
	}

	/**
	 * Resolve the managed members of a Bill of Materials from local build-tool
	 * storage such as the local Maven repository or the Gradle module cache.
	 * <p>Implementations consult caches and local storage only and must not issue
	 * network requests. Resolution may parse the BOM POM, so callers must invoke
	 * this method from a background thread inside a read action.
	 * <p>Implementations return {@literal null} rather than a member-less Bill of
	 * Materials when they cannot resolve the BOM, so callers can tell "not mine"
	 * apart from "resolved to nothing".
	 *
	 * @param project the project providing repository configuration.
	 * @param bom the BOM identity and version to resolve members for.
	 * @return the resolved Bill of Materials, or {@literal null} when this
	 * integration cannot resolve the BOM.
	 */
	default @Nullable BillOfMaterials resolveBillOfMaterials(Project project, VersionedPackage bom) {
		return null;
	}

	/**
	 * Enumerate the anchor files owned by this integration for the given project.
	 * <p>Implementations must apply their own file-scope filters here. The indexer
	 * derives the build context for each anchor on demand.
	 * @param project the IntelliJ project to enumerate against.
	 * @return the anchor files to be processed by the indexer.
	 */
	List<PsiFile> enumerate(Project project);

	/**
	 * Return an {@link IntrospectedDependencies} instance scoped to one collection
	 * run.
	 * <p>Implementations that accumulate run state must return a fresh instance.
	 * The default returns the shared empty instance, suitable for integrations that
	 * do not derive scan-wide metadata.
	 * @param project the IntelliJ project.
	 * @return a completion handle scoped to the new collection run.
	 */
	default IntrospectedDependencies introspect(Project project) {
		return IntrospectedDependencies.empty();
	}

	/**
	 * Collect the given anchor file into a fresh, completed
	 * {@link DependencyCollector}: create a run-scoped introspection handle,
	 * collect the file, and complete the collector.
	 * <p>This is the single-file counterpart of the indexer's collect-complete
	 * flow, used by file-scoped contexts that scan one build file on demand.
	 *
	 * @param anchor the anchor file to collect for.
	 * @return the completed collector.
	 */
	default DependencyCollector collectCompleted(PsiFile anchor) {

		IntrospectedDependencies introspected = introspect(anchor.getProject());
		DependencyCollector collector = new DependencyCollector(getPackageSystem());
		collect(anchor, collector, introspected);
		introspected.complete(collector);
		return collector;
	}

	/**
	 * Collect dependencies for the given anchor file into the provided collector.
	 * <p>The collector is the same instance the indexer later passes to
	 * {@link IntrospectedDependencies#complete(DependencyCollector)} and stores in
	 * the {@link biz.paluch.dap.state.ProjectState}. Implementations must mutate
	 * the provided collector directly. They must not complete it themselves because
	 * the host completes all collectors after phase-one collection.
	 *
	 * @param anchor the anchor file to collect for.
	 * @param collector the collector to populate in place.
	 */
	void collect(PsiFile anchor, DependencyCollector collector);

	/**
	 * Collect dependencies for the given anchor file into the provided collector,
	 * giving the integration access to the run-scoped
	 * {@link IntrospectedDependencies}.
	 * <p>The default delegates to {@link #collect(PsiFile, DependencyCollector)}
	 * and is suitable for integrations that do not accumulate per-entry
	 * introspection state. Integrations that need to feed phase-one state into
	 * their {@link IntrospectedDependencies} should override this method.
	 * @param anchor the anchor file to collect for.
	 * @param collector the collector to populate in place.
	 * @param introspected the introspection handle for the current collection run.
	 */
	default void collect(PsiFile anchor, DependencyCollector collector, IntrospectedDependencies introspected) {
		collect(anchor, collector);
	}

	/**
	 * Create the file-scoped dependency context for the given anchor file.
	 * <p>Invoke only after {@link #supports(PsiFile)} returned {@literal true}.
	 * @param anchor the build file or catalog file that anchors the operation.
	 * @return a file-scoped context, which may be unavailable when the required
	 * project model is not ready.
	 * @throws IllegalStateException if this integration does not support the file.
	 */
	default ProjectDependencyContext createContext(PsiFile anchor) {
		return createContext(anchor.getProject(), anchor);
	}

	/**
	 * Create the file-scoped dependency context for the given anchor file.
	 * <p>Invoke only after {@link #supports(PsiFile)} returned {@literal true}.
	 * @param project the IntelliJ project.
	 * @param anchor the build file or catalog file that anchors the operation.
	 * @return a file-scoped context, which may be unavailable when the required
	 * project model is not ready.
	 * @throws IllegalStateException if this integration does not support the file.
	 */
	ProjectDependencyContext createContext(Project project, PsiFile anchor);

}
