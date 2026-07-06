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
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;

/**
 * SPI for build-tool integrations such as Maven and Gradle.
 *
 * <p>Implementations are contributed through the
 * {@code biz.paluch.dap.assistant} extension point and are shared across
 * projects. They should therefore be stateless or hold only immutable
 * configuration; per-run state lives in the {@link IntrospectedDependencies}
 * instance returned by {@link #introspect(Project)}.
 *
 * <p>An assistant supplies the integration points the
 * {@link ProjectStateIndexer} drives: it enumerates the files an ecosystem owns
 * and collects each into a store-ready {@link DependencyCollector}. The indexer
 * derives the build context for each anchor on demand through
 * {@link #createContext(Project, PsiFile)}.
 *
 * <p>Support checks are expected to be cheap. Expensive parsing and state
 * access belongs in {@link #prepare(Project) preparation}, dependency scanning,
 * or a {@link ProjectDependencyContext}.
 *
 * @author Mark Paluch
 * @see ProjectStateIndexer
 * @see IntrospectedDependencies
 * @see ProjectDependencyContext
 */
public interface DependencyAssistant {

	/**
	 * Return the stable integration id.
	 * @return the stable id, for example {@code "maven"} or {@code "gradle"}.
	 */
	String getId();

	/**
	 * Return the human-readable integration name.
	 */
	String getDisplayName();

	/**
	 * Return the package {@link PackageSystem ecosystem} this integration serves.
	 * <p>One assistant serves exactly one ecosystem.
	 * @return the ecosystem served by this integration.
	 */
	PackageSystem getPackageSystem();

	/**
	 * Return the user-interface metadata for this integration.
	 * <p>The returned {@link InterfaceAssistant} is a stateless, shared instance
	 * supplying icons and display names, reachable without a
	 * {@link ProjectDependencyContext}. Persisted references such as the Upgrade
	 * Plan resolve it from a stored integration class name to recover the correct
	 * row icon.
	 *
	 * @return the interface metadata; guaranteed to be not {@literal null}.
	 */
	InterfaceAssistant getInterfaceAssistant();

	/**
	 * Return whether this integration applies to the given project.
	 * <p>This conditional must not trigger I/O or PSI access.
	 * @param project the IntelliJ project to inspect.
	 */
	boolean supports(Project project);

	/**
	 * Return whether this integration owns the given file.
	 * <p>This conditional must not trigger I/O or PSI access.
	 * @param file the file to inspect.
	 */
	boolean supports(PsiFile file);

	/**
	 * Initialization hook after project startup.
	 * @param project the IntelliJ project.
	 */
	default void prepare(Project project) {
	}

	/**
	 * Resolve the managed member map of a Bill of Materials from local build-tool
	 * storage such as the local Maven repository or the Gradle module cache.
	 * <p>Implementations consult caches and local storage only and must not issue
	 * network requests. Resolution may parse the BOM POM, so callers must invoke
	 * this method from a background thread inside a read action.
	 *
	 * @param project the project providing repository configuration.
	 * @param pkg the BOM package identity.
	 * @param version the BOM version.
	 * @return the managed members keyed by artifact coordinates; empty when this
	 * integration cannot resolve the BOM.
	 */
	default Map<ArtifactId, ArtifactVersion> resolveBillOfMaterials(Project project, PackageIdentity pkg,
			ArtifactVersion version) {
		return Map.of();
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
	 * Collect dependencies for the given anchor file into the provided collector.
	 * <p>The collector is the same instance the indexer later passes to
	 * {@link IntrospectedDependencies#complete(DependencyCollector)} and stores in
	 * the {@link biz.paluch.dap.state.ProjectState}. Implementations must mutate
	 * the provided collector directly and must not replace it with a new instance.
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
	 * @param introspected the introspection handle for the current indexer run.
	 */
	default void collect(PsiFile anchor, DependencyCollector collector, IntrospectedDependencies introspected) {
		collect(anchor, collector);
	}

	/**
	 * Collect the given anchor file into a fresh, completed
	 * {@link DependencyCollector}: run a single-file introspection, collect, attach
	 * the given release sources, and complete the introspection.
	 * <p>This is the single-file counterpart of the indexer's collect-complete
	 * flow, used by file-scoped contexts that scan one build file on demand.
	 *
	 * @param anchor the anchor file to collect for.
	 * @return the completed collector; guaranteed to be not {@literal null}.
	 */
	default DependencyCollector collectCompleted(PsiFile anchor) {

		IntrospectedDependencies introspected = introspect(anchor.getProject());
		DependencyCollector collector = new DependencyCollector();
		collect(anchor, collector, introspected);
		introspected.complete(collector);
		return collector;
	}

	/**
	 * Return a fresh {@link IntrospectedDependencies} instance scoped to one
	 * indexer run.
	 * <p>The default returns the empty instance, suitable for integrations that do
	 * not derive scan-wide metadata.
	 * @param project the IntelliJ project.
	 */
	default IntrospectedDependencies introspect(Project project) {
		return IntrospectedDependencies.empty();
	}

	/**
	 * Create the file-scoped dependency context for the given anchor file.
	 * <p>Invoke only after {@link #supports(PsiFile)} returned {@literal true}.
	 * @param anchor the build file or catalog file that anchors the operation.
	 * @return a file-scoped context.
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
	 * @return a file-scoped context.
	 * @throws IllegalStateException if this integration does not support the file.
	 */
	ProjectDependencyContext createContext(Project project, PsiFile anchor);

}
