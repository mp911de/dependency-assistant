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
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;

/**
 * Completion contributor that suggests cached release versions.
 *
 * @author Mark Paluch
 */
public class ReleaseVersionCompletionContributor extends CompletionContributor {

	private final ReleasesCompletionProvider provider = new ReleasesCompletionProvider(
			ReleasesCompletionProvider.resolver());

	@Override
	public void fillCompletionVariants(CompletionParameters parameters, CompletionResultSet result) {

		PsiFile file = parameters.getOriginalFile();
		if (DependencyAssistantDispatcher.findFirstContext(file.getProject(), parameters.getOriginalFile()) == null) {
			return;
		}

		provider.addCompletionVariants(parameters, new ProcessingContext(), result);
	}

}
