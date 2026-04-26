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
package biz.paluch.dap.support;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import biz.paluch.dap.DependencyAssistant;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.state.DependencyAssistantService;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Central dispatcher that fans out project and file operations to all
 * registered {@link DependencyAssistant} integrations.
 * <p>Integrations are discovered via the {@code biz.paluch.dap.assistant}
 * IntelliJ extension point and iterated in registration order. Two dispatch
 * semantics are provided:
 * <ul>
 * <li><em>Fan-out</em> - {@link #doWithContext(PsiFile, Consumer)} and
 * {@link #findAll} visit every applicable integration. Use these for
 * project-wide operations such as background indexing and cache invalidation on
 * save where all integrations must participate.</li>
 * <li><em>First-match</em> - {@link #findFirstContext(PsiFile)} and
 * {@link #findFirstContext(Project, PsiFile)} return the result from the first
 * integration that claims the file and stop. Use these for per-file editor
 * operations (annotators, line markers, completion) where exactly one
 * integration owns a given file.</li>
 * </ul>
 * <p>Each method guards dispatch behind the corresponding
 * {@link DependencyAssistant#supports} predicate, so integrations that do not
 * apply to the file or project are skipped without allocating a context.
 * <p>{@link #supports(Project)} additionally short-circuits to {@literal true}
 * when the project-scoped {@link DependencyAssistantService} already holds
 * cached dependency or release data, avoiding redundant applicability checks on
 * every UI update.
 *
 * @author Mark Paluch
 * @see DependencyAssistant
 * @see ProjectDependencyContext
 */
class DependencyAssistantDispatcher {

	private static final ExtensionPointName<DependencyAssistant> INTEGRATIONS = ExtensionPointName
			.create("biz.paluch.dap.assistant");

	private DependencyAssistantDispatcher() {
	}

	/**
	 * Return whether any registered integration applies to the given project.
	 * <p>Returns {@literal true} immediately if the project-scoped
	 * {@link DependencyAssistantService} already holds dependency or release data,
	 * bypassing the per-integration applicability check. Otherwise iterates all
	 * registered {@link DependencyAssistant} instances and returns {@literal true}
	 * as soon as one claims the project.
	 * <p>Intended for lightweight action-visibility checks; must not trigger I/O or
	 * PSI access.
	 *
	 * @param project the project to inspect; must not be {@literal null}.
	 * @return {@literal true} if at least one integration applies or cached state
	 * is present; {@literal false} otherwise.
	 */
	static boolean supports(Project project) {

		if (DependencyAssistantService.getInstance(project).hasDependenciesOrReleases()) {
			return true;
		}

		for (DependencyAssistant integration : INTEGRATIONS.getExtensionList()) {
			if (integration.supports(project)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return all integrations that apply to the given project.
	 * <p>Iterates all registered {@link DependencyAssistant} instances and collects
	 * each one that returns {@literal true} from
	 * {@link DependencyAssistant#supports(Project)}. Use this for project-wide
	 * fan-out operations such as post-startup background indexing and
	 * release-metadata refresh where every applicable integration must participate.
	 *
	 * @param project the project to collect integrations for; must not be
	 * {@literal null}.
	 * @return the applicable integrations in registration order; guaranteed to be
	 * not {@literal null} but may be empty.
	 */
	static List<DependencyAssistant> findAll(Project project) {

		List<DependencyAssistant> integrations = new ArrayList<>();
		for (DependencyAssistant integration : INTEGRATIONS.getExtensionList()) {
			if (integration.supports(project)) {
				integrations.add(integration);
			}
		}

		return integrations;
	}

	/**
	 * Invoke the given consumer with a {@link ProjectDependencyContext} for each
	 * integration that claims the file.
	 * <p>Iterates all registered {@link DependencyAssistant} instances, skips those
	 * that do not claim the file, and passes a freshly created
	 * {@link DependencyAssistant#createContext(Project, PsiFile) file-scoped
	 * context} to the consumer for each match. Suitable for fan-out operations such
	 * as cache invalidation on save where multiple integrations may each own
	 * overlapping files.
	 *
	 * @param file the PSI file to which the context applies; must not be
	 * {@literal null}.
	 * @param consumer the callback invoked with a context per matching integration;
	 * must not be {@literal null}.
	 */
	static void doWithContext(PsiFile file, Consumer<ProjectDependencyContext> consumer) {

		for (DependencyAssistant assistant : INTEGRATIONS.getExtensionList()) {
			if (assistant.supports(file)) {
				consumer.accept(assistant.createContext(file.getProject(), file));
			}
		}
	}

	/**
	 * Return the {@link ProjectDependencyContext} from the first integration that
	 * owns the given PSI element, or {@literal null} if none applies.
	 * <p>Intended for per-file editor operations such as annotators, line-marker
	 * providers, and completion contributors where exactly one integration is
	 * expected to own a given file. The search stops at the first match, so
	 * registration order determines priority when integrations could theoretically
	 * overlap.
	 *
	 * @param element the PSI element to resolve; can be {@literal null}.
	 * @return the context from the first matching integration, or {@literal null}
	 * if {@code file} is {@literal null} or no integration claims the file.
	 */
	static @Nullable ProjectDependencyContext findFirstContext(PsiElement element) {
		return findFirstContext(element instanceof PsiFile file ? file : element.getContainingFile());
	}

	/**
	 * Return the {@link ProjectDependencyContext} from the first integration that
	 * owns the given PSI file, or {@literal null} if none applies.
	 * <p>Intended for per-file editor operations such as annotators, line-marker
	 * providers, and completion contributors where exactly one integration is
	 * expected to own a given file. The search stops at the first match, so
	 * registration order determines priority when integrations could theoretically
	 * overlap.
	 *
	 * @param file the PSI file to resolve; can be {@literal null}.
	 * @return the context from the first matching integration, or {@literal null}
	 * if {@code file} is {@literal null} or no integration claims the file.
	 */
	static @Nullable ProjectDependencyContext findFirstContext(PsiFile file) {

		for (DependencyAssistant integration : INTEGRATIONS.getExtensionList()) {

			if (integration.supports(file)) {
				return integration.createContext(file.getProject(), file);
			}
		}

		return null;
	}

	/**
	 * Return the {@link ProjectDependencyContext} from the first integration that
	 * owns the given PSI file, or {@literal null} if none applies.
	 * <p>Equivalent to {@link #findFirstContext(PsiFile)} but accepts an explicit
	 * {@link Project} parameter. Use this overload when the project reference is
	 * already at hand to avoid the implicit {@link PsiFile#getProject()} call.
	 *
	 * @param project the project that contains the file; must not be
	 * {@literal null}.
	 * @param file the PSI file to resolve; can be {@literal null}.
	 * @return the context from the first matching integration, or {@literal null}
	 * if {@code file} is {@literal null} or no integration claims the file.
	 */
	static @Nullable ProjectDependencyContext findFirstContext(Project project, @Nullable PsiFile file) {

		if (file == null) {
			return null;
		}

		for (DependencyAssistant integration : INTEGRATIONS.getExtensionList()) {
			if (integration.supports(file)) {
				return integration.createContext(project, file);
			}
		}

		return null;
	}

}
