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

import com.intellij.psi.PsiFile;

/**
 * Strategy for applying dependency updates to a PSI file.
 *
 * <p>Implementations modify the supplied file in place according to their
 * language and grammar. Calling code is responsible for write-action and
 * command management.
 *
 * @author Mark Paluch
 */
@FunctionalInterface
public interface FileDependencyUpdater {

	/**
	 * Apply the given dependency updates to the supplied file.
	 * @param file the PSI file to modify in place.
	 * @param updates the dependency updates to apply.
	 */
	void applyUpdates(PsiFile file, DependencyUpdates updates);

}
