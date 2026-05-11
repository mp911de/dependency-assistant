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

package biz.paluch.dap.assistant;

import biz.paluch.dap.DependencyAssistantDispatcher;
import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.support.AvailableUpgrades;
import biz.paluch.dap.support.VersionUpgradeLookupSupport;
import com.intellij.psi.PsiElement;

/**
 * @author Mark Paluch
 */
class UpgradeSuggestions {

	/**
	 * Suggest upgrade suggestions for the given PSI element.
	 * @param element the PSI element under inspection.
	 * @return the available upgrade suggestions, or
	 * {@link AvailableUpgrades#none()} if none are available or the element is not
	 * a version element in a supported context.
	 */
	public static AvailableUpgrades suggest(PsiElement element) {
		return suggest(DependencyAssistantDispatcher.findFirstContext(element.getProject(),
				element.getContainingFile()), element);
	}

	/**
	 * Suggest upgrade suggestions for the given PSI element.
	 * @param context the project dependency context to use for lookup.
	 * @param element the PSI element under inspection.
	 * @return the available upgrade suggestions, or
	 * {@link AvailableUpgrades#none()} if none are available or the element is not
	 * a version element in a supported context.
	 */
	public static AvailableUpgrades suggest(ProjectDependencyContext context, PsiElement element) {

		if (context.isAbsent() || !context.isVersionElement(element) || context.isAbsent()) {
			return AvailableUpgrades.none();
		}

		VersionUpgradeLookupSupport service = context.getLookup(element, element.getContainingFile().getVirtualFile());
		return service.suggestUpgrades(element);
	}

}
