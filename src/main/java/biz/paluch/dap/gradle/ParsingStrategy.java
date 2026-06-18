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

package biz.paluch.dap.gradle;

import java.util.function.Predicate;

import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.support.ArtifactDeclaration;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * Strategy for parsing one supported Gradle DSL declaration shape into a
 * {@link ArtifactDeclaration}.
 *
 * <p>Implementations are typically inner classes of a DSL parser so they can
 * close over the parser's resolution collaborators. A strategy first tests
 * whether it {@link #supports(ConfigurationContext) supports} a classified call
 * and, if so, parses the underlying call element.
 *
 * @author Mark Paluch
 * @param <C> the classified configuration call this strategy inspects.
 * @param <E> the call PSI element this strategy parses.
 * @see KotlinDslParser
 * @see GroovyDslParser
 */
interface ParsingStrategy<C extends ConfigurationContext, E extends PsiElement> extends Predicate<C> {

	@Override
	default boolean test(C call) {
		return supports(call);
	}

	/**
	 * Return whether this strategy can parse the given classified call.
	 * @param call the classified configuration call.
	 * @return {@literal true} if this strategy handles the call; {@literal false}
	 * otherwise.
	 */
	boolean supports(C call);

	/**
	 * Parse the given call element into a declaration.
	 * @param call the call PSI element.
	 * @param declarationSource the structural origin (direct dependency, managed
	 * platform, plugin, or plugin management) of the declaration.
	 * @return the parsed declaration, or {@literal null} when the call yields no
	 * supported declaration.
	 */
	@Nullable
	ArtifactDeclaration parse(E call, DeclarationSource declarationSource);

}
