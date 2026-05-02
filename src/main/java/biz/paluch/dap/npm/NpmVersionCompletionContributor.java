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

package biz.paluch.dap.npm;

import biz.paluch.dap.support.ReleasesCompletionProvider;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.patterns.PatternCondition;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

/**
 * Completion contributor for NPM dependency version strings.
 *
 * <p>The contributor reuses {@link ReleasesCompletionProvider} so accepting a
 * completion goes through the same {@code applyUpdates(...)} path used by the
 * dialog. The matcher only fires inside the value of an NPM
 * {@code dependencies}/{@code devDependencies} string literal, which keeps the
 * suggestions out of property names and unrelated JSON files.
 *
 * @author Mark Paluch
 */
public class NpmVersionCompletionContributor extends CompletionContributor {

	private final ReleasesCompletionProvider provider = new ReleasesCompletionProvider(
			ReleasesCompletionProvider.resolver());

	public NpmVersionCompletionContributor() {
		extend(CompletionType.BASIC,
				PlatformPatterns.psiElement().with(new PatternCondition<>("inNpmDependencyValue") {

					@Override
					public boolean accepts(PsiElement element, ProcessingContext context) {
						return isInDependencyValue(element);
					}

				}),
				provider);
	}

	private static boolean isInDependencyValue(PsiElement element) {

		JsonStringLiteral literal = NpmPsiUtils.findDependencyLiteral(element);
		return literal != null;
	}

}
