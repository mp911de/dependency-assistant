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

import biz.paluch.dap.ProjectDependencyContext;
import biz.paluch.dap.support.NewerVersionAnnotator;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;

/**
 * Annotator that highlights the variant-defined replaceable range of an NPM
 * dependency value.
 *
 * <p>The class is named {@code NpmNewerVersionAnnotator} to avoid collision
 * with {@link NewerVersionAnnotator}, which sits in the {@code support}
 * package.
 *
 * @author Mark Paluch
 */
public class NpmNewerVersionAnnotator extends NewerVersionAnnotator {

	@Override
	protected TextRange getTextRange(PsiElement element, ProjectDependencyContext context) {
		return NpmPsiUtils.getVersionRange(element);
	}

}
