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

package biz.paluch.dap.gradle;

import java.util.function.Predicate;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Parse one Gradle declaration shape.
 *
 * @author Mark Paluch
 * @param <C> the classified configuration call.
 * @param <E> the underlying PSI call.
 */
interface ParsingStrategy<C extends ConfigurationContext, E extends PsiElement> extends Predicate<C> {

	@Override
	default boolean test(C call) {
		return supports(call);
	}

	boolean supports(C call);

	/**
	 * Parse the call, or return {@literal null} if no supported declaration
	 * results.
	 */
	@Nullable
	ArtifactDeclaration parse(E call, DeclarationSource declarationSource);

}
