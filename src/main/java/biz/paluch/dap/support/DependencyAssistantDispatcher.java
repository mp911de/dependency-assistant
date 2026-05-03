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
 * Dispatcher for registered {@link DependencyAssistant} integrations.
 *
 * <p>Integrations are discovered through the {@code biz.paluch.dap.assistant}
 * extension point and consulted in registration order. Fan-out methods visit
 * every matching integration; first-match methods stop once a file has an
 * owner.
 *
 * @author Mark Paluch
 * @see DependencyAssistant
 * @see ProjectDependencyContext
 */
public class DependencyAssistantDispatcher {

	private static final ExtensionPointName<DependencyAssistant> INTEGRATIONS = ExtensionPointName
			.create("biz.paluch.dap.assistant");

	private DependencyAssistantDispatcher() {
	}

	/**
	 * Return whether any registered integration applies to the given project.
	 * <p>Returns {@code true} immediately if the project-scoped
	 * {@link DependencyAssistantService} already holds dependency or release data,
	 * avoiding repeated project applicability checks during UI updates.
	 * @param project the project to inspect.
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
	 * @param project the project to collect integrations for.
	 * @return the applicable integrations in registration order.
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
	 * @param file the PSI file to which the context applies.
	 * @param consumer the callback invoked with a context per matching integration.
	 */
	static void doWithContext(PsiFile file, Consumer<ProjectDependencyContext> consumer) {

		for (DependencyAssistant assistant : INTEGRATIONS.getExtensionList()) {
			if (assistant.supports(file)) {
				ProjectDependencyContext context = assistant.createContext(file.getProject(), file);
				if (context.isAvailable()) {
					consumer.accept(context);
				}
			}
		}
	}

	/**
	 * Return the {@link ProjectDependencyContext} from the first integration that
	 * owns the given PSI element.
	 * @param element the PSI element to resolve.
	 * @return the context from the first matching integration, or {@code null}.
	 */
	static @Nullable ProjectDependencyContext findFirstContext(PsiElement element) {
		return findFirstContext(element instanceof PsiFile file ? file : element.getContainingFile());
	}

	/**
	 * Return the {@link ProjectDependencyContext} from the first integration that
	 * owns the given PSI file.
	 * @param file the PSI file to resolve.
	 * @return the context from the first matching integration, or {@code null}.
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
	 * owns the given PSI file.
	 * @param project the project that contains the file.
	 * @param file the PSI file to resolve.
	 * @return the context from the first matching integration, or {@code null}.
	 */
	public static @Nullable ProjectDependencyContext findFirstContext(Project project, @Nullable PsiFile file) {

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

	/**
	 * Return the {@link VersionUpgradeLookupSupport} from the first integration
	 * that owns the given PSI element.
	 * @param element the PSI element to resolve.
	 * @return the VersionUpgradeLookupSupport from the first matching integration,
	 * or {@code null} if not available.
	 */
	static @Nullable VersionUpgradeLookupSupport getVersionLookup(PsiElement element) {
		ProjectDependencyContext context = findFirstContext(
				element instanceof PsiFile file ? file : element.getContainingFile());
		return context != null ? context.getLookup(element) : null;
	}

}
