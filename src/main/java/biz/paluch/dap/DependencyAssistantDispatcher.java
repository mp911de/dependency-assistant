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

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.state.StateService;
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
	 * <p>Return {@literal true} immediately if the project-scoped
	 * {@link StateService} already holds dependency or release data, avoiding
	 * repeated project applicability checks during UI updates.
	 * @param project the project to inspect; must not be {@literal null}.
	 */
	public static boolean supports(Project project) {
		if (StateService.getInstance(project).hasDependenciesOrReleases()) {
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
	 * Return whether any registered integration supports the given {@code file}.
	 * <p>Return {@literal true} immediately if the project-scoped
	 * {@link StateService} already holds dependency or release data, avoiding
	 * repeated project applicability checks during UI updates.
	 * @param file the PSI file to test; must not be {@literal null}.
	 * @return {@literal true} if some integration supports the file;
	 * {@literal false} otherwise.
	 */
	public static boolean supports(PsiFile file) {
		for (DependencyAssistant integration : INTEGRATIONS.getExtensionList()) {
			if (integration.supports(file)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Return whether the first integration owning the given {@code file} exposes an
	 * {@link ProjectDependencyContext#isAvailable() available} context, i.e. its
	 * project model is imported and ready to scan or write.
	 * <p>Stricter than {@link #supports(PsiFile)}, which only checks whether some
	 * integration recognizes the file type. Use this method when an actual context
	 * is required (resolving declarations, writing upgrades), and {@code supports}
	 * when only file-type recognition matters (for example completion confidence).
	 * @param file the PSI file to test; must not be {@literal null}.
	 * @return {@literal true} if an available context exists for the file;
	 * {@literal false} otherwise.
	 * @see #supports(PsiFile)
	 */
	public static boolean contextSupports(PsiFile file) {
		return findFirstContext(file).isAvailable();
	}

	/**
	 * Return all integrations that apply to the given project.
	 * @param project the project to collect integrations for.
	 * @return the applicable integrations in registration order.
	 */
	public static List<DependencyAssistant> findAll(Project project) {

		List<DependencyAssistant> integrations = new ArrayList<>();
		for (DependencyAssistant integration : INTEGRATIONS.getExtensionList()) {
			if (integration.supports(project)) {
				integrations.add(integration);
			}
		}

		return integrations;
	}

	/**
	 * Return the {@link ProjectDependencyContext} from the first integration that
	 * owns the given PSI element.
	 * @param element the PSI element to resolve.
	 * @return the context from the first matching integration, or {@literal null}.
	 */
	public static ProjectDependencyContext findFirstContext(PsiElement element) {
		return findFirstContext(element instanceof PsiFile file ? file : element.getContainingFile());
	}

	/**
	 * Return the {@link ProjectDependencyContext} from the first integration that
	 * owns the given PSI file.
	 * @param file the PSI file to resolve.
	 * @return the context from the first matching integration, or {@literal null}.
	 */
	public static ProjectDependencyContext findFirstContext(PsiFile file) {
		return findFirstContext(file.getProject(), file);
	}

	/**
	 * Return the {@link ProjectDependencyContext} from the first integration that
	 * owns the given PSI file.
	 * @param project the project that contains the file.
	 * @param file the PSI file to resolve.
	 * @return the context from the first matching integration or an
	 * {@link ProjectDependencyContext#isAbsent() absent} context.
	 */
	public static ProjectDependencyContext findFirstContext(Project project, @Nullable PsiFile file) {

		if (file == null) {
			return ProjectDependencyContext.absent();
		}

		// TODO: review caching. Depends on integration, dumb mode, …
		for (DependencyAssistant integration : INTEGRATIONS.getExtensionList()) {
			if (!integration.supports(file)) {
				continue;
			}

			ProjectDependencyContext context = integration.createContext(project, file);
			if (context.isAvailable()) {
				return context;
			}
		}

		return ProjectDependencyContext.absent();
	}

}
